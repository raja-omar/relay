package dev.relay.common;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PlayerNamesTest {
	@ParameterizedTest
	@ValueSource(strings = {"Alice", "a", "Player_01", "ABCDEFGHIJKLMNOP"})
	void acceptsMinecraftNames(String name) {
		assertTrue(PlayerNames.isValid(name), name + " should be accepted");
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"",
			"ABCDEFGHIJKLMNOPQ",
			"has space",
			"§cAlice",
			"Alice\nAlice",
			"Alice;drop",
			"Alíce",
			"../../etc/passwd",
	})
	void rejectsAnythingElse(String name) {
		assertFalse(PlayerNames.isValid(name), name + " should be rejected");
	}

	@Test
	void rejectsNull() {
		assertFalse(PlayerNames.isValid(null));
	}
}
