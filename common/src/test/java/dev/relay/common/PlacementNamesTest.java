package dev.relay.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PlacementNamesTest {
	@ParameterizedTest
	@ValueSource(strings = { "Wall", "Fortress Wall 03", "a", "house.v2" })
	void acceptsOrdinaryNames(String name) {
		assertTrue(PlacementNames.isValid(name));
	}

	@Test
	void rejectsEmptyNullAndControls() {
		assertFalse(PlacementNames.isValid(null));
		assertFalse(PlacementNames.isValid(""));
		assertFalse(PlacementNames.isValid("Wall\nTwo"));
		assertFalse(PlacementNames.isValid("x".repeat(PlacementNames.MAX_LENGTH + 1)));
	}

	@Test
	void sanitiseDropsLineBreaksAndTrims() {
		assertEquals("WallTwo", PlacementNames.sanitise("  Wall\nTwo  "));
		assertEquals("", PlacementNames.sanitise("\n\t"));
	}
}
