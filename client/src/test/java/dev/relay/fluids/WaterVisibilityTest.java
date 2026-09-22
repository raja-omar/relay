package dev.relay.fluids;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class WaterVisibilityTest {
	@Test
	void namesParseKnownWording() {
		assertEquals(WaterVisibility.NORMAL, WaterVisibility.fromName("vanilla", WaterVisibility.OFF));
		assertEquals(WaterVisibility.NORMAL, WaterVisibility.fromName("on", WaterVisibility.OFF));
		assertEquals(WaterVisibility.CLEAR, WaterVisibility.fromName("clear", WaterVisibility.NORMAL));
		assertEquals(WaterVisibility.OFF, WaterVisibility.fromName("hidden", WaterVisibility.NORMAL));
		assertEquals(WaterVisibility.OFF, WaterVisibility.fromName("transparent", WaterVisibility.NORMAL));
		assertEquals(WaterVisibility.NORMAL, WaterVisibility.fromName("nope", WaterVisibility.NORMAL));
	}

	@Test
	void cyclesVanillaClearHidden() {
		assertEquals(WaterVisibility.CLEAR, WaterVisibility.NORMAL.next());
		assertEquals(WaterVisibility.OFF, WaterVisibility.CLEAR.next());
		assertEquals(WaterVisibility.NORMAL, WaterVisibility.OFF.next());
		assertEquals("Vanilla", WaterVisibility.NORMAL.label());
		assertEquals("Clear", WaterVisibility.CLEAR.label());
		assertEquals("Hidden", WaterVisibility.OFF.label());
		assertEquals("vanilla", WaterVisibility.NORMAL.id());
		assertEquals("clear", WaterVisibility.CLEAR.id());
		assertEquals("hidden", WaterVisibility.OFF.id());
	}

	@Test
	void currentFallsBackWhenTheClientHasNotStarted() {
		assertEquals(WaterVisibility.NORMAL, WaterVisibility.current());
		assertFalse(WaterVisibility.hidesWater());
		assertFalse(WaterVisibility.clearsView());
		assertFalse(ClearWater.enabled());
	}
}
