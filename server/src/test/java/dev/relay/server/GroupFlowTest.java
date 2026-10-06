package dev.relay.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.relay.common.protocol.Message;
import dev.relay.common.protocol.MessageType;
import dev.relay.common.protocol.PacketReader;
import dev.relay.common.protocol.PacketWriter;
import dev.relay.common.protocol.PingPackets;
import dev.relay.common.protocol.ProtocolException;
import dev.relay.common.protocol.SharePackets;
import dev.relay.server.player.PlayerDirectory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The group flows driven end to end over sockets by two plain clients, which is the closest thing to
 * two Minecraft clients that can run unattended.
 */
class GroupFlowTest {
	private PlayerDirectory players;
	private final Map<String, PlayerDirectory.Issued> issued = new HashMap<>();
	private RelayServer server;

	@BeforeEach
	void startServer() throws IOException {
		players = PlayerDirectory.inMemory();
		server = new RelayServer(new ServerConfig(0), players);
		server.start();
	}

	@AfterEach
	void stopServer() {
		server.close();
	}

	@Test
	void invitesAcceptsAndShowsEveryoneTheGroup() throws IOException {
		try (Player alice = new Player("Alice"); Player beta = new Player("Beta")) {
			alice.send(MessageType.GROUP_CREATE, "Alpha");
			assertEquals("Created Alpha. Invite people with /relay group invite <player>.", alice.notice());
			assertEquals(List.of("Alice(online)"), alice.groupUpdate().members());

			alice.send(MessageType.GROUP_INVITE, "Beta");
			assertEquals("Invited Beta to Alpha.", alice.notice());

			Message invite = beta.await(MessageType.INVITE);
			PacketReader reader = invite.reader();
			assertEquals("Alpha", reader.readString());
			assertEquals("Alice", reader.readString());

			beta.send(MessageType.GROUP_ACCEPT, "Alpha");
			assertEquals("You joined Alpha.", beta.notice());

			GroupSnapshot betaSees = beta.groupUpdate();
			assertEquals("Alpha", betaSees.name());
			assertEquals(List.of("Alice(online)", "Beta(online)"), betaSees.members());
			assertEquals(alice.playerId, betaSees.ownerId());

			assertEquals("Beta joined Alpha.", alice.notice());
			assertEquals(List.of("Alice(online)", "Beta(online)"), alice.groupUpdate().members());
		}
	}

	@Test
	void tellsTheInviterWhenAnInviteIsDeclined() throws IOException {
		try (Player alice = new Player("Alice"); Player beta = new Player("Beta")) {
			alice.createGroup("Alpha");
			alice.send(MessageType.GROUP_INVITE, "Beta");
			alice.notice();
			beta.await(MessageType.INVITE);

			beta.send(MessageType.GROUP_DECLINE, "Alpha");

			assertEquals("Declined the invitation to Alpha.", beta.notice());
			assertEquals("Beta declined your invitation to Alpha.", alice.notice());
			assertEquals(1, server.groups().groupCount());
		}
	}

	@Test
	void refusesToInviteSomebodyWhoIsNotConnected() throws IOException {
		try (Player alice = new Player("Alice")) {
			alice.createGroup("Alpha");

			alice.send(MessageType.GROUP_INVITE, "Nobody");

			assertEquals("Nobody is not connected to the schematic server.", alice.error());
		}
	}

	@Test
	void reportsGroupActionsThatCannotBeDoneWithoutHangingUp() throws IOException {
		try (Player alice = new Player("Alice")) {
			alice.send(Message.of(MessageType.GROUP_LEAVE));
			assertEquals("You are not in a group.", alice.error());

			alice.send(MessageType.GROUP_ACCEPT, "Ghost");
			assertEquals("You have no invitation to Ghost.", alice.error());

			alice.send(MessageType.GROUP_CREATE, "no");
			assertTrue(alice.error().contains("not a usable group name"));

			// Still connected and usable after all of that.
			alice.createGroup("Alpha");
		}
	}

	@Test
	void answersAGroupInfoRequest() throws IOException {
		try (Player alice = new Player("Alice")) {
			assertFalse(alice.signInState().inGroup());

			alice.send(Message.of(MessageType.GROUP_INFO));
			assertFalse(alice.groupUpdate().inGroup());

			alice.createGroup("Alpha");

			alice.send(Message.of(MessageType.GROUP_INFO));
			assertEquals("Alpha", alice.groupUpdate().name());
		}
	}

	@Test
	void showsAMemberAsOfflineWhenTheyDisconnect() throws IOException {
		try (Player alice = new Player("Alice")) {
			alice.createGroup("Alpha");

			try (Player beta = new Player("Beta")) {
				alice.send(MessageType.GROUP_INVITE, "Beta");
				alice.notice();
				beta.await(MessageType.INVITE);
				beta.send(MessageType.GROUP_ACCEPT, "Alpha");
				beta.notice();
				beta.groupUpdate();
				alice.notice();
				assertEquals(List.of("Alice(online)", "Beta(online)"), alice.groupUpdate().members());
			}

			assertEquals("Beta went offline.", alice.notice());
			assertEquals(List.of("Alice(online)", "Beta(offline)"), alice.groupUpdate().members());
		}
	}

	@Test
	void keepsSomeoneInTheirGroupAcrossAReconnect() throws IOException {
		try (Player first = new Player("Alice")) {
			first.createGroup("Alpha");
		}

		try (Player second = new Player("Alice")) {
			// Signing in tells them where they left off, without asking.
			assertEquals("Alpha", second.signInState().name());
		}
	}

	@Test
	void handsOwnershipOverWhenTheOwnerLeaves() throws IOException {
		try (Player alice = new Player("Alice"); Player beta = new Player("Beta")) {
			alice.createGroup("Alpha");
			alice.send(MessageType.GROUP_INVITE, "Beta");
			alice.notice();
			beta.await(MessageType.INVITE);
			beta.send(MessageType.GROUP_ACCEPT, "Alpha");
			beta.notice();
			beta.groupUpdate();
			alice.notice();
			alice.groupUpdate();

			alice.send(Message.of(MessageType.GROUP_LEAVE));

			assertEquals("You left Alpha.", alice.notice());
			assertFalse(alice.groupUpdate().inGroup());

			assertEquals("Alice left Alpha.", beta.notice());
			assertEquals("Beta now owns Alpha.", beta.notice());

			GroupSnapshot afterwards = beta.groupUpdate();
			assertEquals(beta.playerId, afterwards.ownerId());
			assertEquals(List.of("Beta(online)"), afterwards.members());
		}
	}

	@Test
	void relaysASharedPlacementToTheOtherOnlineMember() throws IOException {
		byte[] data = { 0x1F, (byte) 0x8B, 0x08, 0x00 };

		try (Player alice = new Player("Alice"); Player beta = new Player("Beta")) {
			alice.createGroup("Alpha");
			alice.send(MessageType.GROUP_INVITE, "Beta");
			alice.notice();
			beta.await(MessageType.INVITE);
			beta.send(MessageType.GROUP_ACCEPT, "Alpha");
			beta.notice();
			beta.groupUpdate();
			alice.notice();
			alice.groupUpdate();

			UUID transferId = UUID.randomUUID();
			alice.send(SharePackets.share(transferId, "Wall", data));

			assertEquals("Shared Wall with Alpha.", alice.notice());

			Message shared = beta.await(MessageType.SCHEM_SHARED);
			PacketReader reader = shared.reader();
			assertEquals(transferId, reader.readUuid());
			assertEquals("Alice", reader.readString());
			assertEquals("Wall", reader.readString());
			assertArrayEquals(data, reader.readBytes(1024));
		}
	}

	@Test
	void tellsTheSenderWhenNobodyElseIsOnlineToReceive() throws IOException {
		try (Player alice = new Player("Alice")) {
			alice.createGroup("Alpha");
			alice.send(SharePackets.share(
					UUID.randomUUID(),
					"Wall",
					new byte[] { 0x1F, (byte) 0x8B, 0x08 }));

			assertEquals("Shared Wall, but nobody else in Alpha is online.", alice.notice());
		}
	}

	@Test
	void refusesAShareWithTheSameTransferIdTwice() throws IOException {
		byte[] data = { 0x1F, (byte) 0x8B, 0x08, 0x00 };
		UUID transferId = UUID.randomUUID();

		try (Player alice = new Player("Alice")) {
			alice.createGroup("Alpha");
			alice.send(SharePackets.share(transferId, "Wall", data));
			assertEquals("Shared Wall, but nobody else in Alpha is online.", alice.notice());

			alice.send(SharePackets.share(transferId, "Wall", data));
			assertTrue(alice.error().contains("already sent"));
		}
	}

	@Test
	void refusesAShareWhoseNameHasALineBreak() throws IOException {
		try (Player alice = new Player("Alice")) {
			alice.createGroup("Alpha");
			alice.send(SharePackets.share(
					UUID.randomUUID(),
					"Wall\nTwo",
					new byte[] { 0x1F, (byte) 0x8B, 0x08 }));

			assertTrue(alice.error().contains("Placement names"));
		}
	}

	@Test
	void relaysABlockPingToTheRestOfTheGroup() throws IOException {
		try (Player alice = new Player("Alice"); Player beta = new Player("Beta"); Player cara = new Player("Cara")) {
			alice.createGroup("Alpha");
			alice.send(MessageType.GROUP_INVITE, "Beta");
			alice.notice();
			beta.await(MessageType.INVITE);
			beta.send(MessageType.GROUP_ACCEPT, "Alpha");
			beta.notice();
			beta.groupUpdate();
			alice.notice();
			alice.groupUpdate();

			alice.send(PingPackets.ping(12, 64, -4, 3, "minecraft:overworld"));

			Message pinged = beta.await(MessageType.BLOCK_PINGED);
			PacketReader reader = pinged.reader();
			assertEquals("Alice", reader.readString());
			assertEquals(12, reader.readInt());
			assertEquals(64, reader.readInt());
			assertEquals(-4, reader.readInt());
			assertEquals(3, reader.readInt());
			assertEquals("minecraft:overworld", reader.readString());
			alice.assertNothingElse(MessageType.PING);
			cara.assertNothingElse(MessageType.PING);
		}
	}

	@Test
	void refusesAPingFromSomeoneWhoIsNotInAGroup() throws IOException {
		try (Player alice = new Player("Alice")) {
			alice.send(PingPackets.ping(0, 64, 0, 1, "minecraft:overworld"));
			assertEquals("You are not in a group.", alice.error());
		}
	}

	@Test
	void refusesAPingThatIsNotABlock() throws IOException {
		try (Player alice = new Player("Alice")) {
			alice.createGroup("Alpha");
			alice.send(PingPackets.ping(0, 64, 0, 9, "minecraft:overworld"));
			assertEquals("That ping is not a block in a world.", alice.error());
		}
	}

	@Test
	void forgetsAGroupOnceEverybodyHasLeft() throws IOException {
		try (Player alice = new Player("Alice")) {
			alice.createGroup("Alpha");

			alice.send(Message.of(MessageType.GROUP_LEAVE));
			alice.notice();

			assertFalse(alice.groupUpdate().inGroup());
			assertEquals(0, server.groups().groupCount());
		}
	}

	/** A connected, signed-in player made of nothing but a socket. */
	private final class Player implements AutoCloseable {
		private final TestClient client;
		private final UUID playerId;

		/** The group state the server volunteers on sign in, without being asked. */
		private final GroupSnapshot signInState;

		Player(String name) throws IOException {
			PlayerDirectory.Issued invite = issued.computeIfAbsent(name.toLowerCase(), ignored -> {
				try {
					return players.issue(name);
				} catch (IOException failed) {
					throw new IllegalStateException(failed);
				}
			});

			this.playerId = invite.playerId();
			this.client = new TestClient(server);
			client.sendAuth(invite.token());

			Message authResult = client.receive();
			assertEquals(MessageType.AUTH_RESULT, authResult.type());
			assertTrue(authResult.reader().readBoolean(), "sign in should succeed");
			this.signInState = groupUpdate();
		}

		GroupSnapshot signInState() {
			return signInState;
		}

		void send(Message message) throws IOException {
			client.send(message);
		}

		void send(MessageType type, String value) throws IOException {
			client.send(new PacketWriter().writeString(value).toMessage(type));
		}

		void createGroup(String name) throws IOException {
			send(MessageType.GROUP_CREATE, name);
			notice();
			groupUpdate();
		}

		/** The next message, which must be of the expected type. */
		Message await(MessageType expected) throws IOException {
			Message message = client.receive();
			assertEquals(expected, message.type(), "unexpected message: " + message);
			return message;
		}

		String notice() throws IOException {
			return await(MessageType.NOTICE).reader().readString();
		}

		String error() throws IOException {
			return await(MessageType.ERROR).reader().readString();
		}

		/** The next message must be the answer to a ping, meaning nothing else was queued. */
		void assertNothingElse(MessageType ping) throws IOException {
			send(Message.of(ping));
			assertEquals(MessageType.PONG, client.receive().type());
		}

		GroupSnapshot groupUpdate() throws IOException {
			return GroupSnapshot.read(await(MessageType.GROUP_UPDATE));
		}

		@Override
		public void close() throws IOException {
			client.close();
		}
	}

	/** A GROUP_UPDATE payload, unpacked. */
	private record GroupSnapshot(boolean inGroup, String name, UUID ownerId, List<String> members) {
		static GroupSnapshot read(Message message) throws ProtocolException {
			PacketReader reader = message.reader();

			if (!reader.readBoolean()) {
				return new GroupSnapshot(false, null, null, List.of());
			}

			String name = reader.readString();
			UUID ownerId = reader.readUuid();
			int count = reader.readInt();
			List<String> members = new ArrayList<>();

			for (int i = 0; i < count; i++) {
				reader.readUuid();
				String memberName = reader.readString();
				members.add(memberName + (reader.readBoolean() ? "(online)" : "(offline)"));
			}

			return new GroupSnapshot(true, name, ownerId, members);
		}
	}
}
