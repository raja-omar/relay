package dev.relay.fluids;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class ClearWaterTest {
	@Test
	void enabledFallsBackWhenTheClientHasNotStarted() {
		assertFalse(ClearWater.enabled());
	}
}
