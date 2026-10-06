package dev.relay.place;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;

class EasyPlaceHitsTest {
	@Test
	void downFaceHitSitsOnTheBottom() {
		BlockHitResult hit = EasyPlaceHits.faceHit(new BlockPos(3, 4, 5), Direction.DOWN, 0.25, 0.75);
		assertEquals(Direction.DOWN, hit.getDirection());
		assertEquals(new BlockPos(3, 4, 5), hit.getBlockPos());
		assertEquals(3.25, hit.getLocation().x, 1e-9);
		assertEquals(4.0, hit.getLocation().y, 1e-9);
		assertEquals(5.75, hit.getLocation().z, 1e-9);
	}

	@Test
	void eastFaceHitSitsOnThePositiveX() {
		BlockHitResult hit = EasyPlaceHits.faceHit(new BlockPos(1, 2, 3), Direction.EAST, 0.25, 0.75);
		assertEquals(2.0, hit.getLocation().x, 1e-9);
		assertEquals(2.75, hit.getLocation().y, 1e-9);
		assertEquals(3.25, hit.getLocation().z, 1e-9);
	}

	@Test
	void aCloserSolidBlocksTheGhost() {
		assertFalse(EasyPlaceHits.visible(5.0, 3.0, true));
	}

	@Test
	void aBackingFaceAtTheGhostIsVisible() {
		assertTrue(EasyPlaceHits.visible(5.0, 4.995, true));
	}

	@Test
	void aMissIsVisible() {
		assertTrue(EasyPlaceHits.visible(5.0, Double.POSITIVE_INFINITY, false));
	}

	@Test
	void tooFarIsOutOfReach() {
		assertFalse(EasyPlaceHits.withinReach(6.0, 4.5));
		assertTrue(EasyPlaceHits.withinReach(4.5, 4.5));
	}

	@Test
	void horizontalFacingUsesLevelPitch() {
		assertEquals(0.0F, EasyPlaceHits.assistPitch(Direction.NORTH, 30.0F));
		assertNull(EasyPlaceHits.assistPitch(Direction.NORTH, 0.0F));
	}

	@Test
	void verticalFacingLooksStraightUpOrDown() {
		assertEquals(89.0F, EasyPlaceHits.assistPitch(Direction.UP, 20.0F));
		assertEquals(-89.0F, EasyPlaceHits.assistPitch(Direction.DOWN, -12.0F));
		assertNull(EasyPlaceHits.assistPitch(Direction.UP, 0.0F));
	}
}
