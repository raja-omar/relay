package dev.relay.gui;

/**
 * Frame-rate-independent hover fade. owo treats {@code delta = 1} as 50ms (one client tick).
 */
final class HoverMotion {
	static final int HOVER_MS = 90;

	private HoverMotion() {
	}

	static float approach(float amount, float target, float delta, int durationMs) {
		if (durationMs <= 0) {
			return target;
		}

		float step = (delta * 50.0F) / durationMs;
		if (amount < target) {
			return Math.min(target, amount + step);
		}

		if (amount > target) {
			return Math.max(target, amount - step);
		}

		return amount;
	}

	static float seed(boolean over) {
		return over ? 1.0F : 0.0F;
	}
}
