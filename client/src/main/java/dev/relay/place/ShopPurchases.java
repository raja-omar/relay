package dev.relay.place;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import dev.relay.RelayClient;
import dev.relay.RelayConfig;
import dev.relay.chat.ChatMessages;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BeaconBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Buys schematic blocks from {@code /shop} when cant-miss is short of material.
 *
 * <p>{@code autoPurchase} buys when the item is missing. {@code prePurchase} waits a beat after a
 * successful place and tops the stack back up, so the next click is not empty-handed.
 */
final class ShopPurchases {
	private static final long PURCHASE_COOLDOWN_MS = 1000L;
	private static final long PRE_PURCHASE_DELAY_MS = 250L;
	private static final long PURCHASE_DELIVERY_GUARD_MS = 3000L;

	private static long lastPurchase;
	private static PendingPrePurchase pendingPrePurchase;
	private static final List<PendingPurchase> pendingPurchases = new ArrayList<>();

	private ShopPurchases() {
	}

	static void tick(Minecraft client) {
		long now = System.currentTimeMillis();
		LocalPlayer player = client == null ? null : client.player;
		ClientLevel level = client == null ? null : client.level;
		observeDeliveries(player, level, now);

		PendingPrePurchase pending = pendingPrePurchase;
		if (pending == null) {
			return;
		}

		if (player != pending.player
				|| level != pending.level
				|| client.screen != null
				|| !config().autoPurchase()
				|| !config().prePurchase()
				|| !config().cantMiss()) {
			pendingPrePurchase = null;
			return;
		}

		if (now >= pending.dueAt) {
			pendingPrePurchase = null;
			buyIfNeeded(pending.player, pending.wanted, pending.required, false);
		}
	}

	static boolean tryAutoPurchase(LocalPlayer player, BlockState wanted, ItemStack required) {
		return buyIfNeeded(player, wanted, required, true);
	}

	static boolean tryBuyWaterBucket(LocalPlayer player, BlockState wanted) {
		return purchase(player, new ItemStack(Items.WATER_BUCKET), 1);
	}

	static void schedulePrePurchase(LocalPlayer player, ClientLevel level, BlockState wanted, ItemStack required) {
		if (player == null
				|| level == null
				|| required == null
				|| required.isEmpty()
				|| !config().autoPurchase()
				|| !config().prePurchase()
				|| isOneAtATime(wanted, required)) {
			return;
		}

		if (quantityToBuy(wanted, required, count(player, required)) <= 0) {
			return;
		}

		PendingPrePurchase pending = pendingPrePurchase;
		if (pending == null
				|| pending.player != player
				|| pending.level != level
				|| !ItemStack.isSameItemSameComponents(pending.required, required)) {
			pendingPrePurchase = new PendingPrePurchase(
					player, level, wanted, required.copy(), System.currentTimeMillis() + PRE_PURCHASE_DELAY_MS);
		}
	}

	private static boolean buyIfNeeded(LocalPlayer player, BlockState wanted, ItemStack required, boolean allowOneAtATime) {
		if (player == null
				|| player.isCreative()
				|| required == null
				|| required.isEmpty()
				|| !config().autoPurchase()) {
			return false;
		}

		if (!allowOneAtATime && isOneAtATime(wanted, required)) {
			return false;
		}

		int quantity = quantityToBuy(wanted, required, count(player, required));
		return quantity > 0 && purchase(player, required, quantity);
	}

	private static boolean purchase(LocalPlayer player, ItemStack expected, int quantity) {
		long now = System.currentTimeMillis();
		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client == null ? null : client.level;
		observeDeliveries(player, level, now);

		if (hasPendingPurchase(player, level, expected, now) || now - lastPurchase < PURCHASE_COOLDOWN_MS) {
			return true;
		}

		if (!hasRoom(player, expected, quantity) && !dropExpendableBuildStack(player, expected)) {
			hint(player, "Inventory full — no safe building stack to drop");
			return false;
		}

		lastPurchase = now;
		int baseline = count(player, expected);
		int target = (int) Math.min(Math.max(1, expected.getMaxStackSize()), (long) baseline + Math.max(0, quantity));
		pendingPurchases.add(new PendingPurchase(
				player, level, expected.copy(), target, now + PURCHASE_DELIVERY_GUARD_MS));
		player.connection.sendCommand("shop " + shopItemId(expected) + " " + quantity);
		return true;
	}

	private static int quantityToBuy(BlockState wanted, ItemStack stack, int currentCount) {
		int max = stack != null && !stack.isEmpty() ? stack.getMaxStackSize() : 64;
		return PurchaseQuantityPolicy.quantityToBuy(currentCount, max, isOneAtATime(wanted, stack));
	}

	static boolean isOneAtATime(BlockState wanted, ItemStack stack) {
		if (wanted != null
				&& (wanted.getBlock() instanceof DoorBlock || wanted.getBlock() instanceof TrapDoorBlock)) {
			return true;
		}

		return stack != null
				&& !stack.isEmpty()
				&& stack.getItem() instanceof net.minecraft.world.item.BlockItem blockItem
				&& (blockItem.getBlock() instanceof DoorBlock || blockItem.getBlock() instanceof TrapDoorBlock);
	}

	static String shopItemId(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
	}

	static int count(LocalPlayer player, ItemStack wanted) {
		if (player == null || wanted == null || wanted.isEmpty()) {
			return 0;
		}

		long total = 0;
		var inventory = player.getInventory();

		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(wanted, stack)) {
				total += stack.getCount();
				if (total >= Integer.MAX_VALUE) {
					return Integer.MAX_VALUE;
				}
			}
		}

		return (int) total;
	}

	static int findMatchingSlot(LocalPlayer player, ItemStack wanted) {
		if (player == null || wanted == null || wanted.isEmpty()) {
			return -1;
		}

		var inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(wanted, stack)) {
				return slot;
			}
		}

		return -1;
	}

	private static boolean hasRoom(LocalPlayer player, ItemStack expected, int quantity) {
		if (quantity <= 0) {
			return true;
		}

		var inventory = player.getInventory();
		int remaining = quantity;
		int emptyCapacity = expected != null && !expected.isEmpty() ? Math.max(1, expected.getMaxStackSize()) : 64;

		for (int slot = 0; slot <= 35; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty()) {
				remaining -= emptyCapacity;
			} else if (expected != null && !expected.isEmpty() && ItemStack.isSameItemSameComponents(expected, stack)) {
				remaining -= Math.max(0, stack.getMaxStackSize() - stack.getCount());
			}

			if (remaining <= 0) {
				return true;
			}
		}

		return false;
	}

	private static boolean dropExpendableBuildStack(LocalPlayer player, ItemStack expected) {
		int slot = findExpendableBuildStackSlot(player, expected);
		if (slot < 0) {
			return false;
		}

		ItemStack discarded = player.getInventory().getItem(slot).copy();
		if (!InventoryActions.dropEntireSlot(player, slot)) {
			return false;
		}

		ChatMessages.show(ChatMessages.info(
				"Dropped " + discarded.getCount() + "x " + discarded.getHoverName().getString()
						+ " to make purchase room"));
		return true;
	}

	private static int findExpendableBuildStackSlot(LocalPlayer player, ItemStack expected) {
		var inventory = player.getInventory();
		int bestSlot = -1;
		int bestPriority = Integer.MAX_VALUE;
		int bestCount = Integer.MAX_VALUE;

		for (int slot = 9; slot <= 35; slot++) {
			ItemStack candidate = inventory.getItem(slot);
			if (!isSafeEvictionCandidate(candidate, expected)) {
				continue;
			}

			int priority = HotbarRefill.wasRecentlyUsedBuildStack(candidate) ? 0 : 1;
			if (priority < bestPriority || (priority == bestPriority && candidate.getCount() < bestCount)) {
				bestSlot = slot;
				bestPriority = priority;
				bestCount = candidate.getCount();
			}
		}

		return bestSlot;
	}

	private static boolean isSafeEvictionCandidate(ItemStack stack, ItemStack expected) {
		if (stack == null
				|| stack.isEmpty()
				|| stack.get(DataComponents.CUSTOM_NAME) != null
				|| stack.isEnchanted()
				|| stack.isDamaged()
				|| stack.isDamageableItem()
				|| stack.hasFoil()
				|| (expected != null && !expected.isEmpty() && stack.getItem() == expected.getItem())
				|| isOneAtATime(null, stack)) {
			return false;
		}

		if (stack.is(Items.REDSTONE)) {
			return true;
		}

		if (stack.getItem() instanceof BlockItem blockItem) {
			Block block = blockItem.getBlock();
			return !isProtectedValuableBlock(block)
					&& (HotbarRefill.wasRecentlyUsedBuildStack(stack) || isRedstoneEvictionBlock(block));
		}

		return false;
	}

	private static boolean isProtectedValuableBlock(Block block) {
		return block instanceof ShulkerBoxBlock
				|| block instanceof BeaconBlock
				|| block instanceof EnderChestBlock
				|| block instanceof ChestBlock
				|| block instanceof BarrelBlock
				|| block instanceof AnvilBlock
				|| block instanceof HopperBlock
				|| block == Blocks.ENCHANTING_TABLE
				|| block == Blocks.CRAFTING_TABLE
				|| block == Blocks.FURNACE
				|| block == Blocks.BLAST_FURNACE
				|| block == Blocks.SMOKER
				|| block == Blocks.ENDER_CHEST;
	}

	private static boolean isRedstoneEvictionBlock(Block block) {
		return block instanceof RepeaterBlock
				|| block instanceof ComparatorBlock
				|| block instanceof RedstoneTorchBlock
				|| block instanceof ObserverBlock
				|| block instanceof PistonBaseBlock
				|| block instanceof LeverBlock
				|| block instanceof ButtonBlock
				|| block instanceof PressurePlateBlock
				|| block == Blocks.REDSTONE_WIRE
				|| block == Blocks.REDSTONE_BLOCK
				|| block == Blocks.REDSTONE_LAMP
				|| block == Blocks.TARGET
				|| block == Blocks.NOTE_BLOCK
				|| block == Blocks.TRIPWIRE_HOOK
				|| block == Blocks.TRIPWIRE
				|| block == Blocks.DAYLIGHT_DETECTOR
				|| block == Blocks.DISPENSER
				|| block == Blocks.DROPPER;
	}

	private static void observeDeliveries(LocalPlayer player, ClientLevel level, long now) {
		Iterator<PendingPurchase> iterator = pendingPurchases.iterator();
		while (iterator.hasNext()) {
			PendingPurchase pending = iterator.next();
			if (pending.player != player
					|| pending.level != level
					|| now >= pending.expiresAt
					|| count(player, pending.expected) >= pending.targetCount) {
				iterator.remove();
			}
		}
	}

	private static boolean hasPendingPurchase(LocalPlayer player, ClientLevel level, ItemStack expected, long now) {
		if (expected == null || expected.isEmpty()) {
			return false;
		}

		for (PendingPurchase pending : pendingPurchases) {
			if (pending.player == player
					&& pending.level == level
					&& now < pending.expiresAt
					&& ItemStack.isSameItemSameComponents(pending.expected, expected)) {
				return true;
			}
		}

		return false;
	}

	private static void hint(LocalPlayer player, String text) {
		ChatMessages.hint(text);
	}

	private static RelayConfig config() {
		return RelayClient.get().config();
	}

	private record PendingPrePurchase(
			LocalPlayer player, ClientLevel level, BlockState wanted, ItemStack required, long dueAt) {
	}

	private record PendingPurchase(
			LocalPlayer player, ClientLevel level, ItemStack expected, int targetCount, long expiresAt) {
	}
}
