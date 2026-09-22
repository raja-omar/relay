package dev.relay.patchcrumbs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.relay.patchcrumbs.PatchCrumbsGuides.BlockSpan;
import dev.relay.patchcrumbs.PatchCrumbsGuides.ClippedLine;

class PatchCrumbsGuidesTest {
	@Test
	void lookingAlongXKeepsOnlyTheForwardHalf() {
		BlockSpan span = PatchCrumbsGuides.clipEastWest(
				0, 64, 0, 200,
				0.5, 64.5, 0.5,
				1.0, 0.0, 0.0);
		assertFalse(span.isEmpty());
		assertTrue(span.from() >= 0, "behind-camera -X must be dropped");
		assertEquals(200, span.to());
	}

	@Test
	void lookingAtTheWallKeepsTheWholeCrossAxis() {
		BlockSpan span = PatchCrumbsGuides.clipEastWest(
				0, 64, 0, 200,
				0.5, 64.5, -8.0,
				0.0, 0.0, 1.0);
		assertEquals(new BlockSpan(-200, 200), span);
	}

	@Test
	void walkingAlongZKeepsOnlyWhatIsInFront() {
		BlockSpan span = PatchCrumbsGuides.clipNorthSouth(
				0, 64, 0, 200,
				0.5, 64.5, 40.0,
				0.0, 0.0, 1.0);
		assertFalse(span.isEmpty());
		assertTrue(span.from() >= 40);
		assertEquals(200, span.to());
	}

	@Test
	void lookingPastTheFarEndDropsTheAxis() {
		BlockSpan span = PatchCrumbsGuides.clipNorthSouth(
				0, 64, 0, 200,
				0.5, 64.5, 250.0,
				0.0, 0.0, 1.0);
		assertTrue(span.isEmpty());
	}

	@Test
	void aLineThroughTheCameraIsCutAtTheNearPlane() {
		ClippedLine line = PatchCrumbsGuides.clipLine(
				-10, 0, 0,
				10, 0, 0,
				0, 0, 0,
				1, 0, 0);
		assertNotNull(line);
		assertEquals(PatchCrumbsGuides.NEAR_PLANE, line.x1(), 1.0E-6);
		assertEquals(10.0, line.x2(), 1.0E-6);
	}

	@Test
	void aLineWhollyInFrontIsUnchanged() {
		ClippedLine line = PatchCrumbsGuides.clipLine(
				2, 1, 3,
				8, 1, 3,
				0, 1, 3,
				1, 0, 0);
		assertEquals(new ClippedLine(2, 1, 3, 8, 1, 3), line);
	}

	@Test
	void aLineWhollyBehindIsDropped() {
		assertNull(PatchCrumbsGuides.clipLine(
				-8, 0, 0,
				-2, 0, 0,
				0, 0, 0,
				1, 0, 0));
	}
}
