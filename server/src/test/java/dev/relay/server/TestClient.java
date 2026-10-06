package dev.relay.server;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import dev.relay.common.Tls;
import dev.relay.common.protocol.Message;
import dev.relay.common.protocol.MessageType;
import dev.relay.common.protocol.PacketWriter;
import dev.relay.common.protocol.Protocol;

/**
 * A plain socket client for the tests: no Minecraft, no mod, just the protocol. Exactly the "simple
 * client" the server should be exercised with before the mod is involved.
 */
final class TestClient implements AutoCloseable {
	private static final int TIMEOUT_MILLIS = 5000;

	private final Socket socket;
	private final DataInputStream in;
	private final DataOutputStream out;

	TestClient(RelayServer server) throws IOException {
		this(server.port(), server.tlsPin());
	}

	TestClient(int port, String tlsPin) throws IOException {
		socket = Tls.pinning(tlsPin).connect("127.0.0.1", port, TIMEOUT_MILLIS);
		socket.setSoTimeout(TIMEOUT_MILLIS);
		in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
		out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
	}

	void send(Message message) throws IOException {
		Protocol.write(out, message);
	}

	void sendAuth(String token) throws IOException {
		sendAuth(Protocol.VERSION, token);
	}

	void sendAuth(int version, String token) throws IOException {
		send(new PacketWriter()
				.writeInt(version)
				.writeString(token)
				.toMessage(MessageType.AUTH));
	}

	/** Writes bytes that are not a valid frame. */
	void sendRaw(byte... bytes) throws IOException {
		out.write(bytes);
		out.flush();
	}

	Message receive() throws IOException {
		return Protocol.read(in);
	}

	/** Reads and discards messages until the server hangs up. */
	boolean awaitClose() throws IOException {
		try {
			while (true) {
				receive();
			}
		} catch (SocketTimeoutException stillOpen) {
			return false;
		} catch (SocketException | java.io.EOFException closed) {
			return true;
		}
	}

	@Override
	public void close() throws IOException {
		socket.close();
	}
}
