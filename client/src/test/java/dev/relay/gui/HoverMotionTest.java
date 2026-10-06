package dev.relay.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HoverMotionTest {
	@Test
	void aFreshWidgetUnderTheCursorStartsFullyHovered() {
		assertEquals(1.0F, HoverMotion.seed(true));
		assertEquals(0.0F, HoverMotion.seed(false));
	}

	@Test
	void oneTickOfNinetyMsIsABitOverHalfway() {
		assertEquals(50.0F / 90.0F, HoverMotion.approach(0.0F, 1.0F, 1.0F, 90), 0.0001F);
	}

	@Test
	void itStopsOnTheTarget() {
		assertEquals(1.0F, HoverMotion.approach(0.9F, 1.0F, 8.0F, 90));
		assertEquals(0.0F, HoverMotion.approach(0.1F, 0.0F, 8.0F, 90));
	}

	@Test
	void zeroDurationSnaps() {
		assertEquals(1.0F, HoverMotion.approach(0.0F, 1.0F, 0.0F, 0));
	}
}
