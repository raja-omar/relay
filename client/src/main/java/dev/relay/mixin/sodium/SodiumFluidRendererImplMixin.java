package dev.relay.mixin.sodium;

import dev.relay.fluids.FluidDraw;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.level.material.FluidState;

/**
 * Sodium never calls vanilla {@code LiquidBlockRenderer.tesselate}. Cancel at its fluid
 * entry so Off is a chunk rebuild, not a resource-pack reload.
 */
@Mixin(targets = "net.caffeinemc.mods.sodium.fabric.render.FluidRendererImpl")
public abstract class SodiumFluidRendererImplMixin {
	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void relay$skipHiddenFluids(Object level, Object blockState, FluidState fluidState,
			Object blockPos, Object offset, Object collector, Object buffers, CallbackInfo ci) {
		if (FluidDraw.hides(fluidState)) {
			ci.cancel();
		}
	}
}
