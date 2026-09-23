package dev.relay.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.relay.fluids.ClearWater;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ScreenEffectRenderer;

/**
 * The underwater overlay is drawn from {@code isEyeInFluid}, not fog type, so remapping
 * {@code FogType.WATER} is not enough. Skip the overlay when clear water is on.
 */
@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {
	@Inject(method = "renderWater", at = @At("HEAD"), cancellable = true)
	private static void relay$skipWaterOverlay(Minecraft minecraft, PoseStack poseStack,
			MultiBufferSource bufferSource, CallbackInfo ci) {
		if (ClearWater.clearsView()) {
			ci.cancel();
		}
	}
}
