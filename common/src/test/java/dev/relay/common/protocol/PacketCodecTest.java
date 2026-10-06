package dev.relay.common.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class PacketCodecTest {
	@Test
	void roundTripsEveryFieldType() throws ProtocolException {
		UUID playerId = UUID.randomUUID();
		Message message = new PacketWriter()
				.writeBoolean(true)
				.writeInt(-42)
				.writeLong(1234567890123L)
				.writeString("Fortress_Wall_03.litematic")
				.writeUuid(playerId)
				.writeBytes(new byte[] {9, 8, 7})
				.toMessage(MessageType.SCHEM_SHARE);

		PacketReader reader = message.reader();

		assertEquals(MessageType.SCHEM_SHARE, message.type());
		assertTrue(reader.readBoolean());
		assertEquals(-42, reader.readInt());
		assertEquals(1234567890123L, reader.readLong());
		assertEquals("Fortress_Wall_03.litematic", reader.readString());
		assertEquals(playerId, reader.readUuid());
		assertArrayEquals(new byte[] {9, 8, 7}, reader.readBytes(16));
		assertTrue(reader.atEnd());
	}

	@Test
	void sharePacketsCarryIdNameAndBytes() throws ProtocolException {
		UUID id = UUID.randomUUID();
		byte[] data = { 0x1F, (byte) 0x8B, 0x08 };

		Message share = SharePackets.share(id, "Wall", data);
		PacketReader shareReader = share.reader();

		assertEquals(MessageType.SCHEM_SHARE, share.type());
		assertEquals(id, shareReader.readUuid());
		assertEquals("Wall", shareReader.readString());
		assertArrayEquals(data, shareReader.readBytes(16));
		assertTrue(shareReader.atEnd());

		Message shared = SharePackets.shared(id, "Alice", "Wall", data);
		PacketReader sharedReader = shared.reader();

		assertEquals(MessageType.SCHEM_SHARED, shared.type());
		assertEquals(id, sharedReader.readUuid());
		assertEquals("Alice", sharedReader.readString());
		assertEquals("Wall", sharedReader.readString());
		assertArrayEquals(data, sharedReader.readBytes(16));
		assertTrue(sharedReader.atEnd());
	}

	@Test
	void roundTripsAwkwardStrings() throws ProtocolException {
		Message message = new PacketWriter()
				.writeString("")
				.writeString("château \u00e9\u00e8 \uD83D\uDE00")
				.toMessage(MessageType.ERROR);

		PacketReader reader = message.reader();

		assertEquals("", reader.readString());
		assertEquals("château \u00e9\u00e8 \uD83D\uDE00", reader.readString());
	}

	@Test
	void refusesToWriteAnOversizeString() {
		String tooLong = "x".repeat(Protocol.MAX_STRING_BYTES + 1);

		assertThrows(IllegalArgumentException.class, () -> new PacketWriter().writeString(tooLong));
	}

	@Test
	void rejectsAStringClaimingMoreThanTheLimit() {
		Message message = new PacketWriter().writeInt(Protocol.MAX_STRING_BYTES + 1).toMessage(MessageType.ERROR);

		assertThrows(ProtocolException.class, () -> message.reader().readString());
	}

	@Test
	void rejectsANegativeLength() {
		Message message = new PacketWriter().writeInt(-1).toMessage(MessageType.ERROR);

		assertThrows(ProtocolException.class, () -> message.reader().readString());
	}

	@Test
	void rejectsBytesBeyondWhatTheCallerAllows() {
		Message message = new PacketWriter().writeBytes(new byte[100]).toMessage(MessageType.SCHEM_TRANSFER);

		assertThrows(ProtocolException.class, () -> message.reader().readBytes(99));
	}

	@Test
	void rejectsAFieldThatRunsPastTheEnd() {
		Message message = new PacketWriter().writeInt(1).toMessage(MessageType.AUTH);
		PacketReader reader = message.reader();

		assertThrows(ProtocolException.class, () -> {
			reader.readInt();
			reader.readLong();
		});
	}

	@Test
	void rejectsAStringWhoseBytesAreMissing() {
		// Says forty bytes follow, then stops.
		Message message = new PacketWriter().writeInt(40).toMessage(MessageType.AUTH);

		assertThrows(ProtocolException.class, () -> message.reader().readString());
	}

	@Test
	void knowsWhenMoreFieldsRemain() throws ProtocolException {
		PacketReader reader = new PacketWriter().writeInt(1).writeInt(2).toMessage(MessageType.AUTH).reader();

		assertFalse(reader.atEnd());
		reader.readInt();
		assertFalse(reader.atEnd());
		reader.readInt();
		assertTrue(reader.atEnd());
	}

	@Test
	void mapsEveryTypeToAStableId() throws ProtocolException {
		for (MessageType type : MessageType.values()) {
			assertEquals(type, MessageType.fromId(type.id()));
		}
	}

	@Test
	void rejectsAnIdNobodyDefined() {
		assertThrows(ProtocolException.class, () -> MessageType.fromId(0));
		assertThrows(ProtocolException.class, () -> MessageType.fromId(255));
		assertThrows(ProtocolException.class, () -> MessageType.fromId(-1));
	}
}
