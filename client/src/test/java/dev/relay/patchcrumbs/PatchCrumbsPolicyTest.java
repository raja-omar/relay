package dev.relay.patchcrumbs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import dev.relay.patchcrumbs.PatchCrumbsPolicy.DirectionMode;
import dev.relay.patchcrumbs.PatchCrumbsPolicy.Palette;

class PatchCrumbsPolicyTest {
	@Test
	void timeoutStaysInsideConfiguredRange() {
		assertEquals(1, PatchCrumbsPolicy.clampTimeout(0));
		assertEquals(1, PatchCrumbsPolicy.clampTimeout(-4));
		assertEquals(12, PatchCrumbsPolicy.clampTimeout(12));
		assertEquals(60, PatchCrumbsPolicy.clampTimeout(60));
		assertEquals(60, PatchCrumbsPolicy.clampTimeout(99));
	}

	@Test
	void widthStaysInsideConfiguredRange() {
		assertEquals(1, PatchCrumbsPolicy.clampWidth(0));
		assertEquals(2, PatchCrumbsPolicy.clampWidth(2));
		assertEquals(10, PatchCrumbsPolicy.clampWidth(10));
		assertEquals(10, PatchCrumbsPolicy.clampWidth(40));
	}

	@Test
	void directionNamesParseKnownWording() {
		assertEquals(DirectionMode.NORTH_SOUTH, DirectionMode.fromName("NORTH/SOUTH", DirectionMode.BOTH));
		assertEquals(DirectionMode.EAST_WEST, DirectionMode.fromName("eastwest", DirectionMode.BOTH));
		assertEquals(DirectionMode.AUTO, DirectionMode.fromName("Auto", DirectionMode.BOTH));
		assertEquals(DirectionMode.BOTH, DirectionMode.fromName("nope", DirectionMode.BOTH));
	}

	@Test
	void paletteCyclesAndParses() {
		assertEquals(Palette.ORANGE, Palette.RED.next());
		assertEquals(Palette.RGB, Palette.RED.previous());
		assertEquals(Palette.RGB, Palette.WHITE.next());
		assertEquals(Palette.RED, Palette.RGB.next());
		assertEquals(Palette.CYAN, Palette.fromName("cyan", Palette.RED));
		assertEquals(Palette.RGB, Palette.fromName("rgb", Palette.RED));
		assertEquals(Palette.RGB, Palette.fromName("rainbow", Palette.RED));
		assertEquals(Palette.RED, Palette.fromName("nope", Palette.RED));
		assertEquals("Red", Palette.RED.label());
		assertEquals("RGB", Palette.RGB.label());
		assertEquals(0xFFFF0000, Palette.RED.argb());
		assertEquals(0xFFFFFFFF, Palette.WHITE.argb());
		assertEquals(0xFFFF0000, Palette.rgbCycle(0));
	}
}
