package dev.relay.common.protocol;

/**
 * A block ping shared with a group. The payload is only the block and the face that was aimed at.
 * Color, how long it stays, and whether one beam replaces another are each client's own settings.
 */
public final class PingPackets {
	public static Message ping(int x, int y, int z, int face, String dimension) {
		return new PacketWriter()
				.writeInt(x)
				.writeInt(y)
				.writeInt(z)
				.writeInt(face)
				.writeString(dimension)
				.toMessage(MessageType.BLOCK_PING);
	}

	public static Message pinged(String sender, int x, int y, int z, int face, String dimension) {
		return new PacketWriter()
				.writeString(sender)
				.writeInt(x)
				.writeInt(y)
				.writeInt(z)
				.writeInt(face)
				.writeString(dimension)
				.toMessage(MessageType.BLOCK_PINGED);
	}

	private PingPackets() {
	}
}
