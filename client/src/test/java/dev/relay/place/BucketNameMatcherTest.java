package dev.relay.place;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BucketNameMatcherTest {
	@Test
	void recognisesANamedInfiniteWaterGenerator() {
		assertTrue(BucketNameMatcher.matchesInfiniteWaterGenerator("Infinite Gen Bucket [Water]"));
		assertTrue(BucketNameMatcher.matchesInfiniteWaterGenerator("  infinite-gen-bucket water  "));
		assertFalse(BucketNameMatcher.matchesInfiniteWaterGenerator("Water Bucket"));
		assertFalse(BucketNameMatcher.matchesInfiniteWaterGenerator("Infinite Gen Bucket"));
	}
}
