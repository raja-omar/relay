package dev.relay.mixin;

import dev.relay.fluids.LavaVisibility;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.LavaFogEnvironment;

/**
 * Vanilla lava fog ends at 1 block, so standing inside lava is a solid orange wall.
 * Translucent mode keeps a light wash and lets you see the world through it.
 */
@Mixin(LavaFogEnvironment.class)
public abstract class LavaFogEnvironmentMixin {
	@Inject(method = "setupFog", at = @At("HEAD"), cancellable = true)
	private void relay$seeThroughLavaFog(FogData data, Camera camera, ClientLevel level, float renderDistance,
			DeltaTracker tracker, CallbackInfo ci) {
		if (!LavaVisibility.seeThroughLava()) {
			return;
		}

		float end = Math.max(64.0F, renderDistance);
		data.environmentalStart = 8.0F;
		data.environmentalEnd = end;
		data.skyEnd = end;
		data.cloudEnd = end;
		ci.cancel();
	}
}
