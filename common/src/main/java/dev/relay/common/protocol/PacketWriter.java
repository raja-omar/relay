package dev.relay.common.protocol;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Builds a message payload one field at a time.
 *
 * <p>Writes go to memory, so they cannot really fail; an {@link IOException} from the underlying
 * stream would mean something impossible happened and is rethrown unchecked.
 */
public final class PacketWriter {
	private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
	private final DataOutputStream out = new DataOutputStream(buffer);

	public PacketWriter writeBoolean(boolean value) {
		return write(() -> out.writeBoolean(value));
	}

	public PacketWriter writeInt(int value) {
		return write(() -> out.writeInt(value));
	}

	public PacketWriter writeLong(long value) {
		return write(() -> out.writeLong(value));
	}

	/** UTF-8, length prefixed. Not {@code writeUTF}, which has its own 64 KB modified-UTF-8 rules. */
	public PacketWriter writeString(String value) {
		byte[] bytes = value.getBytes(StandardCharsets.UTF_8);

		if (bytes.length > Protocol.MAX_STRING_BYTES) {
			throw new IllegalArgumentException(
					"String of " + bytes.length + " bytes exceeds the limit of " + Protocol.MAX_STRING_BYTES);
		}

		return write(() -> {
			out.writeInt(bytes.length);
			out.write(bytes);
		});
	}

	public PacketWriter writeUuid(UUID value) {
		return write(() -> {
			out.writeLong(value.getMostSignificantBits());
			out.writeLong(value.getLeastSignificantBits());
		});
	}

	public PacketWriter writeBytes(byte[] value) {
		return write(() -> {
			out.writeInt(value.length);
			out.write(value);
		});
	}

	public Message toMessage(MessageType type) {
		return new Message(type, buffer.toByteArray());
	}

	private PacketWriter write(Field field) {
		try {
			field.write();
		} catch (IOException impossible) {
			throw new UncheckedIOException("Writing to memory failed", impossible);
		}

		return this;
	}

	private interface Field {
		void write() throws IOException;
	}
}
