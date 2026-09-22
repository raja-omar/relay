package dev.relay.mixin;

import dev.relay.fluids.WaterVisibility;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.WaterFluid;

@Mixin(WaterFluid.class)
public abstract class WaterFluidMixin {
	@Inject(method = "animateTick", at = @At("HEAD"), cancellable = true)
	private void relay$hideWaterParticles(Level level, BlockPos pos, FluidState state, RandomSource random,
			CallbackInfo ci) {
		if (WaterVisibility.hidesWater()) {
			ci.cancel();
		}
	}
}
