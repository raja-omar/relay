package dev.relay.group;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class PendingInvitesTest {
	@Test
	void remembersAnInviteUntilItIsTaken() {
		PendingInvites pending = new PendingInvites();
		Instant now = Instant.parse("2026-09-18T12:00:00Z");

		pending.put("Alpha", "Alice", now);

		assertEquals(1, pending.size(now));
		assertEquals("Alice", pending.list(now).get(0).inviterName());
		assertEquals("Alpha", pending.take("Alpha", now).orElseThrow().groupName());
		assertTrue(pending.take("Alpha", now).isEmpty());
	}

	@Test
	void aSecondInviteToTheSameGroupReplacesTheFirst() {
		PendingInvites pending = new PendingInvites();
		Instant now = Instant.parse("2026-09-18T12:00:00Z");

		pending.put("Alpha", "Alice", now);
		pending.put("Alpha", "Beta", now.plusSeconds(10));

		assertEquals(1, pending.size(now.plusSeconds(10)));
		assertEquals("Beta", pending.list(now.plusSeconds(10)).get(0).inviterName());
	}

	@Test
	void dropsAnInviteOnceTheServerWouldHaveForgottenIt() {
		PendingInvites pending = new PendingInvites();
		Instant received = Instant.parse("2026-09-18T12:00:00Z");

		pending.put("Alpha", "Alice", received);

		assertEquals(1, pending.size(received.plus(PendingInvites.LIFETIME)));
		assertEquals(0, pending.size(received.plus(PendingInvites.LIFETIME).plusMillis(1)));
	}

	@Test
	void forgetsTheOldestWhenFull() {
		PendingInvites pending = new PendingInvites();
		Instant now = Instant.parse("2026-09-18T12:00:00Z");

		for (int i = 0; i < 17; i++) {
			pending.put("Group" + i, "Alice", now.plusSeconds(i));
		}

		assertEquals(16, pending.size(now.plusSeconds(16)));
		assertTrue(pending.take("Group0", now.plusSeconds(16)).isEmpty());
		assertTrue(pending.take("Group16", now.plusSeconds(16)).isPresent());
	}
}
