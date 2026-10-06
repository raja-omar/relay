package dev.relay.place;

/**
 * Vanilla waits four ticks between held right-clicks. Fast Place caps that delay. Zero is every
 * tick; four is unchanged vanilla.
 */
public final class FastPlacePolicy {
	public static final int MIN_DELAY_TICKS = 0;
	public static final int MAX_DELAY_TICKS = 4;
	public static final int DEFAULT_DELAY_TICKS = 0;

	private FastPlacePolicy() {
	}

	public static int clampDelay(int delayTicks) {
		return Math.max(MIN_DELAY_TICKS, Math.min(MAX_DELAY_TICKS, delayTicks));
	}

	public static int faster(int delayTicks) {
		return clampDelay(delayTicks - 1);
	}

	public static int slower(int delayTicks) {
		return clampDelay(delayTicks + 1);
	}

	public static boolean canGoFaster(int delayTicks) {
		return clampDelay(delayTicks) > MIN_DELAY_TICKS;
	}

	public static boolean canGoSlower(int delayTicks) {
		return clampDelay(delayTicks) < MAX_DELAY_TICKS;
	}

	public static String speedLabel(int delayTicks) {
		return switch (clampDelay(delayTicks)) {
			case 0 -> "Max";
			case 1 -> "Fast";
			case 2 -> "Medium";
			case 3 -> "Slow";
			default -> "Vanilla";
		};
	}

	public static String speedHint() {
		return "How short the gap is between held right-clicks. Max is one block per tick.";
	}
}
