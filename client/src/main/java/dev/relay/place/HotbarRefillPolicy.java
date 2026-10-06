package dev.relay.place;

import java.util.function.IntPredicate;

final class HotbarRefillPolicy {
	static final int FIRST_BUILD_HOTBAR_SLOT = 3;
	static final int LAST_BUILD_HOTBAR_SLOT = 8;
	static final int FIRST_MAIN_INVENTORY_SLOT = 9;
	static final int LAST_MAIN_INVENTORY_SLOT = 35;

	private HotbarRefillPolicy() {
	}

	static boolean shouldCapture(boolean mainHand, int selectedSlot, boolean blockItem, boolean potion, int beforeCount) {
		return mainHand && selectedSlot >= 3 && selectedSlot <= 8 && blockItem && !potion && beforeCount > 0;
	}

	static boolean shouldArm(boolean captured, boolean accepted, boolean sameInvocation, int beforeCount, int afterCount) {
		return captured && accepted && sameInvocation && beforeCount > afterCount && afterCount == 0;
	}

	static boolean pendingValid(
			boolean enabled,
			boolean samePlayer,
			boolean sameWorld,
			int pendingSlot,
			int selectedSlot,
			boolean screenOpen,
			boolean handlerReady,
			boolean targetEmpty,
			long ageMillis,
			long timeoutMillis) {
		return enabled
				&& samePlayer
				&& sameWorld
				&& pendingSlot >= 3
				&& pendingSlot <= 8
				&& selectedSlot == pendingSlot
				&& !screenOpen
				&& handlerReady
				&& targetEmpty
				&& ageMillis >= 0L
				&& ageMillis <= timeoutMillis;
	}

	static int nextMatchingHotbarSource(int selectedSlot, IntPredicate matches) {
		int buildSlotCount = 6;
		for (int offset = 1; offset < buildSlotCount; offset++) {
			int slot = 3 + Math.floorMod(selectedSlot - 3 + offset, buildSlotCount);
			if (matches.test(slot)) {
				return slot;
			}
		}

		return -1;
	}

	static int firstMatchingMainInventorySource(IntPredicate matches) {
		for (int slot = 9; slot <= 35; slot++) {
			if (matches.test(slot)) {
				return slot;
			}
		}

		return -1;
	}
}
