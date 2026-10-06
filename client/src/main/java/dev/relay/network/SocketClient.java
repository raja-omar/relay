package dev.relay.network;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import dev.relay.ModInfo;
import dev.relay.common.Tls;
import dev.relay.common.protocol.Message;
import dev.relay.common.protocol.MessageType;
import dev.relay.common.protocol.Protocol;

/**
 * The mod's end of the socket.
 *
 * <p>Nothing here runs on Minecraft's thread. Connecting, reading and writing all happen on this
 * class's own threads, and incoming messages are handed to the listener from a network thread --
 * the listener is responsible for getting back onto the client thread before touching the game.
 * That is the rule that keeps an unreachable server from ever freezing Minecraft.
 *
 * <p>When a connection drops unexpectedly it is retried with a growing delay until
 * {@link #disconnect} is called, so a server restart does not need the player to do anything.
 */
public final class SocketClient {
	public enum Status {
		DISCONNECTED,
		CONNECTING,
		CONNECTED,
	}

	/** Called from network threads. */
	public interface Listener {
		void onConnected();

		void onMessage(Message message);

		/** @param willRetry whether a reconnect has been scheduled */
		void onDisconnected(String reason, boolean willRetry);
	}

	private static final int CONNECT_TIMEOUT_MILLIS = 5000;
	private static final int SEND_QUEUE_CAPACITY = 64;
	private static final long KEEPALIVE_SECONDS = 30;
	private static final long FIRST_RETRY_SECONDS = 2;
	private static final long MAX_RETRY_SECONDS = 30;

	private final Listener listener;
	private final ExecutorService threads = Executors.newCachedThreadPool(
			Thread.ofPlatform().name(ModInfo.ID + "-net-", 1).daemon(true).factory());
	private final ScheduledExecutorService timers = Executors.newSingleThreadScheduledExecutor(
			Thread.ofPlatform().name(ModInfo.ID + "-timer").daemon(true).factory());

	private volatile Status status = Status.DISCONNECTED;
	private volatile Target target;
	private volatile Connection connection;
	private volatile boolean wanted;
	private volatile long retrySeconds = FIRST_RETRY_SECONDS;

	public SocketClient(Listener listener) {
		this.listener = listener;
		timers.scheduleWithFixedDelay(this::sendKeepalive, KEEPALIVE_SECONDS, KEEPALIVE_SECONDS, TimeUnit.SECONDS);
	}

	public Status status() {
		return status;
	}

	/** Where we are connected, or trying to connect, in {@code host:port} form. */
	public String address() {
		Target current = target;
		return current == null ? "nowhere" : current.host() + ":" + current.port();
	}

	/** Starts connecting in the background. Returns immediately. */
	public void connect(Target target) {
		disconnect("reconnecting");
		this.target = target;
		this.wanted = true;
		this.retrySeconds = FIRST_RETRY_SECONDS;
		threads.execute(this::openConnection);
	}

	/** Hangs up and stops retrying. */
	public void disconnect(String reason) {
		wanted = false;
		Connection open = connection;

		if (open != null) {
			open.close(reason);
		}
	}

	public boolean send(Message message) {
		Connection open = connection;

		if (open == null || status != Status.CONNECTED) {
			return false;
		}

		return open.enqueue(message);
	}

	/** Called when Minecraft is closing. */
	public void shutdown() {
		disconnect("Minecraft is closing");
		timers.shutdownNow();
		threads.shutdownNow();
	}

	private void openConnection() {
		Target attempt = target;

		if (!wanted || attempt == null) {
			return;
		}

		status = Status.CONNECTING;
		ModInfo.LOG.info("Connecting to {}", address());

		if (!Tls.isWellFormedPin(attempt.tlsPin())) {
			wanted = false;
			finished(null, "No TLS pin. Pack tlsPin= in the jar, or /relay connect <host> <port> <pin>.");
			return;
		}

		Socket socket = null;

		try {
			Tls tls = Tls.pinning(attempt.tlsPin());
			socket = tls.connect(attempt.host(), attempt.port(), CONNECT_TIMEOUT_MILLIS);
			socket.setTcpNoDelay(true);

			Connection open = new Connection(socket);
			connection = open;
			status = Status.CONNECTED;
			retrySeconds = FIRST_RETRY_SECONDS;

			threads.execute(open::writeLoop);
			open.enqueue(attempt.authMessage());
			listener.onConnected();
			open.readLoop();
		} catch (IOException failed) {
			closeQuietly(socket);
			ModInfo.LOG.info("Could not reach {}: {}", address(), failed.getMessage());

			if (Tls.isPinMismatch(failed)) {
				wanted = false;
				finished(null, "TLS pin does not match this server. Pack the pin from the server log.");
				return;
			}

			finished(null, "Could not reach " + address());
		}
	}

	/**
	 * Runs once a connection has ended. {@code ended} is the socket that died, or {@code null} when
	 * we never got one; a later connection is left alone so a reconnect cannot be wiped out by the
	 * previous one's cleanup.
	 */
	private void finished(Connection ended, String reason) {
		if (ended != null && connection != ended) {
			return;
		}

		connection = null;
		status = Status.DISCONNECTED;

		boolean willRetry = wanted;
		listener.onDisconnected(reason, willRetry);

		if (!willRetry) {
			return;
		}

		long delay = retrySeconds;
		retrySeconds = Math.min(retrySeconds * 2, MAX_RETRY_SECONDS);
		ModInfo.LOG.info("Retrying in {}s", delay);
		timers.schedule(() -> threads.execute(this::openConnection), delay, TimeUnit.SECONDS);
	}

	private void sendKeepalive() {
		if (status == Status.CONNECTED) {
			send(Message.of(MessageType.PING));
		}
	}

	private static void closeQuietly(Socket socket) {
		if (socket == null) {
			return;
		}

		try {
			socket.close();
		} catch (IOException ignored) {
			// Already gone.
		}
	}

	/** One live socket: a reader loop, a writer loop, and the queue between them. */
	private final class Connection {
		private final Socket socket;
		private final DataInputStream in;
		private final DataOutputStream out;
		private final BlockingQueue<Message> outgoing = new ArrayBlockingQueue<>(SEND_QUEUE_CAPACITY);

		private volatile boolean closing;
		private volatile String closeReason = "disconnected";

		Connection(Socket socket) throws IOException {
			this.socket = socket;
			this.in = new DataInputStream(new BufferedInputStream(socket.getInputStream(), 64 * 1024));
			this.out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream(), 64 * 1024));
		}

		boolean enqueue(Message message) {
			if (closing) {
				return false;
			}

			if (!outgoing.offer(message)) {
				ModInfo.LOG.warn("Send queue is full, dropping the connection");
				close("the connection could not keep up");
				return false;
			}

			return true;
		}

		void readLoop() {
			try {
				while (!closing) {
					listener.onMessage(Protocol.read(in));
				}
			} catch (IOException ended) {
				if (!closing) {
					closeReason = ended.getMessage() == null ? "connection lost" : "connection lost: " + ended.getMessage();
					ModInfo.LOG.info("Connection to {} ended: {}", address(), ended);
				}
			} catch (RuntimeException unexpected) {
				// A bug on our side must not leave the mod thinking it is still connected.
				ModInfo.LOG.error("Failed while handling a message", unexpected);
				closeReason = "internal error";
			} finally {
				closing = true;
				closeQuietly(socket);
				finished(this, closeReason);
			}
		}

		void writeLoop() {
			try {
				while (true) {
					Message message = outgoing.poll(200, TimeUnit.MILLISECONDS);

					if (message != null) {
						Protocol.write(out, message);
					} else if (closing) {
						return;
					}
				}
			} catch (IOException broken) {
				ModInfo.LOG.info("Could not write to {}: {}", address(), broken.getMessage());
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
			} finally {
				closing = true;
				closeQuietly(socket);
			}
		}

		void close(String reason) {
			closeReason = reason;
			closing = true;
			closeQuietly(socket);
		}
	}
}
