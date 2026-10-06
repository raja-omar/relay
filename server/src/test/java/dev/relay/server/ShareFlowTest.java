package dev.relay.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import dev.relay.common.SchematicLimits;
import dev.relay.common.protocol.Message;
import dev.relay.common.protocol.MessageType;
import dev.relay.common.protocol.PacketReader;
import dev.relay.common.protocol.PacketWriter;
import dev.relay.common.protocol.ProtocolException;
import dev.relay.common.protocol.SharePackets;
import dev.relay.server.player.PlayerDirectory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Sharing driven over real sockets, which is the path two Minecraft clients take minus Litematica
 * itself. The server must copy the placement bytes unchanged to every other online group member.
 */
class ShareFlowTest {
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
	void copiesThePlacementBytesToTheOtherOnlineMember() throws IOException {
		byte[] data = placement((byte) 0xAA, (byte) 0xBB, (byte) 0xCC);
		UUID transferId = UUID.randomUUID();

		try (Player alice = new Player("Alice"); Player beta = new Player("Beta")) {
			together(alice, beta);

			alice.send(SharePackets.share(transferId, "Fortress Wall", data));

			assertEquals("Shared Fortress Wall with Alpha.", alice.notice());
			assertShared(beta.await(MessageType.SCHEM_SHARED), transferId, "Alice", "Fortress Wall", data);
			alice.assertNothingElse(MessageType.PING);
		}
	}

	@Test
	void copiesTheSameBytesToEveryOtherOnlineMember() throws IOException {
		byte[] data = placement(new byte[64 * 1024]);
		UUID transferId = UUID.randomUUID();

		try (Player alice = new Player("Alice"); Player beta = new Player("Beta"); Player cara = new Player("Cara")) {
			together(alice, beta, cara);

			alice.send(SharePackets.share(transferId, "Wall", data));

			assertEquals("Shared Wall with Alpha.", alice.notice());
			assertShared(beta.await(MessageType.SCHEM_SHARED), transferId, "Alice", "Wall", data);
			assertShared(cara.await(MessageType.SCHEM_SHARED), transferId, "Alice", "Wall", data);
		}
	}

	@Test
	void skipsAMemberWhoIsOffline() throws IOException {
		byte[] data = placement((byte) 1);
		UUID transferId = UUID.randomUUID();

		try (Player alice = new Player("Alice"); Player beta = new Player("Beta"); Player cara = new Player("Cara")) {
			together(alice, beta, cara);
			cara.close();
			alice.expectWentOffline("Cara");
			beta.expectWentOffline("Cara");

			alice.send(SharePackets.share(transferId, "Wall", data));

			assertEquals("Shared Wall with Alpha.", alice.notice());
			assertShared(beta.await(MessageType.SCHEM_SHARED), transferId, "Alice", "Wall", data);
		}
	}

	@Test
	void doesNotSendToSomeoneInAnotherGroup() throws IOException {
		byte[] data = placement((byte) 2);

		try (Player alice = new Player("Alice"); Player beta = new Player("Beta"); Player cara = new Player("Cara")) {
			together(alice, beta);
			cara.createGroup("Bravo");

			alice.send(SharePackets.share(UUID.randomUUID(), "Wall", data));

			assertEquals("Shared Wall with Alpha.", alice.notice());
			assertEquals(MessageType.SCHEM_SHARED, beta.await(MessageType.SCHEM_SHARED).type());
			cara.assertNothingElse(MessageType.PING);
		}
	}

	@Test
	void doesNotSendToSomeoneWhoLeft() throws IOException {
		byte[] data = placement((byte) 3);

		try (Player alice = new Player("Alice"); Player beta = new Player("Beta")) {
			together(alice, beta);
			beta.send(Message.of(MessageType.GROUP_LEAVE));
			assertEquals("You left Alpha.", beta.notice());
			assertFalseInGroup(beta);
			assertEquals("Beta left Alpha.", alice.notice());
			alice.groupUpdate();

			alice.send(SharePackets.share(UUID.randomUUID(), "Wall", data));

			assertEquals("Shared Wall, but nobody else in Alpha is online.", alice.notice());
			beta.assertNothingElse(MessageType.PING);
		}
	}

	@Test
	void refusesWhenTheSenderIsNotInAGroup() throws IOException {
		try (Player alice = new Player("Alice")) {
			alice.send(SharePackets.share(UUID.randomUUID(), "Wall", placement((byte) 4)));

			assertTrue(alice.error().contains("not in a group"));
			alice.assertNothingElse(MessageType.PING);
		}
	}

	@Test
	void refusesAnEmptyPlacement() throws IOException {
		try (Player alice = new Player("Alice")) {
			alice.createGroup("Alpha");
			alice.send(SharePackets.share(UUID.randomUUID(), "Wall", new byte[0]));

			assertEquals("That schematic is empty.", alice.error());
		}
	}

	@Test
	void refusesBytesThatAreNotCompressedNbt() throws IOException {
		try (Player alice = new Player("Alice")) {
			alice.createGroup("Alpha");
			alice.send(SharePackets.share(UUID.randomUUID(), "Wall", new byte[] { 0x00, 0x01, 0x02 }));

			assertEquals("That file is not a usable schematic.", alice.error());
		}
	}

	@Test
	void refusesAReplayOfTheSameTransfer() throws IOException {
		byte[] data = placement((byte) 5);
		UUID transferId = UUID.randomUUID();

		try (Player alice = new Player("Alice"); Player beta = new Player("Beta")) {
			together(alice, beta);
			alice.send(SharePackets.share(transferId, "Wall", data));
			alice.notice();
			beta.await(MessageType.SCHEM_SHARED);

			alice.send(SharePackets.share(transferId, "Wall", data));

			assertTrue(alice.error().contains("already sent"));
			beta.assertNothingElse(MessageType.PING);
		}
	}

	@Test
	void aFreshTransferOfTheSamePlacementIsDeliveredAgain() throws IOException {
		byte[] data = placement((byte) 6);

		try (Player alice = new Player("Alice"); Player beta = new Player("Beta")) {
			together(alice, beta);

			UUID first = UUID.randomUUID();
			alice.send(SharePackets.share(first, "Wall", data));
			alice.notice();
			assertEquals(first, beta.await(MessageType.SCHEM_SHARED).reader().readUuid());

			UUID second = UUID.randomUUID();
			alice.send(SharePackets.share(second, "Wall", data));
			alice.notice();
			assertEquals(second, beta.await(MessageType.SCHEM_SHARED).reader().readUuid());
		}
	}

	@Test
	void hangsUpOnAShareLargerThanTheLimit() throws IOException {
		byte[] tooBig = new byte[SchematicLimits.MAX_BYTES + 1];
		tooBig[0] = 0x1F;
		tooBig[1] = (byte) 0x8B;

		try (Player alice = new Player("Alice")) {
			alice.createGroup("Alpha");
			alice.send(SharePackets.share(UUID.randomUUID(), "Wall", tooBig));

			Message reply = alice.receive();
			assertEquals(MessageType.ERROR, reply.type());
			assertTrue(reply.reader().readString().contains("limit"));
			assertTrue(alice.awaitClose());
		}
	}

	private void together(Player owner, Player... members) throws IOException {
		owner.createGroup("Alpha");

		for (int i = 0; i < members.length; i++) {
			Player joining = members[i];
			owner.send(MessageType.GROUP_INVITE, joining.name);
			assertTrue(owner.notice().startsWith("Invited "));
			joining.await(MessageType.INVITE);
			joining.send(MessageType.GROUP_ACCEPT, "Alpha");
			assertEquals("You joined Alpha.", joining.notice());
			joining.groupUpdate();
			assertEquals(joining.name + " joined Alpha.", owner.notice());
			owner.groupUpdate();

			for (int earlier = 0; earlier < i; earlier++) {
				assertEquals(joining.name + " joined Alpha.", members[earlier].notice());
				members[earlier].groupUpdate();
			}
		}
	}

	private static byte[] placement(byte... tail) {
		byte[] data = new byte[2 + tail.length];
		data[0] = 0x1F;
		data[1] = (byte) 0x8B;
		System.arraycopy(tail, 0, data, 2, tail.length);
		return data;
	}

	private static void assertShared(Message message, UUID transferId, String sender, String name, byte[] data)
			throws ProtocolException {
		assertEquals(MessageType.SCHEM_SHARED, message.type());
		PacketReader reader = message.reader();
		assertEquals(transferId, reader.readUuid());
		assertEquals(sender, reader.readString());
		assertEquals(name, reader.readString());
		assertArrayEquals(data, reader.readBytes(SchematicLimits.MAX_BYTES));
		assertTrue(reader.atEnd());
	}

	private static void assertFalseInGroup(Player player) throws IOException {
		Message update = player.await(MessageType.GROUP_UPDATE);
		assertEquals(false, update.reader().readBoolean());
	}

	private final class Player implements AutoCloseable {
		private final TestClient client;
		private final String name;

		Player(String name) throws IOException {
			this.name = name;
			PlayerDirectory.Issued invite = issued.computeIfAbsent(name.toLowerCase(), ignored -> {
				try {
					return players.issue(name);
				} catch (IOException failed) {
					throw new IllegalStateException(failed);
				}
			});

			this.client = new TestClient(server);
			client.sendAuth(invite.token());

			Message authResult = client.receive();
			assertEquals(MessageType.AUTH_RESULT, authResult.type());
			assertTrue(authResult.reader().readBoolean(), "sign in should succeed");
			assertEquals(MessageType.GROUP_UPDATE, client.receive().type());
		}

		Message receive() throws IOException {
			return client.receive();
		}

		boolean awaitClose() throws IOException {
			return client.awaitClose();
		}

		void send(Message message) throws IOException {
			client.send(message);
		}

		void send(MessageType type, String value) throws IOException {
			client.send(new PacketWriter().writeString(value).toMessage(type));
		}

		void createGroup(String groupName) throws IOException {
			send(MessageType.GROUP_CREATE, groupName);
			notice();
			groupUpdate();
		}

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

		void groupUpdate() throws IOException {
			await(MessageType.GROUP_UPDATE);
		}

		void expectWentOffline(String playerName) throws IOException {
			assertEquals(playerName + " went offline.", notice());
			groupUpdate();
		}

		/** The next message must be the answer to a ping, meaning no share was queued. */
		void assertNothingElse(MessageType ping) throws IOException {
			send(Message.of(ping));
			assertEquals(MessageType.PONG, client.receive().type());
		}

		@Override
		public void close() throws IOException {
			client.close();
		}
	}
}
