package dev.relay.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

import dev.relay.combat.AutopotPolicy.RefillPlan;
import dev.relay.combat.AutopotPolicy.Steps;

class AutopotPolicyTest {
	@Test
	void takesTheLeftmostInstantHealthTwo() {
		assertEquals(0, AutopotPolicy.firstSlot(new boolean[] {true, true, false}));
		assertEquals(3, AutopotPolicy.firstSlot(new boolean[] {false, false, false, true, false, true}));
		assertEquals(8, AutopotPolicy.firstSlot(new boolean[] {
				false, false, false, false, false, false, false, false, true}));
	}

	@Test
	void missesWhenTheHotbarHasNoMatch() {
		assertEquals(-1, AutopotPolicy.firstSlot(null));
		assertEquals(-1, AutopotPolicy.firstSlot(new boolean[0]));
		assertEquals(-1, AutopotPolicy.firstSlot(new boolean[] {false, false, false}));

		boolean[] pastTheHotbar = new boolean[10];
		pastTheHotbar[9] = true;
		assertEquals(-1, AutopotPolicy.firstSlot(pastTheHotbar));
	}

	@Test
	void gapsStayShortAndNeverThrowOnTheSwitchTick() {
		Random random = new Random(1);
		boolean sawImmediateSwitch = false;
		boolean sawDelayedSwitch = false;
		boolean sawShorterThrow = false;
		boolean sawLongerThrow = false;
		boolean sawShorterRestore = false;
		boolean sawLongerRestore = false;

		for (int i = 0; i < 64; i++) {
			Steps steps = AutopotPolicy.roll(random);
			assertTrue(steps.switchTicks() == 0 || steps.switchTicks() == 1);
			assertTrue(steps.throwTicks() == 1 || steps.throwTicks() == 2);
			assertTrue(steps.restoreTicks() == 1 || steps.restoreTicks() == 2);
			sawImmediateSwitch |= steps.switchTicks() == 0;
			sawDelayedSwitch |= steps.switchTicks() == 1;
			sawShorterThrow |= steps.throwTicks() == 1;
			sawLongerThrow |= steps.throwTicks() == 2;
			sawShorterRestore |= steps.restoreTicks() == 1;
			sawLongerRestore |= steps.restoreTicks() == 2;
		}

		assertTrue(sawImmediateSwitch && sawDelayedSwitch);
		assertTrue(sawShorterThrow && sawLongerThrow);
		assertTrue(sawShorterRestore && sawLongerRestore);
	}

	@Test
	void refillWaitsForTheSecondThrow() {
		assertFalse(AutopotPolicy.refillDue(0));
		assertFalse(AutopotPolicy.refillDue(1));
		assertTrue(AutopotPolicy.refillDue(2));
	}

	@Test
	void refillFillsEmptyHotbarSlotsFromTheBackpack() {
		assertEquals(3, AutopotPolicy.firstEmptyHotbar(new boolean[] {false, false, false, true, true}));
		assertEquals(-1, AutopotPolicy.firstEmptyHotbar(new boolean[] {false, false, false}));
		assertEquals(-1, AutopotPolicy.firstEmptyHotbar(null));

		boolean[] backpack = new boolean[36];
		backpack[0] = true;
		backpack[8] = true;
		assertEquals(-1, AutopotPolicy.firstInventoryPotion(backpack));
		backpack[12] = true;
		backpack[20] = true;
		assertEquals(12, AutopotPolicy.firstInventoryPotion(backpack));
		assertEquals(-1, AutopotPolicy.firstInventoryPotion(null));
	}

	@Test
	void refillMovesOneOrTwoStacksAfterAShortGap() {
		Random random = new Random(1);
		boolean sawOne = false;
		boolean sawTwo = false;
		boolean sawShortDelay = false;
		boolean sawLongDelay = false;

		for (int i = 0; i < 64; i++) {
			RefillPlan plan = AutopotPolicy.rollRefill(random);
			assertTrue(plan.count() == 1 || plan.count() == 2);
			assertTrue(plan.delayTicks() >= AutopotPolicy.MIN_REFILL_DELAY_TICKS
					&& plan.delayTicks() <= AutopotPolicy.MAX_REFILL_DELAY_TICKS);
			assertTrue(plan.gapTicks() >= AutopotPolicy.MIN_REFILL_GAP_TICKS
					&& plan.gapTicks() <= AutopotPolicy.MAX_REFILL_GAP_TICKS);
			sawOne |= plan.count() == 1;
			sawTwo |= plan.count() == 2;
			sawShortDelay |= plan.delayTicks() == AutopotPolicy.MIN_REFILL_DELAY_TICKS;
			sawLongDelay |= plan.delayTicks() == AutopotPolicy.MAX_REFILL_DELAY_TICKS;
		}

		assertTrue(sawOne && sawTwo);
		assertTrue(sawShortDelay && sawLongDelay);
	}
}
