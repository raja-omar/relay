package dev.relay.common.protocol;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Reads a message payload one field at a time.
 *
 * <p>Everything here assumes the payload came from someone else and may be nonsense: a field that
 * runs past the end of the payload, or claims a size we will not accept, raises
 * {@link ProtocolException} rather than returning something half-built.
 */
public final class PacketReader {
	private final DataInputStream in;

	public PacketReader(byte[] payload) {
		this.in = new DataInputStream(new ByteArrayInputStream(payload));
	}

	public boolean readBoolean() throws ProtocolException {
		return read(in::readBoolean);
	}

	public int readInt() throws ProtocolException {
		return read(in::readInt);
	}

	public long readLong() throws ProtocolException {
		return read(in::readLong);
	}

	public String readString() throws ProtocolException {
		int length = readInt();

		if (length < 0 || length > Protocol.MAX_STRING_BYTES) {
			throw new ProtocolException("String field claims to be " + length + " bytes");
		}

		byte[] bytes = new byte[length];
		read(() -> {
			in.readFully(bytes);
			return null;
		});
		return new String(bytes, StandardCharsets.UTF_8);
	}

	public UUID readUuid() throws ProtocolException {
		return new UUID(readLong(), readLong());
	}

	/**
	 * @param maximumLength the largest blob the caller is willing to accept, checked before
	 *                      allocating anything
	 */
	public byte[] readBytes(int maximumLength) throws ProtocolException {
		int length = readInt();

		if (length < 0 || length > maximumLength) {
			throw new ProtocolException("Byte field claims to be " + length + " bytes, limit is " + maximumLength);
		}

		byte[] bytes = new byte[length];
		read(() -> {
			in.readFully(bytes);
			return null;
		});
		return bytes;
	}

	/** True when every byte of the payload has been read. */
	public boolean atEnd() {
		try {
			return in.available() == 0;
		} catch (IOException impossible) {
			return true;
		}
	}

	private <T> T read(Field<T> field) throws ProtocolException {
		try {
			return field.read();
		} catch (EOFException truncated) {
			throw new ProtocolException("Message ended in the middle of a field");
		} catch (IOException impossible) {
			throw new ProtocolException("Could not read field: " + impossible.getMessage());
		}
	}

	private interface Field<T> {
		T read() throws IOException;
	}
}
