package dev.relay.ping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PingPolicyTest {
	@Test
	void timeoutStaysInsideOneMinute() {
		assertEquals(1, PingPolicy.clampTimeout(0));
		assertEquals(6, PingPolicy.clampTimeout(6));
		assertEquals(60, PingPolicy.clampTimeout(90));
		assertEquals("6s", PingPolicy.timeoutLabel(6));
		assertFalse(PingPolicy.canLowerTimeout(1));
		assertTrue(PingPolicy.canRaiseTimeout(6));
		assertFalse(PingPolicy.canRaiseTimeout(60));
	}
}
