package dev.relay.mixin;

import dev.relay.fluids.LavaVisibility;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.LavaFluid;

@Mixin(LavaFluid.class)
public abstract class LavaFluidMixin {
	@Inject(method = "animateTick", at = @At("HEAD"), cancellable = true)
	private void relay$hideLavaParticles(Level level, BlockPos pos, FluidState state, RandomSource random,
			CallbackInfo ci) {
		if (LavaVisibility.hidesLava()) {
			ci.cancel();
		}
	}
}
