package dev.relay.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RelayLayoutTest {
	@Test
	void scaleThreeStillLeavesAMargin() {
		RelayLayout.Panel panel = RelayLayout.panel(640, 360);
		assertTrue(panel.sidebar() >= 118, "sidebar=" + panel.sidebar());
		assertTrue(panel.width() >= 460, "width=" + panel.width());
		assertTrue(panel.height() >= 300, "height=" + panel.height());
		assertTrue(panel.width() < 640);
		assertTrue(panel.height() < 360);
	}

	@Test
	void scaleTwoUsesTheCap() {
		RelayLayout.Panel panel = RelayLayout.panel(960, 540);
		assertEquals(600, panel.width());
		assertEquals(420, panel.height());
		assertEquals(132, panel.sidebar());
	}

	@Test
	void higherResolutionsDoNotGrowTheOverlay() {
		RelayLayout.Panel hd = RelayLayout.panel(960, 540);
		RelayLayout.Panel qhd = RelayLayout.panel(1280, 720);
		assertEquals(hd.width(), qhd.width());
		assertEquals(hd.height(), qhd.height());
	}

	@ParameterizedTest
	@CsvSource({
			"640, 360",
			"960, 540",
			"1280, 720",
			"1920, 1080"
	})
	void thePanelNeverFillsTheScreen(int guiWidth, int guiHeight) {
		RelayLayout.Panel panel = RelayLayout.panel(guiWidth, guiHeight);
		assertTrue(panel.width() <= guiWidth - 16);
		assertTrue(panel.height() <= guiHeight - 16);
		assertTrue(panel.sidebar() < panel.width());
	}

	@Test
	void nativeScaleStaysCapped() {
		RelayLayout.Panel panel = RelayLayout.panel(1920, 1080);
		assertEquals(600, panel.width());
		assertEquals(420, panel.height());
		assertEquals(132, panel.sidebar());
	}
}
