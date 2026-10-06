package dev.relay.server;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import dev.relay.common.Tls;
import dev.relay.common.protocol.Message;
import dev.relay.common.protocol.ProtocolException;
import dev.relay.server.group.GroupHandler;
import dev.relay.server.group.GroupManager;
import dev.relay.server.ping.PingHandler;
import dev.relay.server.player.PlayerDirectory;
import dev.relay.server.share.ShareHandler;

/**
 * The server: accept connections, read messages from them, hand each one to the router.
 *
 * <p>Two threads per connection, one reading and one writing. That is more threads than a selector
 * loop would use and far easier to follow, and this is sized for a faction, not a datacentre.
 */
public final class RelayServer implements Closeable {
	private final ServerConfig config;
	private final PlayerDirectory players;
	private final SessionRegistry sessions = new SessionRegistry();
	private final GroupManager groups = new GroupManager();
	private final AbuseLimits abuse = new AbuseLimits();
	private final MessageRouter router;
	private final Set<ClientConnection> connections = ConcurrentHashMap.newKeySet();
	private final CountDownLatch stopped = new CountDownLatch(1);
	private final ExecutorService threads = Executors.newCachedThreadPool(
			Thread.ofPlatform().name("relay-", 1).daemon(true).factory());

	private volatile ServerSocket serverSocket;
	private volatile boolean running;
	private volatile Tls tls;

	public RelayServer(ServerConfig config) throws IOException {
		this(config, PlayerDirectory.load(config.playersFile()));
	}

	/** Tests pass an in-memory directory so they can issue ids without touching the disk. */
	public RelayServer(ServerConfig config, PlayerDirectory players) {
		this.config = config;
		this.players = players;
		this.router = new MessageRouter(players, sessions, new GroupHandler(groups, sessions),
				new ShareHandler(groups, sessions, abuse), new PingHandler(groups, sessions, abuse), abuse);
	}

	/** Binds the port before returning, so a port clash surfaces here rather than in a thread. */
	public void start() throws IOException {
		tls = Tls.loadOrCreate(config.tlsFile());
		serverSocket = tls.bind(config.port());
		running = true;
		threads.execute(this::acceptLoop);

		Log.info("Listening on port " + port() + " (" + players.size() + " player id"
				+ (players.size() == 1 ? "" : "s") + ")");
		Log.info("TLS pin " + tls.pin() + " — pack as tlsPin= in the client jar");

		if (players.size() == 0) {
			Log.info("No player ids yet. Issue one with: relay-server invite <name>");
		}
	}

	public String tlsPin() {
		return tls == null ? "" : tls.pin();
	}

	/** The port actually in use, which matters when the config asked for port 0. */
	public int port() {
		return serverSocket == null ? config.port() : serverSocket.getLocalPort();
	}

	public SessionRegistry sessions() {
		return sessions;
	}

	public GroupManager groups() {
		return groups;
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
		if (!abuse.tryConnect(socket.getInetAddress())) {
			Log.warn("Refusing " + socket.getRemoteSocketAddress() + ", connection limit");
			closeQuietly(socket);
			return;
		}

		ClientConnection connection;

		try {
			connection = new ClientConnection(socket);
		} catch (IOException failed) {
			abuse.disconnected(socket.getInetAddress());
			Log.warn("Could not set up " + socket.getRemoteSocketAddress() + ": " + failed.getMessage());
			closeQuietly(socket);
			return;
		}

		connections.add(connection);
		Log.info("Connected " + connection + ", " + connections.size() + " open");
		threads.execute(connection::runWriteLoop);
		threads.execute(() -> readLoop(connection));
	}

	private static void closeQuietly(Socket socket) {
		try {
			socket.close();
		} catch (IOException ignored) {
			// The accept loop already decided this socket is unused.
		}
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
		} catch (EOFException | SocketException | SocketTimeoutException hungUp) {
			connection.disconnect(hungUp instanceof SocketTimeoutException
					? "signed in too slowly"
					: "connection closed");
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
				router.onDisconnected(session);
			}

			connections.remove(connection);
			abuse.disconnected(connection.remoteAddress());
			Log.info("Closed " + connection + ", " + connections.size() + " open, " + sessions.size() + " signed in");
		}
	}
}
