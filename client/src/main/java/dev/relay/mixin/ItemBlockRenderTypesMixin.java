package dev.relay.mixin;

import dev.relay.fluids.LavaVisibility;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.FluidState;

/**
 * I See Lava put lava on the translucent layer with {@code BlockRenderLayerMap.putFluid}.
 * That API is gone in Fabric 1.21.11, so the same lookup is overridden here.
 */
@Mixin(ItemBlockRenderTypes.class)
public abstract class ItemBlockRenderTypesMixin {
	@Inject(method = "getRenderLayer", at = @At("HEAD"), cancellable = true)
	private static void relay$translucentLava(FluidState state, CallbackInfoReturnable<ChunkSectionLayer> cir) {
		if (state.is(FluidTags.LAVA) && LavaVisibility.usesTranslucentPass()) {
			cir.setReturnValue(ChunkSectionLayer.TRANSLUCENT);
		}
	}
}
