package dev.relay.mixin;

import dev.relay.place.RedstoneGateTuner;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	@Inject(method = "handleBlockUpdate", at = @At("TAIL"))
	private void relay$ackSingleBlockUpdate(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
		RedstoneGateTuner.onAuthoritativeBlockUpdate(packet.getPos(), packet.getBlockState());
	}

	@Inject(method = "handleChunkBlocksUpdate", at = @At("TAIL"))
	private void relay$ackChunkBlockUpdates(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
		packet.runUpdates(RedstoneGateTuner::onAuthoritativeBlockUpdate);
	}
}
