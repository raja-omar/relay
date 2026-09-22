package dev.relay.fluids;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LavaVisibilityTest {
	@Test
	void namesParseKnownWording() {
		assertEquals(LavaVisibility.NORMAL, LavaVisibility.fromName("vanilla", LavaVisibility.TRANSPARENT));
		assertEquals(LavaVisibility.TRANSLUCENT, LavaVisibility.fromName("see-thru", LavaVisibility.NORMAL));
		assertEquals(LavaVisibility.TRANSLUCENT, LavaVisibility.fromName("glass", LavaVisibility.NORMAL));
		assertEquals(LavaVisibility.TRANSPARENT, LavaVisibility.fromName("hidden", LavaVisibility.NORMAL));
		assertEquals(LavaVisibility.TRANSPARENT, LavaVisibility.fromName("off", LavaVisibility.NORMAL));
		assertEquals(LavaVisibility.NORMAL, LavaVisibility.fromName("nope", LavaVisibility.NORMAL));
	}

	@Test
	void cyclesThroughTheThreeModes() {
		assertEquals(LavaVisibility.TRANSLUCENT, LavaVisibility.NORMAL.next());
		assertEquals(LavaVisibility.TRANSPARENT, LavaVisibility.TRANSLUCENT.next());
		assertEquals(LavaVisibility.NORMAL, LavaVisibility.TRANSPARENT.next());
		assertEquals(LavaVisibility.TRANSPARENT, LavaVisibility.NORMAL.previous());
		assertEquals("See-through", LavaVisibility.TRANSLUCENT.label());
		assertEquals("Hidden", LavaVisibility.TRANSPARENT.label());
		assertEquals("off", LavaVisibility.TRANSPARENT.id());
	}

	@Test
	void currentFallsBackWhenTheClientHasNotStarted() {
		assertEquals(LavaVisibility.NORMAL, LavaVisibility.current());
		assertFalse(LavaVisibility.hidesLava());
		assertFalse(LavaVisibility.seeThroughLava());
	}

	@Test
	void translucentAlphaMatchesISeeLavaTextures() {
		assertEquals(120.0F / 255.0F, LavaVisibility.TRANSLUCENT_ALPHA);
		LavaVisibility.beginMesh(null);
		assertEquals(1.0F, LavaVisibility.meshAlpha(1.0F));
		LavaVisibility.endMesh();
	}
}
