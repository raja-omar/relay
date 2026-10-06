package dev.relay.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class RecentIdsTest {
	@Test
	void remembersAnIdOnce() {
		RecentIds seen = new RecentIds(8);
		UUID id = UUID.randomUUID();

		assertTrue(seen.add(id));
		assertFalse(seen.add(id));
		assertTrue(seen.contains(id));
	}

	@Test
	void forgetsTheOldestWhenFull() {
		RecentIds seen = new RecentIds(2);
		UUID first = UUID.randomUUID();
		UUID second = UUID.randomUUID();
		UUID third = UUID.randomUUID();

		assertTrue(seen.add(first));
		assertTrue(seen.add(second));
		assertTrue(seen.add(third));

		assertFalse(seen.contains(first));
		assertTrue(seen.contains(second));
		assertTrue(seen.contains(third));
		assertEquals(2, seen.size());
	}
}
