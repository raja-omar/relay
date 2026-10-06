package dev.relay.place;

import dev.relay.litematica.LitematicaIntegration;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Mixin callbacks for Litematica's Easy Place {@code useItemOn} calls.
 */
public final class EasyPlaceClicks {
	private EasyPlaceClicks() {
	}

	public static InteractionResult primary(
			MultiPlayerGameMode manager, LocalPlayer player, InteractionHand hand, BlockHitResult hit) {
		InteractionResult result = EasyPlaceOrientation.interact(manager, player, hand, hit);
		if (result == null || !result.consumesAction()) {
			if (hit != null) {
				LitematicaIntegration.forgetEasyPlaceTarget(hit.getBlockPos());
			}
		}

		return result == null ? InteractionResult.FAIL : result;
	}

	public static InteractionResult skipSameTickDoubleSlab(BlockHitResult hit) {
		if (hit != null) {
			LitematicaIntegration.forgetEasyPlaceTarget(hit.getBlockPos());
		}

		return InteractionResult.FAIL;
	}
}
