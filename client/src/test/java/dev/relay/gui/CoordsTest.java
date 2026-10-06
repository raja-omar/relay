package dev.relay.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CoordsTest {
	@Test
	void namesEachAxis() {
		assertEquals("X 12  Y 64  Z -8", Coords.format(12, 64, -8));
	}

	@Test
	void typingAllowsASignedIntegerDraft() {
		assertTrue(Coords.typing(""));
		assertTrue(Coords.typing("-"));
		assertTrue(Coords.typing("-12"));
		assertTrue(Coords.typing("30000000"));
		assertFalse(Coords.typing("1.5"));
		assertFalse(Coords.typing("12a"));
		assertFalse(Coords.typing("--1"));
	}

	@Test
	void parseReadsWholeNumbers() {
		assertEquals(-8, Coords.parse(" -8 ").orElseThrow());
		assertTrue(Coords.parse("").isEmpty());
		assertTrue(Coords.parse("-").isEmpty());
		assertTrue(Coords.parse("9999999999").isEmpty());
	}
}
