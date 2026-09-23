package dev.relay.fluids;

import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.FluidState;

/**
 * Shared hide check for lava and water. Used from vanilla and Sodium mesh mixins.
 */
public final class FluidDraw {
	private FluidDraw() {
	}

	public static boolean hides(FluidState state) {
		if (state == null || state.isEmpty()) {
			return false;
		}
		if (state.is(FluidTags.LAVA)) {
			return LavaVisibility.hidesLava();
		}
		if (state.is(FluidTags.WATER)) {
			return WaterVisibility.hidesWater();
		}
		return false;
	}
}
