package dev.relay.mixin;

import dev.relay.place.CantMiss;
import dev.relay.place.HotbarRefill;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
	@Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
	private void relay$guardSchematicPlacement(
			LocalPlayer player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
		HotbarRefill.beginBlockInteraction(player, Minecraft.getInstance().level, hand, hit);
		InteractionResult result = CantMiss.intercept((MultiPlayerGameMode) (Object) this, player, hand, hit);
		if (result != null) {
			HotbarRefill.cancelBlockInteraction(player, Minecraft.getInstance().level, hand, hit);
			cir.setReturnValue(result);
		}
	}

	@Inject(method = "useItemOn", at = @At("RETURN"))
	private void relay$finishSchematicPlacement(
			LocalPlayer player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
		InteractionResult result = cir.getReturnValue();
		if (result != null && result.consumesAction()) {
			HotbarRefill.captureDispatchedBlockUse(player, Minecraft.getInstance().level, hand, hit);
		}

		CantMiss.afterUseItemOn(player, result);
		HotbarRefill.finishDispatchedBlockUse(player, Minecraft.getInstance().level, hand, hit, result);
	}

	@Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
	private void relay$guardWaterItemUse(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		if (player instanceof LocalPlayer local) {
			InteractionResult result = CantMiss.interceptItemUse(local, hand);
			if (result != null) {
				cir.setReturnValue(result);
			}
		}
	}
}
