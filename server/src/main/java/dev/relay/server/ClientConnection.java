package dev.relay.server;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.relay.common.protocol.Message;
import dev.relay.common.protocol.MessageType;
import dev.relay.common.protocol.PacketWriter;
import dev.relay.common.protocol.Protocol;

/**
 * One connected socket.
 *
 * <p>Reads happen on the thread that called {@link #readMessage()}, writes on a thread running
 * {@link #runWriteLoop()}. Splitting them matters once schematics are involved: relaying a few
 * megabytes to a slow client must not stall the player who shared it.
 *
 * <p>Closing always goes through {@link #disconnect(String)}, which stops accepting new messages,
 * lets the writer flush whatever is already queued -- an error message, usually -- and then closes
 * the socket, which in turn unblocks the reader.
 */
public final class ClientConnection {
	/**
	 * How many messages may be waiting for one client. A client that falls this far behind is not
	 * coming back, and queueing more would just consume the server's memory on its behalf.
	 */
	private static final int SEND_QUEUE_CAPACITY = 64;

	private static final long POLL_MILLIS = 200;

	private final Socket socket;
	private final DataInputStream in;
	private final DataOutputStream out;
	private final BlockingQueue<Message> outgoing = new ArrayBlockingQueue<>(SEND_QUEUE_CAPACITY);
	private final AtomicBoolean closing = new AtomicBoolean();
	private final String description;
	private final java.net.InetAddress remoteAddress;

	private volatile ClientSession session;
	private volatile int maxFrameBytes = Protocol.MAX_PRE_AUTH_FRAME_BYTES;

	ClientConnection(Socket socket) throws IOException {
		this.socket = socket;
		this.socket.setTcpNoDelay(true);
		this.in = new DataInputStream(new BufferedInputStream(socket.getInputStream(), 64 * 1024));
		this.out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream(), 64 * 1024));
		this.description = String.valueOf(socket.getRemoteSocketAddress());
		this.remoteAddress = socket.getInetAddress();
		try {
			this.socket.setSoTimeout(AbuseLimits.PRE_AUTH_TIMEOUT_MILLIS);
		} catch (SocketException ignored) {
			// The read loop will notice a dead socket the same way.
		}
	}

	/** The authenticated player, or null while this connection is still anonymous. */
	public ClientSession session() {
		return session;
	}

	void session(ClientSession session) {
		this.session = session;
	}

	public boolean isClosing() {
		return closing.get();
	}

	public java.net.InetAddress remoteAddress() {
		return remoteAddress;
	}

	/** After AUTH, schematic-sized frames are allowed and idle reads no longer time out. */
	void admit() {
		maxFrameBytes = Protocol.MAX_FRAME_BYTES;

		try {
			socket.setSoTimeout(0);
		} catch (SocketException ignored) {
			// Already closing.
		}
	}

	Message readMessage() throws IOException {
		return Protocol.read(in, maxFrameBytes);
	}

	/** Queues a message. Never blocks, and never throws: a failed send only ends this connection. */
	public void send(Message message) {
		if (closing.get()) {
			return;
		}

		if (!outgoing.offer(message)) {
			Log.warn(this + " is not keeping up, dropping it");
			disconnect("too far behind");
		}
	}

	public void sendError(String reason) {
		send(new PacketWriter().writeString(reason).toMessage(MessageType.ERROR));
	}

	/** Sends {@code reason} as an error and then hangs up. */
	public void reject(String reason) {
		sendError(reason);
		disconnect(reason);
	}

	/**
	 * Stops accepting messages and asks the writer to flush and close. Safe to call repeatedly and
	 * from any thread.
	 */
	public void disconnect(String reason) {
		if (closing.compareAndSet(false, true)) {
			Log.info("Disconnecting " + this + ": " + reason);
		}
	}

	void runWriteLoop() {
		try {
			while (true) {
				Message message = outgoing.poll(POLL_MILLIS, TimeUnit.MILLISECONDS);

				if (message != null) {
					Protocol.write(out, message);
				} else if (closing.get()) {
					return;
				}
			}
		} catch (IOException broken) {
			// The client is gone or stopped reading. Nothing to do but close, below.
			Log.info(this + " write failed: " + broken.getMessage());
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
		} finally {
			closing.set(true);
			closeSocket();
		}
	}

	/** Closes the socket, which unblocks anyone waiting in {@link #readMessage()}. */
	void closeSocket() {
		try {
			socket.close();
		} catch (IOException ignored) {
			// Already gone, which is the outcome we wanted.
		}
	}

	@Override
	public String toString() {
		return session == null ? description : session.playerName() + "@" + description;
	}
}
