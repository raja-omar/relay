package dev.relay.schematic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class PendingSharesTest {
	private static final UUID FIRST = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID SECOND = UUID.fromString("22222222-2222-2222-2222-222222222222");
	private static final UUID THIRD = UUID.fromString("33333333-3333-3333-3333-333333333333");

	@Test
	void remembersAShareUntilItIsTaken() {
		PendingShares pending = new PendingShares(8);
		byte[] data = { 1, 2, 3 };

		assertTrue(pending.put(new PendingShares.Share(FIRST, "Alice", "Wall", data)));
		assertEquals(1, pending.size());

		PendingShares.Share share = pending.take(FIRST).orElseThrow();

		assertEquals("Alice", share.senderName());
		assertEquals("Wall", share.name());
		assertArrayEquals(data, share.data());
		assertTrue(pending.take(FIRST).isEmpty());
	}

	@Test
	void refusesTheSameIdTwice() {
		PendingShares pending = new PendingShares(8);

		assertTrue(pending.put(new PendingShares.Share(FIRST, "Alice", "Wall", new byte[] { 1 })));
		assertFalse(pending.put(new PendingShares.Share(FIRST, "Alice", "Wall", new byte[] { 2 })));
		assertEquals(1, pending.size());
	}

	@Test
	void listsWithoutTaking() {
		PendingShares pending = new PendingShares(8);

		assertTrue(pending.put(new PendingShares.Share(FIRST, "Alice", "Wall", new byte[] { 1 })));
		assertEquals(1, pending.list().size());
		assertEquals("Wall", pending.list().get(0).name());
		assertEquals(1, pending.size());
		assertTrue(pending.take(FIRST).isPresent());
	}

	@Test
	void forgetsTheOldestWhenFull() {
		PendingShares pending = new PendingShares(2);

		assertTrue(pending.put(new PendingShares.Share(FIRST, "Alice", "One", new byte[] { 1 })));
		assertTrue(pending.put(new PendingShares.Share(SECOND, "Alice", "Two", new byte[] { 2 })));
		assertTrue(pending.put(new PendingShares.Share(THIRD, "Alice", "Three", new byte[] { 3 })));

		assertEquals(2, pending.size());
		assertTrue(pending.take(FIRST).isEmpty());
		assertTrue(pending.take(SECOND).isPresent());
		assertTrue(pending.take(THIRD).isPresent());
	}
}
