package dev.relay.place;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

final class InventoryActions {
	private InventoryActions() {
	}

	static boolean handlerReady(LocalPlayer player) {
		Minecraft client = Minecraft.getInstance();
		return client != null
				&& client.gameMode != null
				&& client.screen == null
				&& player.containerMenu == player.inventoryMenu
				&& player.containerMenu.getCarried().isEmpty();
	}

	static boolean selectBuildSlot(LocalPlayer player, int sourceSlot) {
		Minecraft client = Minecraft.getInstance();
		if (player == null || client == null) {
			return false;
		}

		if (sourceSlot >= 0 && sourceSlot <= 8) {
			player.getInventory().setSelectedSlot(sourceSlot);
			PlacementRateLimiter.deferAfterMutation(client.level);
			return true;
		}

		if (sourceSlot >= 9 && sourceSlot <= 35 && handlerReady(player)) {
			int destination = findBuildHotbarDestination(player);
			client.gameMode.handleInventoryMouseClick(
					player.inventoryMenu.containerId, sourceSlot, destination, ClickType.SWAP, player);
			player.getInventory().setSelectedSlot(destination);
			PlacementRateLimiter.deferAfterMutation(client.level);
			return true;
		}

		return false;
	}

	static boolean dropEntireSlot(LocalPlayer player, int slot) {
		Minecraft client = Minecraft.getInstance();
		if (player == null || client == null || client.gameMode == null || !handlerReady(player)) {
			return false;
		}

		if (slot < 9 || slot > 35) {
			return false;
		}

		client.gameMode.handleInventoryMouseClick(
				player.inventoryMenu.containerId, slot, 1, ClickType.THROW, player);
		PlacementRateLimiter.deferAfterMutation(client.level);
		return true;
	}

	private static int findBuildHotbarDestination(LocalPlayer player) {
		var inventory = player.getInventory();
		int selected = inventory.getSelectedSlot();
		int anchor = selected >= 3 && selected <= 8 ? selected : 8;

		for (int offset = 1; offset <= 6; offset++) {
			int slot = 3 + Math.floorMod(anchor - 3 + offset, 6);
			if (inventory.getItem(slot).isEmpty()) {
				return slot;
			}
		}

		return 3 + Math.floorMod(anchor - 3 + 1, 6);
	}

	static boolean isPotion(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}

		var item = stack.getItem();
		return item == net.minecraft.world.item.Items.POTION
				|| item == net.minecraft.world.item.Items.SPLASH_POTION
				|| item == net.minecraft.world.item.Items.LINGERING_POTION;
	}
}
