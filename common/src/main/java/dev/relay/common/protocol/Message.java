package dev.relay.common.protocol;

/**
 * One message: a type and its raw payload bytes.
 *
 * <p>The payload array is not copied. Schematics are megabytes, and copying them on every hop
 * would be wasteful for no gain -- nothing in this codebase mutates a payload after building it.
 */
public record Message(MessageType type, byte[] payload) {
	private static final byte[] NO_PAYLOAD = new byte[0];

	public Message {
		if (type == null) {
			throw new IllegalArgumentException("type is required");
		}

		if (payload == null) {
			payload = NO_PAYLOAD;
		}
	}

	public static Message of(MessageType type) {
		return new Message(type, NO_PAYLOAD);
	}

	/** Reads the payload field by field. */
	public PacketReader reader() {
		return new PacketReader(payload);
	}

	@Override
	public String toString() {
		return type + "(" + payload.length + " bytes)";
	}
}
