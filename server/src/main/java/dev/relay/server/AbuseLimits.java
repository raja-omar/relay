package dev.relay.server;

import java.net.InetAddress;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Caps that keep a public schematic server from being an open tap on CPU, memory, or egress.
 *
 * <p>One issued id is still one signed-in session. These limits are about the sockets and shares
 * in front of that: how many connections one IP may hold, how often a bad id may be tried, and
 * how hard one player may push placements through the process.
 */
public final class AbuseLimits {
	public static final int MAX_CONNECTIONS = 200;
	public static final int MAX_CONNECTIONS_PER_IP = 32;
	public static final int AUTH_FAILURES_PER_MINUTE = 20;
	public static final int SHARE_BURST = 4;
	public static final int SHARES_PER_MINUTE = 12;
	public static final int PING_BURST = 10;
	public static final int PINGS_PER_MINUTE = 60;
	public static final long SHARE_BYTES_PER_MINUTE = 48L * 1024 * 1024;
	public static final int PRE_AUTH_TIMEOUT_MILLIS = 10_000;

	private final AtomicInteger connections = new AtomicInteger();
	private final ConcurrentHashMap<String, AtomicInteger> connectionsByIp = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Window> authFailures = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<UUID, Bucket> shareCount = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<UUID, Bucket> shareBytes = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<UUID, Bucket> pingCount = new ConcurrentHashMap<>();

	public int connectionCount() {
		return connections.get();
	}

	/** Reserves a slot. False means the socket should be closed without starting threads. */
	public boolean tryConnect(InetAddress address) {
		String ip = key(address);

		if (connections.get() >= MAX_CONNECTIONS) {
			return false;
		}

		AtomicInteger perIp = connectionsByIp.computeIfAbsent(ip, ignored -> new AtomicInteger());

		while (true) {
			int current = perIp.get();

			if (current >= MAX_CONNECTIONS_PER_IP) {
				return false;
			}

			if (perIp.compareAndSet(current, current + 1)) {
				break;
			}
		}

		int global = connections.incrementAndGet();

		if (global > MAX_CONNECTIONS) {
			connections.decrementAndGet();
			releaseIp(ip);
			return false;
		}

		return true;
	}

	public void disconnected(InetAddress address) {
		connections.decrementAndGet();
		releaseIp(key(address));
	}

	public boolean allowAuthAttempt(InetAddress address) {
		return authFailures.computeIfAbsent(key(address), ignored -> new Window(AUTH_FAILURES_PER_MINUTE))
				.allow();
	}

	public void authFailed(InetAddress address) {
		authFailures.computeIfAbsent(key(address), ignored -> new Window(AUTH_FAILURES_PER_MINUTE))
				.record();
	}

	public boolean allowPing(UUID playerId) {
		Bucket counts = pingCount.computeIfAbsent(playerId,
				ignored -> new Bucket(PING_BURST, PINGS_PER_MINUTE));

		synchronized (this) {
			if (!counts.has(1)) {
				return false;
			}

			counts.take(1);
			return true;
		}
	}

	public boolean allowShare(UUID playerId, int bytes) {
		if (bytes < 0) {
			return false;
		}

		Bucket counts = shareCount.computeIfAbsent(playerId,
				ignored -> new Bucket(SHARE_BURST, SHARES_PER_MINUTE));
		Bucket volume = shareBytes.computeIfAbsent(playerId,
				ignored -> new Bucket(SHARE_BYTES_PER_MINUTE, SHARE_BYTES_PER_MINUTE));

		synchronized (this) {
			if (!counts.has(1) || !volume.has(bytes)) {
				return false;
			}

			counts.take(1);
			volume.take(bytes);
			return true;
		}
	}

	private void releaseIp(String ip) {
		AtomicInteger perIp = connectionsByIp.get(ip);

		if (perIp == null) {
			return;
		}

		if (perIp.decrementAndGet() <= 0) {
			connectionsByIp.remove(ip, perIp);
		}
	}

	private static String key(InetAddress address) {
		return address == null ? "unknown" : address.getHostAddress();
	}

	/** Sliding one-minute window. */
	static final class Window {
		private final int limit;
		private final long[] stamps;
		private int next;

		Window(int limit) {
			this.limit = limit;
			this.stamps = new long[limit];
		}

		synchronized boolean allow() {
			prune(System.nanoTime());
			return next < limit;
		}

		synchronized void record() {
			long now = System.nanoTime();
			prune(now);

			if (next < stamps.length) {
				stamps[next++] = now;
			}
		}

		private void prune(long now) {
			long horizon = now - 60_000_000_000L;
			int kept = 0;

			for (int i = 0; i < next; i++) {
				if (stamps[i] > horizon) {
					stamps[kept++] = stamps[i];
				}
			}

			next = kept;
		}
	}

	/** Refills continuously up to {@code capacity}, at {@code perMinute} units per minute. */
	static final class Bucket {
		private final double capacity;
		private final double perNano;
		private double tokens;
		private long lastNanos = System.nanoTime();

		Bucket(double capacity, double perMinute) {
			this.capacity = capacity;
			this.perNano = perMinute / 60_000_000_000L;
			this.tokens = capacity;
		}

		boolean has(double amount) {
			refill();
			return tokens >= amount;
		}

		void take(double amount) {
			refill();
			tokens -= amount;
		}

		private void refill() {
			long now = System.nanoTime();
			tokens = Math.min(capacity, tokens + (now - lastNanos) * perNano);
			lastNanos = now;
		}
	}
}
