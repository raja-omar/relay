package dev.relay.server;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import dev.relay.common.protocol.Message;
import dev.relay.common.protocol.ProtocolException;

/**
 * The server: accept connections, read messages from them, hand each one to the router.
 *
 * <p>Two threads per connection, one reading and one writing. That is more threads than a selector
 * loop would use and far easier to follow, and this is sized for a faction, not a datacentre.
 */
public final class RelayServer implements Closeable {
	/** A crude flood guard, so a connect loop cannot spawn threads without limit. */
	private static final int MAX_CONNECTIONS = 200;

	private final ServerConfig config;
	private final SessionRegistry sessions = new SessionRegistry();
	private final MessageRouter router;
	private final Set<ClientConnection> connections = ConcurrentHashMap.newKeySet();
	private final CountDownLatch stopped = new CountDownLatch(1);
	private final ExecutorService threads = Executors.newCachedThreadPool(
			Thread.ofPlatform().name("relay-", 1).daemon(true).factory());

	private volatile ServerSocket serverSocket;
	private volatile boolean running;

	public RelayServer(ServerConfig config) {
		this.config = config;
		this.router = new MessageRouter(config, sessions);
	}

	/** Binds the port before returning, so a port clash surfaces here rather than in a thread. */
	public void start() throws IOException {
		serverSocket = new ServerSocket(config.port());
		running = true;
		threads.execute(this::acceptLoop);

		Log.info("Listening on port " + port()
				+ (config.requiresSecret() ? " (secret required)" : " (no secret set, anyone may connect)"));
	}

	/** The port actually in use, which matters when the config asked for port 0. */
	public int port() {
		return serverSocket == null ? config.port() : serverSocket.getLocalPort();
	}

	public SessionRegistry sessions() {
		return sessions;
	}

	public int connectionCount() {
		return connections.size();
	}

	/** Blocks until {@link #close()} is called. Used by {@code main} to park the main thread. */
	public void awaitShutdown() throws InterruptedException {
		stopped.await();
	}

	@Override
	public void close() {
		if (!running) {
			return;
		}

		running = false;
		Log.info("Shutting down");

		try {
			serverSocket.close();
		} catch (IOException ignored) {
			// Already closed; the accept loop will notice either way.
		}

		for (ClientConnection connection : connections) {
			connection.disconnect("server shutting down");
		}

		threads.shutdown();

		try {
			if (!threads.awaitTermination(2, TimeUnit.SECONDS)) {
				// Writers get a moment to flush, then we stop waiting for them.
				threads.shutdownNow();
			}
		} catch (InterruptedException interrupted) {
			threads.shutdownNow();
			Thread.currentThread().interrupt();
		}

		connections.forEach(ClientConnection::closeSocket);
		connections.clear();
		stopped.countDown();
	}

	private void acceptLoop() {
		while (running) {
			try {
				Socket socket = serverSocket.accept();
				accept(socket);
			} catch (IOException closed) {
				if (running) {
					Log.error("Accept failed", closed);
				}

				// Otherwise this is just close() pulling the socket out from under us.
			}
		}
	}

	private void accept(Socket socket) {
		ClientConnection connection;

		try {
			connection = new ClientConnection(socket);
		} catch (IOException failed) {
			Log.warn("Could not set up " + socket.getRemoteSocketAddress() + ": " + failed.getMessage());
			return;
		}

		if (connections.size() >= MAX_CONNECTIONS) {
			Log.warn("Refusing " + connection + ", already at " + MAX_CONNECTIONS + " connections");
			connection.closeSocket();
			return;
		}

		connections.add(connection);
		Log.info("Connected " + connection + ", " + connections.size() + " open");
		threads.execute(connection::runWriteLoop);
		threads.execute(() -> readLoop(connection));
	}

	private void readLoop(ClientConnection connection) {
		try {
			while (!connection.isClosing()) {
				Message message = connection.readMessage();
				router.handle(connection, message);
			}
		} catch (ProtocolException malformed) {
			// The client is speaking nonsense, so stop listening to it.
			Log.warn("Protocol error from " + connection + ": " + malformed.getMessage());
			connection.reject(malformed.getMessage());
		} catch (EOFException | SocketException hungUp) {
			connection.disconnect("connection closed");
		} catch (IOException failed) {
			Log.warn("Read failed for " + connection + ": " + failed.getMessage());
			connection.disconnect("read failed");
		} catch (RuntimeException unexpected) {
			// A bug in our own handling must not take the server down with it.
			Log.error("Unexpected failure handling " + connection, unexpected);
			connection.reject("Internal server error");
		} finally {
			ClientSession session = connection.session();

			if (session != null) {
				sessions.logout(session);
			}

			connections.remove(connection);
			Log.info("Closed " + connection + ", " + connections.size() + " open, " + sessions.size() + " signed in");
		}
	}
}
