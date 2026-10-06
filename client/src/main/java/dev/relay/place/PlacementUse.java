package dev.relay.place;

import java.util.function.BooleanSupplier;

import dev.relay.litematica.LitematicaIntegration;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.BlockHitResult;

/**
 * One schematic block click: wait for FastPlace, skip the addon CPS cap, sneak on interactive
 * supports, and optionally send a one-tick look for observers and pistons.
 */
final class PlacementUse {
	private static final ThreadLocal<Integer> DISPATCH_DEPTH = ThreadLocal.withInitial(() -> 0);

	private static LocalPlayer pendingRestorePlayer;
	private static float pendingRestoreYaw;
	private static float pendingRestorePitch;
	private static LocalPlayer pendingSneakRestorePlayer;
	private static Input pendingSneakRestoreInput;
	private static boolean sneakRestoreArmedThisTick;
	private static boolean lookRestoreArmedThisTick;
	private static boolean sneakArmedForCurrentUse;
	private static Input armedSneakPreviousInput;

	private PlacementUse() {
	}

	static InteractionResult interactBlock(
			MultiPlayerGameMode manager,
			LocalPlayer player,
			InteractionHand hand,
			BlockHitResult hit,
			boolean sneakIfInteractive) {
		int outerDepth = DISPATCH_DEPTH.get();
		if (outerDepth > 0) {
			return manager.useItemOn(player, hand, hit);
		}

		if (!flushPendingRestore(player) || !flushPendingSneakRestore(player)) {
			return InteractionResult.FAIL;
		}

		if (!FastPlaceCompat.canUseBlockNow()) {
			return InteractionResult.FAIL;
		}

		ClientLevel world = Minecraft.getInstance().level;
		if (!PlacementRateLimiter.isReadyAfterMutation(world)) {
			return InteractionResult.FAIL;
		}

		Input previousInput = player.input == null ? null : player.input.keyPresses;
		boolean autoSneak = sneakIfInteractive && needsSneak(player, world, hit);
		DISPATCH_DEPTH.set(outerDepth + 1);
		try {
			if (autoSneak) {
				applySneak(player, true);
			}

			return manager.useItemOn(player, hand, hit);
		} finally {
			if (autoSneak) {
				restoreSneak(player, previousInput);
			}

			if (outerDepth == 0) {
				DISPATCH_DEPTH.remove();
			} else {
				DISPATCH_DEPTH.set(outerDepth);
			}
		}
	}

	static InteractionResult interactBlockWithTemporaryPitch(
			MultiPlayerGameMode manager,
			LocalPlayer player,
			InteractionHand hand,
			BlockHitResult hit,
			float candidatePitch,
			BooleanSupplier finalValidation) {
		if (manager == null
				|| player == null
				|| hand == null
				|| hit == null
				|| finalValidation == null
				|| !Float.isFinite(candidatePitch)
				|| candidatePitch < -90.0F
				|| candidatePitch > 90.0F) {
			return InteractionResult.FAIL;
		}

		int outerDepth = DISPATCH_DEPTH.get();
		if (outerDepth > 0) {
			return InteractionResult.FAIL;
		}

		if (!flushPendingRestore(player) || !flushPendingSneakRestore(player)) {
			return InteractionResult.FAIL;
		}

		if (!FastPlaceCompat.canUseBlockNow()) {
			return InteractionResult.FAIL;
		}

		ClientLevel world = Minecraft.getInstance().level;
		if (!PlacementRateLimiter.isReadyAfterMutation(world)) {
			return InteractionResult.FAIL;
		}

		float originalYaw = player.getYRot();
		float originalPitch = player.getXRot();
		Input previousInput = player.input == null ? null : player.input.keyPresses;
		boolean autoSneak = needsSneak(player, world, hit);
		boolean restoreRequired = false;
		DISPATCH_DEPTH.set(outerDepth + 1);
		try {
			player.setYRot(originalYaw);
			player.setXRot(candidatePitch);
			if (!finalValidation.getAsBoolean()) {
				return InteractionResult.FAIL;
			}

			restoreRequired = true;
			pendingRestorePlayer = player;
			pendingRestoreYaw = originalYaw;
			pendingRestorePitch = originalPitch;
			lookRestoreArmedThisTick = true;
			player.connection.send(new ServerboundMovePlayerPacket.Rot(
					originalYaw, candidatePitch, player.onGround(), player.horizontalCollision));
			if (autoSneak) {
				applySneak(player, true);
			}

			return manager.useItemOn(player, hand, hit);
		} catch (RuntimeException failed) {
			return InteractionResult.FAIL;
		} finally {
			if (autoSneak) {
				restoreSneak(player, previousInput);
			}

			if (!restoreRequired) {
				player.setYRot(originalYaw);
				player.setXRot(originalPitch);
				if (pendingRestorePlayer == player) {
					pendingRestorePlayer = null;
				}
			}

			if (outerDepth == 0) {
				DISPATCH_DEPTH.remove();
			} else {
				DISPATCH_DEPTH.set(outerDepth);
			}
		}
	}

	static void tick(Minecraft client) {
		LocalPlayer player = client == null ? null : client.player;
		if (pendingRestorePlayer != null) {
			if (player != pendingRestorePlayer) {
				pendingRestorePlayer = null;
			} else if (!lookRestoreArmedThisTick) {
				flushPendingRestore(pendingRestorePlayer);
			}
		}

		if (pendingSneakRestorePlayer != null) {
			if (player != pendingSneakRestorePlayer) {
				pendingSneakRestorePlayer = null;
				pendingSneakRestoreInput = null;
			} else if (!sneakRestoreArmedThisTick) {
				flushPendingSneakRestore(pendingSneakRestorePlayer);
			}
		}

		sneakRestoreArmedThisTick = false;
		lookRestoreArmedThisTick = false;
	}

	static void armSneakForCurrentUse(LocalPlayer player) {
		if (player == null || sneakArmedForCurrentUse || player.isShiftKeyDown()) {
			return;
		}

		armedSneakPreviousInput = player.input == null ? null : player.input.keyPresses;
		sneakArmedForCurrentUse = true;
		applySneak(player, true);
	}

	static void releaseSneakForCurrentUse(LocalPlayer player) {
		if (player == null || !sneakArmedForCurrentUse) {
			return;
		}

		Input previous = armedSneakPreviousInput;
		sneakArmedForCurrentUse = false;
		armedSneakPreviousInput = null;
		restoreSneak(player, previous);
	}

	static boolean needsSneak(LocalPlayer player, ClientLevel world, BlockHitResult hit) {
		if (player == null || world == null || hit == null || player.isShiftKeyDown()) {
			return false;
		}

		return PlacementSupports.needsSneakToPlaceOn(world.getBlockState(hit.getBlockPos()));
	}

	private static boolean flushPendingRestore(LocalPlayer player) {
		if (pendingRestorePlayer == null) {
			return true;
		}

		if (pendingRestorePlayer != player) {
			return false;
		}

		try {
			player.setYRot(pendingRestoreYaw);
			player.setXRot(pendingRestorePitch);
			player.connection.send(new ServerboundMovePlayerPacket.Rot(
					pendingRestoreYaw, pendingRestorePitch, player.onGround(), player.horizontalCollision));
			pendingRestorePlayer = null;
			return true;
		} catch (RuntimeException ignored) {
			return false;
		}
	}

	private static boolean flushPendingSneakRestore(LocalPlayer player) {
		if (pendingSneakRestorePlayer == null) {
			return true;
		}

		if (pendingSneakRestorePlayer != player) {
			return false;
		}

		try {
			Input previous = pendingSneakRestoreInput;
			if (previous != null && player.input != null) {
				player.input.keyPresses = previous;
				player.connection.send(new ServerboundPlayerInputPacket(previous));
			}

			player.setShiftKeyDown(previous != null && previous.shift());
			LitematicaIntegration.setFakedSneaking(false);
			pendingSneakRestorePlayer = null;
			pendingSneakRestoreInput = null;
			sneakArmedForCurrentUse = false;
			armedSneakPreviousInput = null;
			return true;
		} catch (RuntimeException ignored) {
			return false;
		}
	}

	private static void applySneak(LocalPlayer player, boolean sneaking) {
		if (player.input != null) {
			Input previous = player.input.keyPresses;
			player.input.keyPresses = new Input(
					previous.forward(),
					previous.backward(),
					previous.left(),
					previous.right(),
					previous.jump(),
					sneaking,
					previous.sprint());
			player.connection.send(new ServerboundPlayerInputPacket(player.input.keyPresses));
		}

		player.setShiftKeyDown(sneaking);
		LitematicaIntegration.setFakedSneaking(sneaking);
	}

	private static void restoreSneak(LocalPlayer player, Input previous) {
		pendingSneakRestorePlayer = player;
		pendingSneakRestoreInput = previous;
		sneakRestoreArmedThisTick = true;
	}
}
