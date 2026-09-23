package dev.relay.mixin;

import dev.relay.fluids.ClearWater;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.WaterFogEnvironment;

/**
 * Clear water should not use vanilla's short underwater fog or the biome water wash.
 * Camera remapping usually skips this class; this is the fallback if FogType stays WATER.
 */
@Mixin(WaterFogEnvironment.class)
public abstract class WaterFogEnvironmentMixin {
	@Inject(method = "setupFog", at = @At("HEAD"), cancellable = true)
	private void relay$clearWaterFog(FogData data, Camera camera, ClientLevel level, float renderDistance,
			DeltaTracker tracker, CallbackInfo ci) {
		if (!ClearWater.clearsView()) {
			return;
		}

		float end = Math.max(96.0F, renderDistance);
		data.environmentalStart = end;
		data.environmentalEnd = end;
		data.skyEnd = end;
		data.cloudEnd = end;
		ci.cancel();
	}

	@Inject(method = "getBaseColor", at = @At("HEAD"), cancellable = true)
	private void relay$clearWaterColor(ClientLevel level, Camera camera, int renderDistance, float partialTick,
			CallbackInfoReturnable<Integer> cir) {
		if (ClearWater.clearsView()) {
			cir.setReturnValue(-1);
		}
	}
}
