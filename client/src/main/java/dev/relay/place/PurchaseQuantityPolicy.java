package dev.relay.place;

/**
 * How many items {@code /shop} should buy for a schematic block.
 *
 * <p>Doors and trapdoors are sold one at a time. Everything else tops up to a full stack once the
 * player is at or below 28.
 */
final class PurchaseQuantityPolicy {
	static final int TOP_UP_AT_OR_BELOW = 28;

	private PurchaseQuantityPolicy() {
	}

	static int quantityToBuy(int currentCount, int maxStackSize, boolean oneAtATime) {
		int current = Math.max(0, currentCount);
		int maximum = Math.max(1, maxStackSize);

		if (oneAtATime) {
			return current == 0 ? 1 : 0;
		}

		if (current >= maximum) {
			return 0;
		}

		return current > 0 && current > Math.min(TOP_UP_AT_OR_BELOW, maximum - 1)
				? 0
				: maximum - current;
	}
}
