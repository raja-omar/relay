package dev.relay.mixin;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Remote servers often send {@code BlockChangedAck} before the block update. Vanilla then
 * restores the pre-prediction air and the later update puts the block back — the flicker.
 * If no server state was recorded yet, keep the predicted block and let the update apply it.
 */
@Mixin(BlockStatePredictionHandler.class)
public abstract class BlockStatePredictionHandlerMixin {
	@Unique
	private final LongOpenHashSet relay$serverUpdated = new LongOpenHashSet();

	@Inject(method = "retainKnownServerState", at = @At("HEAD"))
	private void relay$resetServerUpdate(BlockPos pos, BlockState state, LocalPlayer player, CallbackInfo ci) {
		this.relay$serverUpdated.remove(pos.asLong());
	}

	@Inject(method = "updateKnownServerState", at = @At("RETURN"))
	private void relay$markServerUpdate(BlockPos pos, BlockState state, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) {
			this.relay$serverUpdated.add(pos.asLong());
		}
	}

	@Redirect(
			method = "endPredictionsUpTo",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/multiplayer/ClientLevel;syncBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/phys/Vec3;)V"))
	private void relay$keepPredictedIfUnverified(ClientLevel level, BlockPos pos, BlockState serverState, Vec3 playerPos) {
		if (this.relay$serverUpdated.remove(pos.asLong())) {
			level.syncBlockState(pos, serverState, playerPos);
		}
	}
}
