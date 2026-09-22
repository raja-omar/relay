package dev.relay.common.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.Test;

class ProtocolTest {
	@Test
	void roundTripsAMessage() throws IOException {
		Message written = new Message(MessageType.AUTH, new byte[] {1, 2, 3});

		Message read = readBack(frame(written));

		assertEquals(MessageType.AUTH, read.type());
		assertArrayEquals(new byte[] {1, 2, 3}, read.payload());
	}

	@Test
	void roundTripsAMessageWithNoPayload() throws IOException {
		Message read = readBack(frame(Message.of(MessageType.PING)));

		assertEquals(MessageType.PING, read.type());
		assertEquals(0, read.payload().length);
	}

	@Test
	void readsSeveralMessagesFromOneStream() throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(bytes);
		Protocol.write(out, new Message(MessageType.AUTH, new byte[] {7}));
		Protocol.write(out, Message.of(MessageType.PING));
		Protocol.write(out, new Message(MessageType.ERROR, "nope".getBytes()));

		DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));

		assertEquals(MessageType.AUTH, Protocol.read(in).type());
		assertEquals(MessageType.PING, Protocol.read(in).type());
		assertEquals(MessageType.ERROR, Protocol.read(in).type());
	}

	/** The point of the length prefix: a frame split across many reads still arrives whole. */
	@Test
	void reassemblesAMessageDeliveredOneByteAtATime() throws IOException {
		byte[] payload = new byte[5000];

		for (int i = 0; i < payload.length; i++) {
			payload[i] = (byte) i;
		}

		byte[] frame = frame(new Message(MessageType.SCHEM_TRANSFER, payload));
		Message read = Protocol.read(new DataInputStream(oneByteAtATime(frame)));

		assertEquals(MessageType.SCHEM_TRANSFER, read.type());
		assertArrayEquals(payload, read.payload());
	}

	@Test
	void rejectsAFrameLongerThanTheLimit() {
		ProtocolException thrown = assertThrows(ProtocolException.class, () -> readBack(lengthOnly(Integer.MAX_VALUE)));

		assertEquals(true, thrown.getMessage().contains("exceeds the limit"));
	}

	@Test
	void rejectsAFrameLongerThanACallerSuppliedCap() {
		byte[] header = lengthOnly(Protocol.MAX_PRE_AUTH_FRAME_BYTES + 1);
		ProtocolException thrown = assertThrows(ProtocolException.class, () -> Protocol.read(
				new DataInputStream(new ByteArrayInputStream(header)), Protocol.MAX_PRE_AUTH_FRAME_BYTES));

		assertEquals(true, thrown.getMessage().contains("exceeds the limit"));
	}

	@Test
	void rejectsAnEmptyOrNegativeFrame() {
		assertThrows(ProtocolException.class, () -> readBack(lengthOnly(0)));
		assertThrows(ProtocolException.class, () -> readBack(lengthOnly(-1)));
	}

	@Test
	void rejectsAnUnknownMessageType() {
		assertThrows(ProtocolException.class, () -> readBack(new byte[] {0, 0, 0, 1, (byte) 200}));
	}

	@Test
	void rejectsAFrameThatStopsEarly() {
		// Claims eight payload bytes, supplies two.
		assertThrows(EOFException.class, () -> readBack(new byte[] {0, 0, 0, 9, 1, 1, 2}));
	}

	@Test
	void reportsEndOfStreamBetweenMessages() {
		assertThrows(EOFException.class, () -> readBack(new byte[0]));
	}

	private static byte[] frame(Message message) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		Protocol.write(new DataOutputStream(bytes), message);
		return bytes.toByteArray();
	}

	private static Message readBack(byte[] frame) throws IOException {
		return Protocol.read(new DataInputStream(new ByteArrayInputStream(frame)));
	}

	private static byte[] lengthOnly(int length) {
		return new byte[] {
				(byte) (length >>> 24), (byte) (length >>> 16), (byte) (length >>> 8), (byte) length,
		};
	}

	private static InputStream oneByteAtATime(byte[] source) {
		return new InputStream() {
			private int position;

			@Override
			public int read() {
				return position < source.length ? source[position++] & 0xFF : -1;
			}

			@Override
			public int read(byte[] target, int offset, int length) {
				if (position >= source.length) {
					return -1;
				}

				target[offset] = source[position++];
				return 1;
			}
		};
	}
}
