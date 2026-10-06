package dev.relay.mixin;

import dev.relay.place.CantMiss;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

@Mixin(targets = "fi.dy.masa.litematica.util.InventoryUtils", remap = false)
public abstract class InventoryUtilsMixin {
	@Inject(method = "schematicWorldPickBlock", at = @At("RETURN"), require = 0)
	private static void relay$buyWhenPickFindsNothing(
			ItemStack stack, BlockPos pos, Level world, Minecraft client, CallbackInfo ci) {
		CantMiss.buyMissingPickedItem(stack, pos);
	}
}
