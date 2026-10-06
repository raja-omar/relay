package dev.relay.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PlacementFacingTest {
	@Test
	void rotationLabelsFollowMinecraftOrdinals() {
		assertEquals(4, PlacementFacing.ROTATION_COUNT);
		assertEquals("None", PlacementFacing.rotationLabel(0));
		assertEquals("90°", PlacementFacing.rotationLabel(1));
		assertEquals("180°", PlacementFacing.rotationLabel(2));
		assertEquals("270°", PlacementFacing.rotationLabel(3));
		assertEquals("None", PlacementFacing.rotationLabel(-1));
		assertEquals("None", PlacementFacing.rotationLabel(99));
	}

	@Test
	void mirrorLabelsFollowMinecraftOrdinals() {
		assertEquals(3, PlacementFacing.MIRROR_COUNT);
		assertEquals("Off", PlacementFacing.mirrorLabel(0));
		assertEquals("Left/Right", PlacementFacing.mirrorLabel(1));
		assertEquals("Front/Back", PlacementFacing.mirrorLabel(2));
		assertEquals("Off", PlacementFacing.mirrorLabel(4));
	}

	@Test
	void hintsNameTheNextClick() {
		assertEquals("Turn this placement 90° clockwise.", PlacementFacing.rotateHint());
		assertEquals("Mirror this placement. Click to cycle Off, Left/Right and Front/Back.",
				PlacementFacing.mirrorHint());
		assertEquals("That placement is locked.", PlacementFacing.lockedHint());
		assertEquals("Open Litematica's material list for this placement.", PlacementFacing.materialsHint());
		assertEquals("Open Litematica's schematic verifier for this placement.", PlacementFacing.verifierHint());
	}
}
