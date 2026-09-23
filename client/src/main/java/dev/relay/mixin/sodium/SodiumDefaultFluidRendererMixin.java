package dev.relay.mixin.sodium;

import dev.relay.fluids.FluidDraw;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.level.material.FluidState;

/**
 * Backup if a Sodium build meshes through {@code DefaultFluidRenderer} without going
 * through {@code FluidRendererImpl}.
 */
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.DefaultFluidRenderer")
public abstract class SodiumDefaultFluidRendererMixin {
	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void relay$skipHiddenFluids(Object level, Object blockState, FluidState fluidState,
			Object blockPos, Object offset, Object collector, Object meshBuilder, Object material,
			Object colorProvider, Object model, CallbackInfo ci) {
		if (FluidDraw.hides(fluidState)) {
			ci.cancel();
		}
	}
}
