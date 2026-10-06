package dev.relay.common.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * The frame format, and the limits that go with it.
 *
 * <p>One frame looks like:
 *
 * <pre>
 * [int32 length][uint8 type][payload ...]
 * </pre>
 *
 * <p>{@code length} counts the type byte plus the payload, so a message with no payload has
 * length 1. Reads use {@code readFully}, so a frame arriving in several TCP chunks is fine; the
 * length is checked before anything is allocated, so a bogus length cannot exhaust memory.
 */
public final class Protocol {
	/** Bumped whenever the meaning of a message changes. Mismatches are refused at login. */
	public static final int VERSION = 4;

	/**
	 * Hard ceiling for a single frame. This is a "something is wrong" guard, not the schematic
	 * size limit -- that one is smaller and configurable, and lives with the transfer code.
	 */
	public static final int MAX_FRAME_BYTES = 32 * 1024 * 1024;

	/** Ceiling for one string field, generous for a filename and small for an attacker. */
	public static final int MAX_STRING_BYTES = 32 * 1024;

	/** Bytes a client may send in one frame before it has signed in. AUTH and PING fit easily. */
	public static final int MAX_PRE_AUTH_FRAME_BYTES = 4 * 1024;

	public static void write(DataOutputStream out, Message message) throws IOException {
		byte[] payload = message.payload();
		long length = 1L + payload.length;

		if (length > MAX_FRAME_BYTES) {
			throw new ProtocolException("Refusing to send " + length + " bytes, the limit is " + MAX_FRAME_BYTES);
		}

		// One write per frame keeps frames from interleaving if this ever gets called concurrently.
		byte[] frame = new byte[4 + (int) length];
		frame[0] = (byte) (length >>> 24);
		frame[1] = (byte) (length >>> 16);
		frame[2] = (byte) (length >>> 8);
		frame[3] = (byte) length;
		frame[4] = (byte) message.type().id();
		System.arraycopy(payload, 0, frame, 5, payload.length);

		out.write(frame);
		out.flush();
	}

	public static Message read(DataInputStream in) throws IOException {
		return read(in, MAX_FRAME_BYTES);
	}

	/**
	 * Reads one whole frame, blocking until it has arrived.
	 *
	 * @throws java.io.EOFException when the other side closed cleanly between frames
	 * @throws ProtocolException when the frame could not possibly be valid
	 */
	public static Message read(DataInputStream in, int maxFrameBytes) throws IOException {
		int length = in.readInt();

		if (length < 1) {
			throw new ProtocolException("Frame length " + length + " is not a frame");
		}

		int limit = maxFrameBytes > 0 ? maxFrameBytes : MAX_FRAME_BYTES;

		if (length > limit) {
			throw new ProtocolException("Frame of " + length + " bytes exceeds the limit of " + limit);
		}

		MessageType type = MessageType.fromId(in.readUnsignedByte());
		byte[] payload = new byte[length - 1];
		in.readFully(payload);
		return new Message(type, payload);
	}

	private Protocol() {
	}
}
