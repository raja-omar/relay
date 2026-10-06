package dev.relay.combat;

import java.util.Random;

/**
 * Which hotbar slot Autpot throws, and how many ticks to wait between selecting it, throwing, and
 * going back. The throw and the return each wait at least one tick so the carried-slot packet is
 * already on its way before the next action.
 */
public final class AutopotPolicy {
	public static final int HOTBAR_SIZE = 9;
	public static final int INVENTORY_START = 9;
	public static final int INVENTORY_END = 35;
	public static final int MIN_REFILL_DELAY_TICKS = 2;
	public static final int MAX_REFILL_DELAY_TICKS = 5;
	public static final int MIN_REFILL_GAP_TICKS = 2;
	public static final int MAX_REFILL_GAP_TICKS = 4;

	private AutopotPolicy() {
	}

	public record Steps(int switchTicks, int throwTicks, int restoreTicks) {
	}

	/** How many stacks to move, and how long to wait before the first and between the two. */
	public record RefillPlan(int count, int delayTicks, int gapTicks) {
	}

	/** Leftmost Instant Health II splash in the hotbar, or -1. */
	public static int firstSlot(boolean[] instantHealthTwo) {
		if (instantHealthTwo == null) {
			return -1;
		}

		int limit = Math.min(HOTBAR_SIZE, instantHealthTwo.length);
		for (int slot = 0; slot < limit; slot++) {
			if (instantHealthTwo[slot]) {
				return slot;
			}
		}
		return -1;
	}

	public static Steps roll(Random random) {
		return new Steps(random.nextInt(2), 1 + random.nextInt(2), 1 + random.nextInt(2));
	}

	/** A restock is due once two splashes have been thrown. */
	public static boolean refillDue(int throwsSinceRefill) {
		return throwsSinceRefill >= 2;
	}

	public static RefillPlan rollRefill(Random random) {
		return new RefillPlan(
				1 + random.nextInt(2),
				between(random, MIN_REFILL_DELAY_TICKS, MAX_REFILL_DELAY_TICKS),
				between(random, MIN_REFILL_GAP_TICKS, MAX_REFILL_GAP_TICKS));
	}

	/** Leftmost empty hotbar slot, or -1. True means the slot is empty. */
	public static int firstEmptyHotbar(boolean[] empty) {
		if (empty == null) {
			return -1;
		}

		int limit = Math.min(HOTBAR_SIZE, empty.length);
		for (int slot = 0; slot < limit; slot++) {
			if (empty[slot]) {
				return slot;
			}
		}
		return -1;
	}

	/** Leftmost Instant Health II splash in the backpack (slots 9-35), or -1. */
	public static int firstInventoryPotion(boolean[] instantHealthTwo) {
		if (instantHealthTwo == null) {
			return -1;
		}

		int limit = Math.min(INVENTORY_END + 1, instantHealthTwo.length);
		for (int slot = INVENTORY_START; slot < limit; slot++) {
			if (instantHealthTwo[slot]) {
				return slot;
			}
		}
		return -1;
	}

	private static int between(Random random, int min, int max) {
		return min + random.nextInt(max - min + 1);
	}
}
