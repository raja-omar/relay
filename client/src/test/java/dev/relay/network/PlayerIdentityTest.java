package dev.relay.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * The fallback identity for clients without a real account, which is every development client and
 * every offline-mode server.
 */
class PlayerIdentityTest {
	@Test
	void givesDifferentPlayersDifferentIds() {
		assertNotEquals(PlayerIdentity.offlineId("Alice"), PlayerIdentity.offlineId("Beta"));
	}

	@Test
	void givesTheSamePlayerTheSameIdEveryTime() {
		assertEquals(PlayerIdentity.offlineId("Alice"), PlayerIdentity.offlineId("Alice"));
	}

	@Test
	void matchesHowMinecraftItselfDerivesOfflineIds() {
		// A version 3 UUID of "OfflinePlayer:<name>", the same scheme vanilla offline mode uses, so a
		// player keeps one identity whichever way they connect. Worked out independently of the code.
		assertEquals(UUID.fromString("10920508-d5d8-3eed-93d2-92f193afe7d7"), PlayerIdentity.offlineId("Alice"));
	}
}
