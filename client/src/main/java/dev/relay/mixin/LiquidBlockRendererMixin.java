package dev.relay.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.relay.fluids.FluidDraw;
import dev.relay.fluids.LavaVisibility;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

@Mixin(LiquidBlockRenderer.class)
public abstract class LiquidBlockRendererMixin {
	@Inject(method = "tesselate", at = @At("HEAD"), cancellable = true)
	private void relay$beginLavaMesh(BlockAndTintGetter level, BlockPos pos, VertexConsumer consumer,
			BlockState blockState, FluidState fluidState, CallbackInfo ci) {
		if (FluidDraw.hides(fluidState)) {
			ci.cancel();
			return;
		}

		LavaVisibility.beginMesh(fluidState);
	}

	@Inject(method = "tesselate", at = @At("RETURN"))
	private void relay$endLavaMesh(CallbackInfo ci) {
		LavaVisibility.endMesh();
	}

	@Redirect(
			method = "vertex",
			at = @At(
					value = "INVOKE",
					target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;setColor(FFFF)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
	private VertexConsumer relay$translucentLavaAlpha(VertexConsumer consumer, float red, float green, float blue,
			float alpha) {
		return consumer.setColor(red, green, blue, LavaVisibility.meshAlpha(alpha));
	}
}
