package dev.relay.common;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PingLocationsTest {
	@Test
	void acceptsANormalOverworldBlock() {
		assertTrue(PingLocations.isValid(10, 64, -3, 2, "minecraft:overworld"));
	}

	@Test
	void refusesAFaceOrDimensionThatIsNotABlock() {
		assertFalse(PingLocations.isValid(10, 64, -3, 6, "minecraft:overworld"));
		assertFalse(PingLocations.isValid(10, 64, -3, -1, "minecraft:overworld"));
		assertFalse(PingLocations.isValid(10, 9000, -3, 2, "minecraft:overworld"));
		assertFalse(PingLocations.isValid(10, 64, -3, 2, "Overworld"));
		assertFalse(PingLocations.isValid(10, 64, -3, 2, "minecraft:the nether"));
	}
}
