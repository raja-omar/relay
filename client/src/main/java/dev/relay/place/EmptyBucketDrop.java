package dev.relay.place;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * After a vanilla water bucket is emptied, throw the leftover empty bucket so the hotbar stays
 * a build slot.
 */
final class EmptyBucketDrop {
	private static final long PENDING_TIMEOUT_MS = 3000L;
	private static Pending pending;

	private EmptyBucketDrop() {
	}

	static void armCurrentUse(Minecraft client) {
		if (client == null || client.player == null || client.level == null || client.gameMode == null) {
			return;
		}

		LocalPlayer player = client.player;
		ItemStack mainHand = player.getMainHandItem();
		if (isVanillaWaterBucket(mainHand)) {
			pending = new Pending(
					player, client.level, InteractionHand.MAIN_HAND, player.getInventory().getSelectedSlot(),
					System.currentTimeMillis() + PENDING_TIMEOUT_MS);
			return;
		}

		ItemStack offHand = player.getOffhandItem();
		if (isVanillaWaterBucket(offHand)) {
			pending = new Pending(player, client.level, InteractionHand.OFF_HAND, -1,
					System.currentTimeMillis() + PENDING_TIMEOUT_MS);
			return;
		}

		Pending request = pending;
		if (request != null && request.player == player && request.world == client.level) {
			ItemStack tracked = trackedStack(request);
			boolean completed = tracked.is(Items.BUCKET) && tracked.getCount() == 1;
			if (!isVanillaWaterBucket(tracked) && !completed) {
				pending = null;
			}
		}
	}

	static void cancelPending() {
		pending = null;
	}

	static void tick(Minecraft client) {
		Pending request = pending;
		if (request == null) {
			return;
		}

		long now = System.currentTimeMillis();
		if (client == null || client.player != request.player || client.level != request.world || now > request.expiresAt) {
			pending = null;
			return;
		}

		LocalPlayer player = request.player;
		if (client.screen != null || client.gameMode == null || player.containerMenu != player.inventoryMenu) {
			return;
		}

		ItemStack result = trackedStack(request);
		int screenSlot = request.hand == InteractionHand.MAIN_HAND ? 36 + request.hotbarSlot : 45;
		if (result.is(Items.BUCKET) && result.getCount() == 1) {
			client.gameMode.handleInventoryMouseClick(
					player.inventoryMenu.containerId, screenSlot, 0, net.minecraft.world.inventory.ClickType.THROW, player);
			PlacementRateLimiter.deferAfterMutation(client.level);
			pending = null;
		} else if (!isVanillaWaterBucket(result)) {
			pending = null;
		}
	}

	private static ItemStack trackedStack(Pending request) {
		return request.hand == InteractionHand.MAIN_HAND
				? request.player.getInventory().getItem(request.hotbarSlot)
				: request.player.getOffhandItem();
	}

	private static boolean isVanillaWaterBucket(ItemStack stack) {
		return WaterBuckets.isVanilla(stack);
	}

	private record Pending(
			LocalPlayer player, ClientLevel world, InteractionHand hand, int hotbarSlot, long expiresAt) {
	}
}
