package dev.relay.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RelayThemeTest {
	@Test
	void blendStaysInsideEachChannel() {
		assertEquals(0xFF808080, RelayTheme.blend(0xFF000000, 0xFFFFFFFF, 0.5F));
		assertEquals(0x00000000, RelayTheme.blend(0x00000000, 0xFFFFFFFF, 0.0F));
	}

	@Test
	void windowCornersUseTheOuterQuarters() {
		assertEquals(0.0, RelayTheme.cornerFrom(true, true));
		assertEquals(90.0, RelayTheme.cornerTo(true, true));
		assertEquals(90.0, RelayTheme.cornerFrom(false, true));
		assertEquals(180.0, RelayTheme.cornerFrom(false, false));
		assertEquals(270.0, RelayTheme.cornerFrom(true, false));
		assertEquals(360.0, RelayTheme.cornerTo(true, false));
	}

	@Test
	void roundCornersNeverExceedHalfTheShortSide() {
		assertEquals(0, RelayTheme.clampRadius(18, 18, 0));
		assertEquals(4, RelayTheme.clampRadius(18, 18, 4));
		assertEquals(9, RelayTheme.clampRadius(18, 18, 20));
		assertEquals(0, RelayTheme.clampRadius(0, 10, 4));
	}
}
