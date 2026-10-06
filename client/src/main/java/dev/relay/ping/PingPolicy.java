package dev.relay.ping;

/**
 * Ranges for the ping timer. Colors come from the shared palette, including RGB.
 */
public final class PingPolicy {
	public static final int MIN_TIMEOUT_SECONDS = 1;
	public static final int MAX_TIMEOUT_SECONDS = 60;
	public static final int DEFAULT_TIMEOUT_SECONDS = 6;

	private PingPolicy() {
	}

	public static int clampTimeout(int seconds) {
		return Math.max(MIN_TIMEOUT_SECONDS, Math.min(MAX_TIMEOUT_SECONDS, seconds));
	}

	public static boolean canLowerTimeout(int seconds) {
		return clampTimeout(seconds) > MIN_TIMEOUT_SECONDS;
	}

	public static boolean canRaiseTimeout(int seconds) {
		return clampTimeout(seconds) < MAX_TIMEOUT_SECONDS;
	}

	public static String timeoutLabel(int seconds) {
		return clampTimeout(seconds) + "s";
	}

	public static String timeoutHint() {
		return "How long a ping stays before it disappears.";
	}
}
