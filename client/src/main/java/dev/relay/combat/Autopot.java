package dev.relay.combat;

import java.util.Random;

import dev.relay.RelayClient;
import dev.relay.chat.ChatMessages;
import dev.relay.combat.AutopotPolicy.RefillPlan;
import dev.relay.combat.AutopotPolicy.Steps;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

/**
 * Throws the leftmost Instant Health II splash potion, then selects the slot that was held when
 * the key was pressed. The throw waits until a later tick than the slot change, so the carried-slot
 * packet is already sent before the splash. Every second throw can restock empty hotbar slots from
 * the backpack without moving items that are already there.
 */
public final class Autopot {
	private static final Random RANDOM = new Random();

	private static Sequence active;
	private static boolean armedThisTick;
	private static int throwsSinceRefill;
	private static Refill refill;

	private Autopot() {
	}

	public static void press(Minecraft client) {
		if (active != null || !RelayClient.get().config().autopot()
				|| client.player == null || client.gameMode == null || client.screen != null) {
			return;
		}

		Inventory inventory = client.player.getInventory();
		int potionSlot = findPotionSlot(inventory);
		if (potionSlot < 0) {
			ChatMessages.show(ChatMessages.error("No Instant Health II."));
			return;
		}

		int originalSlot = inventory.getSelectedSlot();
		Steps steps = AutopotPolicy.roll(RANDOM);
		Sequence sequence = new Sequence(originalSlot, potionSlot, steps);
		active = sequence;
		armedThisTick = true;
		if (steps.switchTicks() == 0) {
			switchToPotion(inventory, sequence);
			sequence.phase = Phase.THROW;
		}
	}

	public static void tick(Minecraft client) {
		if (!RelayClient.get().config().autopot()) {
			if (active != null) {
				stop(client, true);
			}
			refill = null;
			throwsSinceRefill = 0;
			return;
		}

		if (active != null) {
			tickSequence(client);
		}
		if (active == null) {
			tickRefill(client);
		}
	}

	public static void clear() {
		active = null;
		armedThisTick = false;
		refill = null;
		throwsSinceRefill = 0;
	}

	private static void tickSequence(Minecraft client) {
		if (armedThisTick) {
			armedThisTick = false;
			return;
		}

		if (client.player == null || client.gameMode == null) {
			clear();
			return;
		}

		if (client.screen != null) {
			stop(client, true);
			return;
		}

		Sequence sequence = active;
		sequence.waited++;
		switch (sequence.phase) {
			case SWITCH -> {
				if (sequence.waited < sequence.steps.switchTicks()) {
					return;
				}
				switchToPotion(client.player.getInventory(), sequence);
				sequence.phase = Phase.THROW;
				sequence.waited = 0;
			}
			case THROW -> {
				if (sequence.waited < sequence.steps.throwTicks()) {
					return;
				}
				Inventory inventory = client.player.getInventory();
				boolean stillSelected = inventory.getSelectedSlot() == sequence.potionSlot;
				if (!stillSelected || !isInstantHealthTwoSplash(inventory.getItem(sequence.potionSlot))) {
					stop(client, stillSelected);
					return;
				}
				client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
				noteThrown();
				if (sequence.potionSlot == sequence.originalSlot) {
					endSequence();
					return;
				}
				sequence.phase = Phase.RESTORE;
				sequence.waited = 0;
			}
			case RESTORE -> {
				if (sequence.waited < sequence.steps.restoreTicks()) {
					return;
				}
				client.player.getInventory().setSelectedSlot(sequence.originalSlot);
				endSequence();
			}
		}
	}

	private static void tickRefill(Minecraft client) {
		if (refill == null) {
			return;
		}

		if (!RelayClient.get().config().autopotRefill()) {
			refill = null;
			throwsSinceRefill = 0;
			return;
		}

		if (client.player == null || client.gameMode == null) {
			clear();
			return;
		}

		if (client.screen != null || !inventoryReady(client.player)) {
			return;
		}

		if (refill.skipThisTick) {
			refill.skipThisTick = false;
			return;
		}

		refill.waited++;
		if (refill.waited < refill.delay) {
			return;
		}

		Inventory inventory = client.player.getInventory();
		int destination = AutopotPolicy.firstEmptyHotbar(emptyHotbar(inventory));
		int source = AutopotPolicy.firstInventoryPotion(inventoryPotions(inventory));
		if (destination < 0 || source < 0) {
			finishRefill();
			return;
		}

		client.gameMode.handleInventoryMouseClick(
				client.player.inventoryMenu.containerId, source, destination, ClickType.SWAP, client.player);
		refill.remaining--;
		if (refill.remaining <= 0) {
			finishRefill();
			return;
		}
		refill.waited = 0;
		refill.delay = refill.gapTicks;
	}

	private static void noteThrown() {
		if (!RelayClient.get().config().autopotRefill()) {
			return;
		}
		throwsSinceRefill++;
		queueRefill();
	}

	private static void queueRefill() {
		if (refill != null || !RelayClient.get().config().autopotRefill()
				|| !AutopotPolicy.refillDue(throwsSinceRefill)) {
			return;
		}

		throwsSinceRefill -= 2;
		refill = new Refill(AutopotPolicy.rollRefill(RANDOM));
	}

	private static void finishRefill() {
		refill = null;
		queueRefill();
	}

	private static void endSequence() {
		active = null;
		armedThisTick = false;
	}

	private static void switchToPotion(Inventory inventory, Sequence sequence) {
		if (sequence.potionSlot == sequence.originalSlot) {
			return;
		}
		inventory.setSelectedSlot(sequence.potionSlot);
		sequence.switched = true;
	}

	private static void stop(Minecraft client, boolean restore) {
		Sequence sequence = active;
		endSequence();
		if (restore && sequence != null && sequence.switched && client.player != null) {
			client.player.getInventory().setSelectedSlot(sequence.originalSlot);
		}
	}

	private static boolean inventoryReady(LocalPlayer player) {
		return player.containerMenu == player.inventoryMenu && player.containerMenu.getCarried().isEmpty();
	}

	private static boolean[] emptyHotbar(Inventory inventory) {
		boolean[] empty = new boolean[AutopotPolicy.HOTBAR_SIZE];
		for (int slot = 0; slot < empty.length; slot++) {
			empty[slot] = inventory.getItem(slot).isEmpty();
		}
		return empty;
	}

	private static boolean[] inventoryPotions(Inventory inventory) {
		boolean[] potions = new boolean[AutopotPolicy.INVENTORY_END + 1];
		for (int slot = AutopotPolicy.INVENTORY_START; slot <= AutopotPolicy.INVENTORY_END; slot++) {
			potions[slot] = isInstantHealthTwoSplash(inventory.getItem(slot));
		}
		return potions;
	}

	private static int findPotionSlot(Inventory inventory) {
		boolean[] matches = new boolean[AutopotPolicy.HOTBAR_SIZE];
		for (int slot = 0; slot < matches.length; slot++) {
			matches[slot] = isInstantHealthTwoSplash(inventory.getItem(slot));
		}
		return AutopotPolicy.firstSlot(matches);
	}

	private static boolean isInstantHealthTwoSplash(ItemStack stack) {
		if (stack == null || stack.isEmpty() || !stack.is(Items.SPLASH_POTION)) {
			return false;
		}

		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		if (contents == null) {
			return false;
		}

		if (contents.is(Potions.STRONG_HEALING)) {
			return true;
		}

		for (MobEffectInstance effect : contents.getAllEffects()) {
			if (effect.is(MobEffects.INSTANT_HEALTH) && effect.getAmplifier() >= 1) {
				return true;
			}
		}
		return false;
	}

	private enum Phase {
		SWITCH, THROW, RESTORE
	}

	private static final class Sequence {
		private final int originalSlot;
		private final int potionSlot;
		private final Steps steps;
		private Phase phase = Phase.SWITCH;
		private int waited;
		private boolean switched;

		private Sequence(int originalSlot, int potionSlot, Steps steps) {
			this.originalSlot = originalSlot;
			this.potionSlot = potionSlot;
			this.steps = steps;
		}
	}

	private static final class Refill {
		private int remaining;
		private int waited;
		private int delay;
		private final int gapTicks;
		private boolean skipThisTick = true;

		private Refill(RefillPlan plan) {
			this.remaining = plan.count();
			this.delay = plan.delayTicks();
			this.gapTicks = plan.gapTicks();
		}
	}
}
