package dev.relay.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class AbuseLimitsTest {
	@Test
	void capsConnectionsPerAddress() throws Exception {
		AbuseLimits abuse = new AbuseLimits();
		InetAddress address = InetAddress.getByName("10.0.0.8");

		for (int i = 0; i < AbuseLimits.MAX_CONNECTIONS_PER_IP; i++) {
			assertTrue(abuse.tryConnect(address));
		}

		assertFalse(abuse.tryConnect(address));
		abuse.disconnected(address);
		assertTrue(abuse.tryConnect(address));
	}

	@Test
	void letsAPlayerPingABurstThenStopsThem() {
		AbuseLimits abuse = new AbuseLimits();
		UUID player = UUID.randomUUID();

		for (int i = 0; i < AbuseLimits.PING_BURST; i++) {
			assertTrue(abuse.allowPing(player));
		}

		assertFalse(abuse.allowPing(player));
		assertTrue(abuse.allowPing(UUID.randomUUID()));
	}

	@Test
	void letsAPlayerShareABurstThenStopsThem() {
		AbuseLimits abuse = new AbuseLimits();
		UUID player = UUID.randomUUID();

		for (int i = 0; i < AbuseLimits.SHARE_BURST; i++) {
			assertTrue(abuse.allowShare(player, 1024));
		}

		assertFalse(abuse.allowShare(player, 1024));
		assertTrue(abuse.allowShare(UUID.randomUUID(), 1024));
	}

	@Test
	void refusesAShareThatWouldBlowTheByteBudget() {
		AbuseLimits abuse = new AbuseLimits();
		UUID player = UUID.randomUUID();

		assertFalse(abuse.allowShare(player, (int) AbuseLimits.SHARE_BYTES_PER_MINUTE + 1));
		assertTrue(abuse.allowShare(player, 16 * 1024 * 1024));
	}
}
