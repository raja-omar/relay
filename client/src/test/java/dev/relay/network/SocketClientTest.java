package dev.relay.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

import dev.relay.common.AccessTokens;
import dev.relay.common.SchematicLimits;
import dev.relay.common.protocol.Message;
import dev.relay.common.protocol.MessageType;
import dev.relay.common.protocol.PacketWriter;
import dev.relay.common.protocol.SharePackets;
import dev.relay.group.GroupState;
import dev.relay.server.RelayServer;
import dev.relay.server.ServerConfig;
import dev.relay.server.player.PlayerDirectory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * The mod's end of the socket, talking to the real server.
 *
 * <p>This is as close to two Minecraft clients sharing a group as a test can get: everything but the
 * chat window and the mouse click is the code that ships.
 */
@Timeout(30)
class SocketClientTest {
	@TempDir
	Path work;

	private PlayerDirectory players;
	private final Map<String, String> tokens = new HashMap<>();
	private RelayServer server;
	private final List<SocketClient> clients = new ArrayList<>();
	private Path tlsFile;

	@BeforeEach
	void startServer() throws IOException {
		tlsFile = work.resolve("tls.p12");
		players = PlayerDirectory.inMemory();
		server = new RelayServer(new ServerConfig(0, ServerConfig.DEFAULT_PLAYERS_FILE, tlsFile), players);
		server.start();
	}

	@AfterEach
	void stopEverything() {
		clients.forEach(SocketClient::shutdown);

		if (server != null) {
			server.close();
		}
	}

	@Test
	void signsInAndIsToldItHasNoGroup() throws Exception {
		Recorder alice = connect("Alice");

		assertEquals("connected", alice.awaitEvent());
		assertTrue(alice.await(MessageType.AUTH_RESULT).reader().readBoolean());
		assertTrue(GroupState.read(alice.await(MessageType.GROUP_UPDATE)).isEmpty());
		assertEquals(SocketClient.Status.CONNECTED, alice.socket.status());
	}

	@Test
	void createsAGroupAndIsSentItsMembers() throws Exception {
		Recorder alice = connect("Alice");
		alice.awaitSignIn();

		alice.socket.send(new PacketWriter().writeString("Alpha").toMessage(MessageType.GROUP_CREATE));

		assertTrue(alice.await(MessageType.NOTICE).reader().readString().startsWith("Created Alpha"));

		GroupState group = GroupState.read(alice.await(MessageType.GROUP_UPDATE)).orElseThrow();

		assertEquals("Alpha", group.name());
		assertEquals("Alice", group.owner().name());
		assertEquals(1, group.members().size());
		assertTrue(group.members().get(0).online());
	}

	@Test
	void carriesAnInvitationFromOneClientToAnother() throws Exception {
		Recorder alice = connect("Alice");
		Recorder beta = connect("Beta");
		alice.awaitSignIn();
		beta.awaitSignIn();

		alice.socket.send(new PacketWriter().writeString("Alpha").toMessage(MessageType.GROUP_CREATE));
		alice.await(MessageType.NOTICE);
		alice.await(MessageType.GROUP_UPDATE);

		alice.socket.send(new PacketWriter().writeString("Beta").toMessage(MessageType.GROUP_INVITE));
		assertEquals("Invited Beta to Alpha.", alice.await(MessageType.NOTICE).reader().readString());

		Message invitation = beta.await(MessageType.INVITE);
		var reader = invitation.reader();
		assertEquals("Alpha", reader.readString());
		assertEquals("Alice", reader.readString());

		// What clicking [Accept] ends up sending.
		beta.socket.send(new PacketWriter().writeString("Alpha").toMessage(MessageType.GROUP_ACCEPT));

		assertEquals("You joined Alpha.", beta.await(MessageType.NOTICE).reader().readString());

		GroupState betaSees = GroupState.read(beta.await(MessageType.GROUP_UPDATE)).orElseThrow();
		assertEquals(List.of("Alice", "Beta"), betaSees.members().stream().map(GroupState.Member::name).toList());
		assertEquals("Alice", betaSees.owner().name());
		assertEquals(2, betaSees.onlineCount());

		assertEquals("Beta joined Alpha.", alice.await(MessageType.NOTICE).reader().readString());
		assertEquals(2, GroupState.read(alice.await(MessageType.GROUP_UPDATE)).orElseThrow().members().size());
	}

	@Test
	void sharesAPlacementFromOneClientToTheOther() throws Exception {
		Recorder alice = connect("Alice");
		Recorder beta = connect("Beta");
		alice.awaitSignIn();
		beta.awaitSignIn();

		alice.socket.send(new PacketWriter().writeString("Alpha").toMessage(MessageType.GROUP_CREATE));
		alice.await(MessageType.NOTICE);
		alice.await(MessageType.GROUP_UPDATE);

		alice.socket.send(new PacketWriter().writeString("Beta").toMessage(MessageType.GROUP_INVITE));
		alice.await(MessageType.NOTICE);
		beta.await(MessageType.INVITE);
		beta.socket.send(new PacketWriter().writeString("Alpha").toMessage(MessageType.GROUP_ACCEPT));
		beta.await(MessageType.NOTICE);
		beta.await(MessageType.GROUP_UPDATE);
		alice.await(MessageType.NOTICE);
		alice.await(MessageType.GROUP_UPDATE);

		byte[] data = { 0x1F, (byte) 0x8B, 0x08, 0x11, 0x22, 0x33 };
		UUID transferId = UUID.randomUUID();
		assertTrue(alice.socket.send(SharePackets.share(transferId, "Wall", data)));

		assertEquals("Shared Wall with Alpha.", alice.await(MessageType.NOTICE).reader().readString());

		Message shared = beta.await(MessageType.SCHEM_SHARED);
		var reader = shared.reader();
		assertEquals(transferId, reader.readUuid());
		assertEquals("Alice", reader.readString());
		assertEquals("Wall", reader.readString());
		assertArrayEquals(data, reader.readBytes(SchematicLimits.MAX_BYTES));
	}

	@Test
	void doesNotRetryWhenTheTlsPinIsWrong() throws Exception {
		Recorder alice = new Recorder();
		clients.add(alice.socket);
		alice.socket.connect(new Target("127.0.0.1", server.port(), tokenFor("Alice"),
				"ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));

		assertTrue(alice.awaitEvent(10).startsWith("disconnected"));
		assertFalse(alice.lastWillRetry, "a pin mismatch is a config error, not a blip");
	}

	@Test
	void doesNotRetryWhenNoTlsPinIsPacked() throws Exception {
		Recorder alice = new Recorder();
		clients.add(alice.socket);
		alice.socket.connect(new Target("127.0.0.1", server.port(), tokenFor("Alice"), ""));

		assertTrue(alice.awaitEvent().startsWith("disconnected"));
		assertFalse(alice.lastWillRetry, "a missing pin should not hammer the server");
	}

	@Test
	void reportsAServerThatTurnsUsAway() throws Exception {
		server.close();
		players = PlayerDirectory.inMemory();
		players.issue("Alice");
		server = new RelayServer(new ServerConfig(0, ServerConfig.DEFAULT_PLAYERS_FILE, tlsFile), players);
		server.start();

		Recorder alice = new Recorder();
		SocketClient socket = alice.socket;
		clients.add(socket);
		socket.connect(new Target("127.0.0.1", server.port(), AccessTokens.issue(), server.tlsPin()));

		alice.awaitEvent();
		Message authResult = alice.await(MessageType.AUTH_RESULT);
		var reader = authResult.reader();

		assertFalse(reader.readBoolean());
		assertEquals("Unknown player id", reader.readString());
	}

	@Test
	void keepsTryingWhenTheServerIsNotThere() throws Exception {
		Recorder alice = new Recorder();
		clients.add(alice.socket);

		alice.socket.connect(new Target("127.0.0.1", 1, AccessTokens.issue(),
				"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"));

		assertTrue(alice.awaitEvent(10).startsWith("disconnected"), "should report the failure");
		assertTrue(alice.lastWillRetry, "an unreachable server should be retried");
		assertEquals(SocketClient.Status.DISCONNECTED, alice.socket.status());
		assertFalse(alice.socket.send(Message.of(MessageType.PING)), "nothing can be sent while down");
	}

	@Test
	void stopsTryingOnceAskedToDisconnect() throws Exception {
		Recorder alice = connect("Alice");
		alice.awaitSignIn();

		alice.socket.disconnect("test");

		assertTrue(alice.awaitEvent().startsWith("disconnected"));
		assertFalse(alice.lastWillRetry, "an intentional disconnect must not reconnect");
	}

	@Test
	void comesBackByItselfAfterTheServerRestarts() throws Exception {
		int port = server.port();
		Recorder alice = connect("Alice", port);
		alice.awaitSignIn();

		server.close();
		assertTrue(alice.awaitEvent().startsWith("disconnected"));
		assertTrue(alice.lastWillRetry);

		server = new RelayServer(new ServerConfig(port, ServerConfig.DEFAULT_PLAYERS_FILE, tlsFile), players);
		server.start();

		// The first retry waits a couple of seconds, so this is the one place the test has to be patient.
		assertEquals("connected", alice.awaitEvent(20));
		assertTrue(alice.await(MessageType.AUTH_RESULT, 20).reader().readBoolean(), "should sign in again");
	}

	private Recorder connect(String playerName) {
		return connect(playerName, server.port());
	}

	private Recorder connect(String playerName, int port) {
		Recorder recorder = new Recorder();
		clients.add(recorder.socket);
		recorder.socket.connect(new Target("127.0.0.1", port, tokenFor(playerName), server.tlsPin()));
		return recorder;
	}

	private String tokenFor(String playerName) {
		return tokens.computeIfAbsent(playerName, name -> {
			try {
				return players.issue(name).token();
			} catch (IOException failed) {
				throw new IllegalStateException(failed);
			}
		});
	}

	/** Collects what the listener is told, since it is called from network threads. */
	private static final class Recorder implements SocketClient.Listener {
		private final BlockingQueue<Message> messages = new LinkedBlockingQueue<>();
		private final BlockingQueue<String> events = new LinkedBlockingQueue<>();
		private final SocketClient socket = new SocketClient(this);

		private volatile boolean lastWillRetry;

		@Override
		public void onConnected() {
			events.add("connected");
		}

		@Override
		public void onMessage(Message message) {
			messages.add(message);
		}

		@Override
		public void onDisconnected(String reason, boolean willRetry) {
			lastWillRetry = willRetry;
			events.add("disconnected: " + reason);
		}

		void awaitSignIn() throws Exception {
			awaitEvent();
			assertTrue(await(MessageType.AUTH_RESULT).reader().readBoolean(), "sign in should succeed");
			await(MessageType.GROUP_UPDATE);
		}

		Message await(MessageType expected) throws Exception {
			return await(expected, 5);
		}

		Message await(MessageType expected, int seconds) throws Exception {
			Message message = messages.poll(seconds, TimeUnit.SECONDS);

			assertNotNull(message, () -> "expected " + expected + " but nothing arrived");
			assertEquals(expected, message.type());
			return message;
		}

		String awaitEvent() throws Exception {
			return awaitEvent(5);
		}

		String awaitEvent(int seconds) throws Exception {
			String event = events.poll(seconds, TimeUnit.SECONDS);
			assertNotNull(event, "expected a connection event but nothing happened");
			return event;
		}
	}

}
