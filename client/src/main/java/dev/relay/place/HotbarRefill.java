package dev.relay.place;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

import dev.relay.RelayClient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

/**
 * When a build-hotbar stack (slots 4–9) is used up, pull the same block from another hotbar slot
 * or the backpack.
 */
public final class HotbarRefill {
	private static final long RESTOCK_COOLDOWN_MS = 125L;
	private static final long PENDING_REFILL_TIMEOUT_MS = 1500L;
	private static final long RECENT_BUILD_STACK_MS = 600_000L;

	private static final ItemStack[] recentPlacedStacks = new ItemStack[9];
	private static final long[] recentPlacedAt = new long[9];
	private static final ThreadLocal<Deque<BlockUseFrame>> BLOCK_USE_FRAMES = ThreadLocal.withInitial(ArrayDeque::new);

	private static long lastRestock;
	private static LocalPlayer trackedPlayer;
	private static ClientLevel trackedWorld;
	private static PendingDepletion pendingDepletion;

	private HotbarRefill() {
	}

	public static void beginBlockInteraction(LocalPlayer player, ClientLevel world, InteractionHand hand, BlockHitResult hit) {
		if (player == null || world == null || hand == null || hit == null) {
			return;
		}

		if (player != trackedPlayer || world != trackedWorld) {
			resetContext(player, world);
		}

		BLOCK_USE_FRAMES.get().push(new BlockUseFrame(player, world, hand, hit));
	}

	public static void cancelBlockInteraction(LocalPlayer player, ClientLevel world, InteractionHand hand, BlockHitResult hit) {
		Deque<BlockUseFrame> frames = BLOCK_USE_FRAMES.get();
		if (!frames.isEmpty() && !frames.peek().packetSent && frames.peek().matches(player, world, hand, hit)) {
			frames.pop();
		}
	}

	public static void captureDispatchedBlockUse(LocalPlayer player, ClientLevel world, InteractionHand hand, BlockHitResult hit) {
		if (player == null || world == null || hand == null || hit == null) {
			return;
		}

		Deque<BlockUseFrame> frames = BLOCK_USE_FRAMES.get();
		if (frames.isEmpty()) {
			return;
		}

		BlockUseFrame frame = frames.peek();
		if (!frame.matches(player, world, hand, hit)) {
			return;
		}

		int selectedSlot = player.getInventory().getSelectedSlot();
		ItemStack before = player.getItemInHand(hand);
		boolean captured = HotbarRefillPolicy.shouldCapture(
				hand == InteractionHand.MAIN_HAND,
				selectedSlot,
				before.getItem() instanceof BlockItem,
				InventoryActions.isPotion(before),
				before.getCount());
		frame.packetSent = true;
		frame.selectedSlot = selectedSlot;
		frame.wanted = captured ? before.copy() : ItemStack.EMPTY;
		frame.beforeCount = before.getCount();
		frame.captured = captured;
	}

	public static void finishDispatchedBlockUse(
			LocalPlayer player, ClientLevel world, InteractionHand hand, BlockHitResult hit, InteractionResult result) {
		Deque<BlockUseFrame> frames = BLOCK_USE_FRAMES.get();
		while (!frames.isEmpty() && !frames.peek().matches(player, world, hand, hit)) {
			if (frames.peek().packetSent) {
				return;
			}

			frames.pop();
		}

		if (frames.isEmpty()) {
			return;
		}

		BlockUseFrame use = frames.pop();
		if (!use.packetSent) {
			return;
		}

		int selectedSlot = player.getInventory().getSelectedSlot();
		ItemStack after = player.getItemInHand(hand);
		boolean sameInvocation = selectedSlot == use.selectedSlot;
		boolean accepted = result != null && result.consumesAction();
		int afterCount = after.isEmpty() ? 0 : after.getCount();
		if (use.captured && accepted && sameInvocation && use.beforeCount > afterCount) {
			recentPlacedStacks[use.selectedSlot] = use.wanted.copy();
			recentPlacedAt[use.selectedSlot] = System.currentTimeMillis();
		}

		if (HotbarRefillPolicy.shouldArm(use.captured, accepted, sameInvocation, use.beforeCount, afterCount)) {
			pendingDepletion = new PendingDepletion(player, world, use.selectedSlot, use.wanted.copy(), System.currentTimeMillis());
		}
	}

	static void tick(Minecraft client) {
		LocalPlayer player = client == null ? null : client.player;
		ClientLevel world = client == null ? null : client.level;
		BLOCK_USE_FRAMES.get().clear();
		if (player == null || world == null) {
			resetContext(null, null);
			return;
		}

		if (player != trackedPlayer || world != trackedWorld) {
			resetContext(player, world);
		}

		PendingDepletion pending = pendingDepletion;
		if (pending == null) {
			return;
		}

		int selectedSlot = player.getInventory().getSelectedSlot();
		ItemStack held = player.getInventory().getItem(selectedSlot);
		long now = System.currentTimeMillis();
		boolean valid = HotbarRefillPolicy.pendingValid(
				RelayClient.get().config().autoRefill(),
				pending.player == player,
				pending.world == world,
				pending.slot,
				selectedSlot,
				client.screen != null,
				InventoryActions.handlerReady(player),
				held.isEmpty(),
				now - pending.armedAt,
				PENDING_REFILL_TIMEOUT_MS);
		if (!valid) {
			pendingDepletion = null;
			return;
		}

		if (now - lastRestock < RESTOCK_COOLDOWN_MS) {
			return;
		}

		ItemStack wanted = pending.wanted;
		int sourceHotbarSlot = HotbarRefillPolicy.nextMatchingHotbarSource(
				selectedSlot, slot -> isMatchingSource(player.getInventory().getItem(slot), wanted));
		if (sourceHotbarSlot >= 0) {
			pendingDepletion = null;
			player.getInventory().setSelectedSlot(sourceHotbarSlot);
			lastRestock = now;
			PlacementRateLimiter.deferAfterMutation(world);
			return;
		}

		int sourceSlot = HotbarRefillPolicy.firstMatchingMainInventorySource(
				slot -> isMatchingSource(player.getInventory().getItem(slot), wanted));
		if (sourceSlot >= 0 && InventoryActions.selectBuildSlot(player, sourceSlot)) {
			pendingDepletion = null;
			lastRestock = now;
		} else {
			pendingDepletion = null;
		}
	}

	static boolean wasRecentlyUsedBuildStack(ItemStack candidate) {
		if (candidate == null || candidate.isEmpty()) {
			return false;
		}

		long cutoff = System.currentTimeMillis() - RECENT_BUILD_STACK_MS;
		for (int slot = 3; slot < recentPlacedStacks.length; slot++) {
			ItemStack prior = recentPlacedStacks[slot];
			if (recentPlacedAt[slot] >= cutoff
					&& prior != null
					&& !prior.isEmpty()
					&& ItemStack.isSameItemSameComponents(prior, candidate)) {
				return true;
			}
		}

		return false;
	}

	private static void resetContext(LocalPlayer player, ClientLevel world) {
		trackedPlayer = player;
		trackedWorld = world;
		pendingDepletion = null;
		BLOCK_USE_FRAMES.get().clear();
		Arrays.fill(recentPlacedStacks, null);
		Arrays.fill(recentPlacedAt, 0L);
		lastRestock = 0L;
	}

	private static boolean isMatchingSource(ItemStack candidate, ItemStack wanted) {
		return candidate != null
				&& !candidate.isEmpty()
				&& !InventoryActions.isPotion(candidate)
				&& ItemStack.isSameItemSameComponents(wanted, candidate);
	}

	private static final class BlockUseFrame {
		private final LocalPlayer player;
		private final ClientLevel world;
		private final InteractionHand hand;
		private final BlockHitResult hit;
		private boolean packetSent;
		private int selectedSlot = -1;
		private ItemStack wanted = ItemStack.EMPTY;
		private int beforeCount;
		private boolean captured;

		private BlockUseFrame(LocalPlayer player, ClientLevel world, InteractionHand hand, BlockHitResult hit) {
			this.player = player;
			this.world = world;
			this.hand = hand;
			this.hit = hit;
		}

		private boolean matches(LocalPlayer player, ClientLevel world, InteractionHand hand, BlockHitResult hit) {
			return this.player == player && this.world == world && this.hand == hand && this.hit == hit;
		}
	}

	private record PendingDepletion(LocalPlayer player, ClientLevel world, int slot, ItemStack wanted, long armedAt) {
	}
}
