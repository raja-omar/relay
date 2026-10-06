package dev.relay.place;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HotbarRefillPolicyTest {
	@Test
	void capturesOnlyBuildHotbarBlockItems() {
		assertTrue(HotbarRefillPolicy.shouldCapture(true, 5, true, false, 16));
		assertFalse(HotbarRefillPolicy.shouldCapture(false, 5, true, false, 16));
		assertFalse(HotbarRefillPolicy.shouldCapture(true, 2, true, false, 16));
		assertFalse(HotbarRefillPolicy.shouldCapture(true, 5, false, false, 16));
		assertFalse(HotbarRefillPolicy.shouldCapture(true, 5, true, true, 16));
		assertFalse(HotbarRefillPolicy.shouldCapture(true, 5, true, false, 0));
	}

	@Test
	void armsOnlyWhenTheLastItemWasUsed() {
		assertTrue(HotbarRefillPolicy.shouldArm(true, true, true, 1, 0));
		assertFalse(HotbarRefillPolicy.shouldArm(true, true, true, 8, 7));
		assertFalse(HotbarRefillPolicy.shouldArm(true, false, true, 1, 0));
	}

	@Test
	void walksOtherBuildSlotsThenTheBackpack() {
		assertEquals(6, HotbarRefillPolicy.nextMatchingHotbarSource(5, slot -> slot == 6));
		assertEquals(3, HotbarRefillPolicy.nextMatchingHotbarSource(8, slot -> slot == 3));
		assertEquals(-1, HotbarRefillPolicy.nextMatchingHotbarSource(5, slot -> false));
		assertEquals(12, HotbarRefillPolicy.firstMatchingMainInventorySource(slot -> slot == 12));
	}
}
