package dev.relay.common;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GroupNamesTest {
	@ParameterizedTest
	@ValueSource(strings = {"Alpha", "abc", "Cannon-Crew", "faction_01", "AAAAAAAAAAAAAAAAAAAAAAAA"})
	void acceptsReasonableNames(String name) {
		assertTrue(GroupNames.isValid(name), name + " should be a valid group name");
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"",
			"ab",
			"AAAAAAAAAAAAAAAAAAAAAAAAA",
			"has space",
			"emoji\uD83D\uDE00",
			"semi;colon",
			"../escape",
			"quote\"",
			"new\nline",
	})
	void rejectsUnreasonableNames(String name) {
		assertFalse(GroupNames.isValid(name), name + " should be rejected");
	}

	@Test
	void rejectsNull() {
		assertFalse(GroupNames.isValid(null));
	}

	@Test
	void lengthBoundsAreInclusive() {
		assertTrue(GroupNames.isValid("a".repeat(GroupNames.MIN_LENGTH)));
		assertTrue(GroupNames.isValid("a".repeat(GroupNames.MAX_LENGTH)));
		assertFalse(GroupNames.isValid("a".repeat(GroupNames.MIN_LENGTH - 1)));
		assertFalse(GroupNames.isValid("a".repeat(GroupNames.MAX_LENGTH + 1)));
	}
}
