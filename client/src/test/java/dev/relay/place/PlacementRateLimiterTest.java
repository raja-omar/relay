package dev.relay.place;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlacementRateLimiterTest {
	@BeforeEach
	void reset() {
		PlacementRateLimiter.resetForTests();
	}

	@Test
	void firstClickIsAllowedThenNeedsAGap() {
		Object world = new Object();
		assertTrue(PlacementRateLimiter.tryAcquireAt(1_000L, world));
		assertFalse(PlacementRateLimiter.tryAcquireAt(1_001L, world));
		assertTrue(PlacementRateLimiter.tryAcquireAt(1_000L + 83_333_334L, world));
	}

	@Test
	void aMutationDefersTheNextPlace() {
		Object world = new Object();
		assertTrue(PlacementRateLimiter.tryAcquireAt(0L, world));
		PlacementRateLimiter.deferAfterMutationAt(10L, world);
		assertFalse(PlacementRateLimiter.isReadyAfterMutationAt(20L, world));
		assertTrue(PlacementRateLimiter.isReadyAfterMutationAt(10L + 50_000_000L, world));
	}
}
