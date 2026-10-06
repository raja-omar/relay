package dev.relay.common.protocol;

import java.util.UUID;

/**
 * The two messages that carry a placement across the wire.
 *
 * <p>{@code SCHEM_SHARE} is what the sender posts: a fresh id, the placement name, and gzip NBT.
 * The server copies that to the rest of the group as {@code SCHEM_SHARED}, adding the sender's
 * name. The id is how both sides recognise a replay.
 */
public final class SharePackets {
	public static Message share(UUID id, String name, byte[] data) {
		return new PacketWriter()
				.writeUuid(id)
				.writeString(name)
				.writeBytes(data)
				.toMessage(MessageType.SCHEM_SHARE);
	}

	public static Message shared(UUID id, String sender, String name, byte[] data) {
		return new PacketWriter()
				.writeUuid(id)
				.writeString(sender)
				.writeString(name)
				.writeBytes(data)
				.toMessage(MessageType.SCHEM_SHARED);
	}

	private SharePackets() {
	}
}
