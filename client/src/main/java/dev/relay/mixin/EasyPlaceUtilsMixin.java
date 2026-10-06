package dev.relay.mixin;

import dev.relay.place.CantMiss;
import dev.relay.place.EasyPlaceClicks;
import dev.relay.place.RedstoneGateTuner;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;

@Mixin(targets = "fi.dy.masa.litematica.util.EasyPlaceUtils", remap = false)
public abstract class EasyPlaceUtilsMixin {
	@Inject(method = "handleEasyPlace", at = @At("HEAD"), cancellable = true, require = 0)
	private static void relay$tuneRedstoneGate(CallbackInfoReturnable<InteractionResult> cir) {
		InteractionResult result = RedstoneGateTuner.interceptEasyPlace(Minecraft.getInstance());
		if (result != null) {
			cir.setReturnValue(result);
		}
	}

	@Inject(method = "handleEasyPlace", at = @At("RETURN"), cancellable = true, require = 0)
	private static void relay$buyMissingEasyPlaceMaterial(CallbackInfoReturnable<InteractionResult> cir) {
		if (cir.getReturnValue() != InteractionResult.FAIL) {
			return;
		}

		if (CantMiss.buyMissingEasyPlaceMaterial(Minecraft.getInstance())) {
			cir.setReturnValue(InteractionResult.SUCCESS);
		}
	}

	@Redirect(
			method = "handleEasyPlace",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;useItemOn(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;",
					ordinal = 0,
					remap = true),
			require = 0)
	private static InteractionResult relay$orientPrimaryPlacement(
			MultiPlayerGameMode manager, LocalPlayer player, InteractionHand hand, BlockHitResult hit) {
		return EasyPlaceClicks.primary(manager, player, hand, hit);
	}

	@Redirect(
			method = "handleEasyPlace",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;useItemOn(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;",
					ordinal = 1,
					remap = true),
			require = 0)
	private static InteractionResult relay$skipSameTickDoubleSlab(
			MultiPlayerGameMode manager, LocalPlayer player, InteractionHand hand, BlockHitResult hit) {
		return EasyPlaceClicks.skipSameTickDoubleSlab(hit);
	}
}
