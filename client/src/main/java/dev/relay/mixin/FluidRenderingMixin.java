package dev.relay.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.relay.fluids.FluidDraw;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandler;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRendering;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * Sodium 0.6+ draws vanilla fluids through Fabric's {@code FluidRendering.render}.
 * Cancelling here hides lava/water without a texture pack.
 */
@Mixin(FluidRendering.class)
public abstract class FluidRenderingMixin {
	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private static void relay$skipHiddenFluids(FluidRenderHandler handler, BlockAndTintGetter world, BlockPos pos,
			VertexConsumer vertexConsumer, BlockState blockState, FluidState fluidState,
			FluidRendering.DefaultRenderer defaultRenderer, CallbackInfo ci) {
		if (FluidDraw.hides(fluidState)) {
			ci.cancel();
		}
	}
}
