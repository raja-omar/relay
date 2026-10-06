package dev.relay.litematica;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

class PlacementFingerprintTest {
	@Test
	void identityIgnoresOriginSoNudgeDoesNotRebuild() {
		var here = view(new BlockPos(1, 2, 3), 1, 0, false);
		var there = view(new BlockPos(8, 64, -4), 1, 0, false);
		assertEquals(LitematicaIntegration.fingerprint(List.of(here), false),
				LitematicaIntegration.fingerprint(List.of(there), false));
		assertNotEquals(LitematicaIntegration.fingerprint(List.of(here), true),
				LitematicaIntegration.fingerprint(List.of(there), true));
	}

	@Test
	void identityStillSeesRotationMirrorAndLock() {
		String identity = LitematicaIntegration.fingerprint(List.of(view(new BlockPos(1, 2, 3), 1, 0, false)), false);
		assertNotEquals(identity,
				LitematicaIntegration.fingerprint(List.of(view(new BlockPos(1, 2, 3), 2, 0, false)), false));
		assertNotEquals(identity,
				LitematicaIntegration.fingerprint(List.of(view(new BlockPos(1, 2, 3), 1, 1, false)), false));
		assertNotEquals(identity,
				LitematicaIntegration.fingerprint(List.of(view(new BlockPos(1, 2, 3), 1, 0, true)), false));
	}

	private static LitematicaIntegration.PlacementView view(BlockPos origin, int rotation, int mirror, boolean locked) {
		return new LitematicaIntegration.PlacementView("p", "House", origin, true, true, rotation, mirror, locked);
	}
}
