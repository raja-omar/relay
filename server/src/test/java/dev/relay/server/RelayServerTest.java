package dev.relay.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import dev.relay.common.protocol.Message;
import dev.relay.common.protocol.MessageType;
import dev.relay.common.protocol.PacketReader;
import dev.relay.common.protocol.PacketWriter;
import dev.relay.common.protocol.Protocol;
import dev.relay.common.protocol.ProtocolException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Drives a real server over real sockets with the plain {@link TestClient}.
 *
 * <p>Every server listens on port 0, so the tests can run in parallel with anything else on the
 * machine.
 */
class RelayServerTest {
	private RelayServer server;

	@AfterEach
	void stopServer() {
		if (server != null) {
			server.close();
		}
	}

	private RelayServer start(String secret) throws IOException {
		server = new RelayServer(new ServerConfig(0, secret));
		server.start();
		return server;
	}

	@Test
	void signsInAClientThatAsksProperly() throws IOException {
		start("");

		try (TestClient client = new TestClient(server.port())) {
			client.sendAuth(UUID.randomUUID(), "Alice", "");

			Message reply = client.receive();
			PacketReader reader = reply.reader();

			assertEquals(MessageType.AUTH_RESULT, reply.type());
			assertTrue(reader.readBoolean());
			assertEquals("Signed in as Alice", reader.readString());
			awaitUntil(() -> server.sessions().size() == 1, "session to be registered");
		}
	}

	@Test
	void acceptsTheRightSecretAndRefusesTheWrongOne() throws IOException {
		start("hunter2");

		try (TestClient good = new TestClient(server.port())) {
			good.sendAuth(UUID.randomUUID(), "Alice", "hunter2");
			assertTrue(accepted(good.receive()));
		}

		try (TestClient bad = new TestClient(server.port())) {
			bad.sendAuth(UUID.randomUUID(), "Sneak", "hunter3");

			Message reply = bad.receive();
			PacketReader reader = reply.reader();

			assertEquals(MessageType.AUTH_RESULT, reply.type());
			assertFalse(reader.readBoolean());
			assertEquals("Wrong server secret", reader.readString());
			assertTrue(bad.awaitClose(), "server should hang up after refusing");
			assertEquals(0, server.sessions().size());
		}
	}

	@Test
	void refusesAMismatchedProtocolVersion() throws IOException {
		start("");

		try (TestClient client = new TestClient(server.port())) {
			client.sendAuth(Protocol.VERSION + 1, UUID.randomUUID(), "Alice", "");

			assertFalse(accepted(client.receive()));
			assertTrue(client.awaitClose());
		}
	}

	@Test
	void refusesAPlayerNameItWouldNotWantToPrint() throws IOException {
		start("");

		try (TestClient client = new TestClient(server.port())) {
			client.sendAuth(UUID.randomUUID(), "§cAdmin\nAlice", "");

			assertFalse(accepted(client.receive()));
			assertTrue(client.awaitClose());
		}
	}

	@Test
	void insistsOnAuthBeforeAnythingElse() throws IOException {
		start("");

		try (TestClient client = new TestClient(server.port())) {
			client.send(Message.of(MessageType.GROUP_INFO));

			Message reply = client.receive();

			assertEquals(MessageType.ERROR, reply.type());
			assertEquals("Send AUTH before anything else", reply.reader().readString());
			assertTrue(client.awaitClose());
		}
	}

	@Test
	void answersAPingWithAPong() throws IOException {
		start("");

		try (TestClient client = new TestClient(server.port())) {
			client.send(Message.of(MessageType.PING));

			assertEquals(MessageType.PONG, client.receive().type());

			client.sendAuth(UUID.randomUUID(), "Alice", "");
			assertTrue(accepted(client.receive()));

			client.send(Message.of(MessageType.PING));
			assertEquals(MessageType.PONG, client.receive().type());
		}
	}

	@Test
	void reportsMessagesItCannotHandleYetWithoutHangingUp() throws IOException {
		start("");

		try (TestClient client = new TestClient(server.port())) {
			client.sendAuth(UUID.randomUUID(), "Alice", "");
			assertTrue(accepted(client.receive()));

			client.send(new PacketWriter().writeString("Alpha").toMessage(MessageType.GROUP_CREATE));

			Message reply = client.receive();

			assertEquals(MessageType.ERROR, reply.type());
			assertTrue(reply.reader().readString().contains("GROUP_CREATE"));

			// Still usable afterwards.
			client.send(Message.of(MessageType.PING));
			assertEquals(MessageType.PONG, client.receive().type());
		}
	}

	@Test
	void rejectsAnAuthMessageMissingItsFields() throws IOException {
		start("");

		try (TestClient client = new TestClient(server.port())) {
			client.send(new PacketWriter().writeInt(Protocol.VERSION).toMessage(MessageType.AUTH));

			assertEquals(MessageType.ERROR, client.receive().type());
			assertTrue(client.awaitClose());
		}
	}

	@Test
	void survivesAFrameClaimingToBeEnormous() throws IOException {
		start("");

		try (TestClient client = new TestClient(server.port())) {
			client.sendRaw((byte) 0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF);

			Message reply = client.receive();

			assertEquals(MessageType.ERROR, reply.type());
			assertTrue(reply.reader().readString().contains("exceeds the limit"));
			assertTrue(client.awaitClose());
		}

		// The server itself is unharmed and still takes connections.
		try (TestClient after = new TestClient(server.port())) {
			after.sendAuth(UUID.randomUUID(), "Alice", "");
			assertTrue(accepted(after.receive()));
		}
	}

	@Test
	void survivesAnUnknownMessageType() throws IOException {
		start("");

		try (TestClient client = new TestClient(server.port())) {
			client.sendRaw((byte) 0, (byte) 0, (byte) 0, (byte) 1, (byte) 199);

			assertEquals(MessageType.ERROR, client.receive().type());
			assertTrue(client.awaitClose());
		}
	}

	@Test
	void replacesAnOlderConnectionForTheSamePlayer() throws IOException {
		start("");
		UUID playerId = UUID.randomUUID();

		try (TestClient first = new TestClient(server.port())) {
			first.sendAuth(playerId, "Alice", "");
			assertTrue(accepted(first.receive()));

			try (TestClient second = new TestClient(server.port())) {
				second.sendAuth(playerId, "Alice", "");
				assertTrue(accepted(second.receive()));

				Message kicked = first.receive();

				assertEquals(MessageType.ERROR, kicked.type());
				assertEquals("Signed in from somewhere else", kicked.reader().readString());
				assertTrue(first.awaitClose());

				// The newer connection keeps the session.
				awaitUntil(() -> server.sessions().size() == 1, "one session to remain");
				second.send(Message.of(MessageType.PING));
				assertEquals(MessageType.PONG, second.receive().type());
			}
		}
	}

	@Test
	void forgetsAPlayerWhoDisconnects() throws IOException {
		start("");

		try (TestClient client = new TestClient(server.port())) {
			client.sendAuth(UUID.randomUUID(), "Alice", "");
			assertTrue(accepted(client.receive()));
			awaitUntil(() -> server.sessions().size() == 1, "session to be registered");
		}

		awaitUntil(() -> server.sessions().size() == 0, "session to be dropped");
		awaitUntil(() -> server.connectionCount() == 0, "connection to be dropped");
	}

	@Test
	void stopsCleanlyWithClientsStillConnected() throws IOException {
		start("");

		try (TestClient client = new TestClient(server.port())) {
			client.sendAuth(UUID.randomUUID(), "Alice", "");
			assertTrue(accepted(client.receive()));

			server.close();

			assertTrue(client.awaitClose(), "clients should be hung up on during shutdown");
		}
	}

	private static boolean accepted(Message authResult) throws ProtocolException {
		assertEquals(MessageType.AUTH_RESULT, authResult.type());
		return authResult.reader().readBoolean();
	}

	private static void awaitUntil(BooleanSupplier condition, String what) {
		long deadline = System.nanoTime() + 3_000_000_000L;

		while (System.nanoTime() < deadline) {
			if (condition.getAsBoolean()) {
				return;
			}

			try {
				Thread.sleep(10);
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				break;
			}
		}

		throw new AssertionError("Timed out waiting for " + what);
	}
}
