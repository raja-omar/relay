package dev.relay.common.protocol;

/**
 * Every kind of message that can cross the wire, with a fixed id per type.
 *
 * <p>The ids are part of the wire format: change one and old clients start misreading messages.
 * Add new types in the gaps instead, and never reuse an id.
 */
public enum MessageType {
	// Client -> server.
	AUTH(1),
	GROUP_CREATE(2),
	GROUP_INVITE(3),
	GROUP_ACCEPT(4),
	GROUP_DECLINE(5),
	GROUP_LEAVE(6),
	GROUP_INFO(7),
	SCHEM_SHARE(8),
	SCHEM_REQUEST(9),
	BLOCK_PING(60),

	// Server -> client.
	AUTH_RESULT(20),
	GROUP_UPDATE(21),
	INVITE(22),
	SCHEM_SHARED(23),
	SCHEM_TRANSFER(24),
	ERROR(25),
	NOTICE(26),
	BLOCK_PINGED(61),

	// Either direction.
	PING(40),
	PONG(41);

	// 62+ is left free.

	private static final MessageType[] BY_ID = new MessageType[256];

	static {
		for (MessageType type : values()) {
			BY_ID[type.id] = type;
		}
	}

	private final int id;

	MessageType(int id) {
		this.id = id;
	}

	public int id() {
		return id;
	}

	public static MessageType fromId(int id) throws ProtocolException {
		MessageType type = id >= 0 && id < BY_ID.length ? BY_ID[id] : null;

		if (type == null) {
			throw new ProtocolException("Unknown message type " + id);
		}

		return type;
	}
}
