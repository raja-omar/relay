package dev.relay.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import dev.relay.gui.FacingNudge.Move;
import dev.relay.gui.FacingNudge.Step;

class FacingNudgeTest {
	@Test
	void cardinalLooksStayOnOneAxis() {
		assertEquals(new Step(0, 0, 1), FacingNudge.horizontal(0.0F));
		assertEquals(new Step(-1, 0, 0), FacingNudge.horizontal(90.0F));
		assertEquals(new Step(0, 0, -1), FacingNudge.horizontal(180.0F));
		assertEquals(new Step(1, 0, 0), FacingNudge.horizontal(270.0F));
		assertEquals(new Step(1, 0, 0), FacingNudge.horizontal(-90.0F));
	}

	@Test
	void aSlightDiagonalStillPrefersTheCloserSide() {
		assertEquals(new Step(0, 0, 1), FacingNudge.horizontal(20.0F));
		assertEquals(new Step(0, 0, 1), FacingNudge.horizontal(-20.0F));
		assertEquals(new Step(-1, 0, 0), FacingNudge.horizontal(70.0F));
		assertEquals(new Step(1, 0, 0), FacingNudge.horizontal(-70.0F));
	}

	@Test
	void aClearDiagonalStepsOnBothAxes() {
		assertEquals(new Step(-1, 0, 1), FacingNudge.horizontal(45.0F));
		assertEquals(new Step(-1, 0, -1), FacingNudge.horizontal(135.0F));
		assertEquals(new Step(1, 0, -1), FacingNudge.horizontal(225.0F));
		assertEquals(new Step(1, 0, 1), FacingNudge.horizontal(315.0F));
		assertEquals(new Step(-1, 0, 1), FacingNudge.horizontal(35.0F));
	}

	@Test
	void yawWrapsTheSameWayMinecraftDoes() {
		assertEquals(FacingNudge.horizontal(0.0F), FacingNudge.horizontal(360.0F));
		assertEquals(FacingNudge.horizontal(45.0F), FacingNudge.horizontal(405.0F));
		assertEquals(FacingNudge.horizontal(-45.0F), FacingNudge.horizontal(315.0F));
	}

	@Test
	void leftAndRightAreThePlayersSides() {
		assertEquals(new Step(1, 0, 0), FacingNudge.step(Move.LEFT, 0.0F));
		assertEquals(new Step(-1, 0, 0), FacingNudge.step(Move.RIGHT, 0.0F));
		assertEquals(new Step(0, 0, 1), FacingNudge.step(Move.LEFT, 90.0F));
		assertEquals(new Step(0, 0, -1), FacingNudge.step(Move.RIGHT, 90.0F));
		assertEquals(new Step(1, 0, 1), FacingNudge.step(Move.LEFT, 45.0F));
		assertEquals(new Step(-1, 0, -1), FacingNudge.step(Move.RIGHT, 45.0F));
	}

	@Test
	void backIsTheOppositeOfForward() {
		assertEquals(new Step(0, 0, -1), FacingNudge.step(Move.BACK, 0.0F));
		assertEquals(new Step(1, 0, -1), FacingNudge.step(Move.BACK, 45.0F));
	}

	@Test
	void upAndDownIgnoreYaw() {
		assertEquals(new Step(0, 1, 0), FacingNudge.step(Move.UP, 135.0F));
		assertEquals(new Step(0, -1, 0), FacingNudge.step(Move.DOWN, 135.0F));
	}

	@Test
	void headingsNameTheResolvedSide() {
		assertEquals("south", FacingNudge.heading(0.0F));
		assertEquals("west", FacingNudge.heading(90.0F));
		assertEquals("southwest", FacingNudge.heading(45.0F));
		assertEquals("northeast", FacingNudge.heading(225.0F));
		assertEquals("up", FacingNudge.heading(new Step(0, 1, 0)));
		assertEquals("forward", FacingNudge.describe(Move.FORWARD));
		assertEquals("up", FacingNudge.describe(Move.UP));
	}

	@Test
	void hintsStayGenericInsteadOfNamingACompassPoint() {
		assertEquals("Move one block forward, relative to where you are looking.", FacingNudge.hint(Move.FORWARD));
		assertEquals("Move one block up.", FacingNudge.hint(Move.UP));
		assertEquals("Move one block down.", FacingNudge.hint(Move.DOWN));
	}
}
