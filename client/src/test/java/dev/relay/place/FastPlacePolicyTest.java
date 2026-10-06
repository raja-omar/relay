package dev.relay.place;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FastPlacePolicyTest {
	@Test
	void delayStaysInsideTheVanillaRange() {
		assertEquals(0, FastPlacePolicy.clampDelay(-2));
		assertEquals(0, FastPlacePolicy.clampDelay(0));
		assertEquals(4, FastPlacePolicy.clampDelay(4));
		assertEquals(4, FastPlacePolicy.clampDelay(99));
	}

	@Test
	void fasterCutsATickUntilEveryTick() {
		assertEquals(0, FastPlacePolicy.faster(0));
		assertEquals(0, FastPlacePolicy.faster(1));
		assertEquals(3, FastPlacePolicy.faster(4));
		assertFalse(FastPlacePolicy.canGoFaster(0));
		assertTrue(FastPlacePolicy.canGoFaster(1));
	}

	@Test
	void slowerAddsATickUntilVanilla() {
		assertEquals(4, FastPlacePolicy.slower(4));
		assertEquals(4, FastPlacePolicy.slower(3));
		assertEquals(1, FastPlacePolicy.slower(0));
		assertFalse(FastPlacePolicy.canGoSlower(4));
		assertTrue(FastPlacePolicy.canGoSlower(3));
	}

	@Test
	void labelsNameTheFeelRatherThanTheTickCount() {
		assertEquals("Max", FastPlacePolicy.speedLabel(0));
		assertEquals("Fast", FastPlacePolicy.speedLabel(1));
		assertEquals("Medium", FastPlacePolicy.speedLabel(2));
		assertEquals("Slow", FastPlacePolicy.speedLabel(3));
		assertEquals("Vanilla", FastPlacePolicy.speedLabel(4));
		assertEquals("Max", FastPlacePolicy.speedLabel(-1));
	}
}
