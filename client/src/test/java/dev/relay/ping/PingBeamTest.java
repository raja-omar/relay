package dev.relay.ping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.relay.ping.PingBeam.Segment;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

class PingBeamTest {
	private static final BlockPos POS = new BlockPos(10, 64, -3);
	private static final double CX = 10.5;
	private static final double CY = 64.5;
	private static final double CZ = -2.5;
	private static final int REACH = 1024;

	@Test
	void eastAndWestRunAlongXThroughTheCenter() {
		assertEquals(PingBeam.through(POS, Direction.EAST, REACH), PingBeam.through(POS, Direction.WEST, REACH));
		Segment beam = PingBeam.through(POS, Direction.EAST, REACH);
		assertEquals(new Segment(CX - REACH, CY, CZ, CX + REACH, CY, CZ), beam);
		assertCenter(beam);
	}

	@Test
	void upAndDownRunAlongYThroughTheCenter() {
		assertEquals(PingBeam.through(POS, Direction.UP, REACH), PingBeam.through(POS, Direction.DOWN, REACH));
		Segment beam = PingBeam.through(POS, Direction.UP, REACH);
		assertEquals(new Segment(CX, CY - REACH, CZ, CX, CY + REACH, CZ), beam);
		assertCenter(beam);
	}

	@Test
	void northAndSouthRunAlongZThroughTheCenter() {
		assertEquals(PingBeam.through(POS, Direction.NORTH, REACH), PingBeam.through(POS, Direction.SOUTH, REACH));
		Segment beam = PingBeam.through(POS, Direction.NORTH, REACH);
		assertEquals(new Segment(CX, CY, CZ - REACH, CX, CY, CZ + REACH), beam);
		assertCenter(beam);
	}

	@Test
	void shaftIsALittleThinnerThanABeaconBeam() {
		assertTrue(PingBeam.HALF_WIDTH < 0.2F);
		assertTrue(PingBeam.HALF_WIDTH > 0.12F);
	}

	@Test
	void defaultReachExtendsFarEnoughToReadAsEndless() {
		Segment beam = PingBeam.through(POS, Direction.SOUTH);
		assertEquals(PingBeam.REACH, 1024);
		assertEquals(CZ - PingBeam.REACH, beam.z1(), 1e-9);
		assertEquals(CZ + PingBeam.REACH, beam.z2(), 1e-9);
		assertCenter(beam);
	}

	private static void assertCenter(Segment beam) {
		assertEquals(CX, (beam.x1() + beam.x2()) / 2.0, 1e-9);
		assertEquals(CY, (beam.y1() + beam.y2()) / 2.0, 1e-9);
		assertEquals(CZ, (beam.z1() + beam.z2()) / 2.0, 1e-9);
	}
}
