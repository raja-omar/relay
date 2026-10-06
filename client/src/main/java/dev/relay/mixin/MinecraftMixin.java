package dev.relay.mixin;

import dev.relay.place.CantMiss;
import dev.relay.place.FastPlace;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Shadow
	private int rightClickDelay;

	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void relay$beginHeldUse(CallbackInfo ci) {
		Minecraft client = (Minecraft) (Object) this;
		if (CantMiss.interceptCrouchWaterUse(client)) {
			ci.cancel();
		} else {
			CantMiss.armEmptyBucketDrop(client);
		}
	}

	@Inject(method = "handleKeybinds", at = @At("HEAD"))
	private void relay$fastPlace(CallbackInfo ci) {
		FastPlace.delayCap((Minecraft) (Object) this).ifPresent(cap -> {
			if (this.rightClickDelay > cap) {
				this.rightClickDelay = cap;
			}
		});
	}
}
