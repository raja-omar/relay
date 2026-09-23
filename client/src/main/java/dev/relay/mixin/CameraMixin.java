package dev.relay.mixin;

import dev.relay.fluids.ClearWater;
import dev.relay.fluids.LavaVisibility;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Camera;
import net.minecraft.world.level.material.FogType;

/**
 * Hidden lava should not paint the world orange. Clear water remaps WATER so fog and FOV
 * stay at the air values; the water overlay is skipped separately.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Inject(method = "getFluidInCamera", at = @At("RETURN"), cancellable = true)
	private void relay$hideLavaFog(CallbackInfoReturnable<FogType> cir) {
		FogType type = cir.getReturnValue();
		if (type == FogType.LAVA && LavaVisibility.hidesLava()) {
			cir.setReturnValue(FogType.NONE);
		} else if (type == FogType.WATER && ClearWater.clearsView()) {
			cir.setReturnValue(FogType.NONE);
		}
	}
}
