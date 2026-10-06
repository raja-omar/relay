package dev.relay.place;

import java.util.Objects;

import dev.relay.RelayClient;
import dev.relay.chat.ChatMessages;
import dev.relay.litematica.LitematicaIntegration;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ComparatorMode;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Right-click a placed repeater, comparator, or fence gate until it matches the schematic.
 */
public final class RedstoneGateTuner {
	private static final long ACK_TIMEOUT_NANOS = 5_000_000_000L;
	private static final long HINT_INTERVAL_MS = 450L;
	private static final ThreadLocal<Boolean> DISPATCHING = ThreadLocal.withInitial(() -> Boolean.FALSE);
	private static final RedstoneTuningSession<ClientLevel, BlockPos, StaticGateState> SESSION = new RedstoneTuningSession<>();

	private static long nextHintAt;
	private static String lastHint = "";

	private RedstoneGateTuner() {
	}

	static void tick(Minecraft client) {
		ClientLevel world = client == null ? null : client.level;
		boolean usePressed = client != null && client.options != null && client.options.keyUse.isDown();
		SESSION.tick(world, usePressed, System.nanoTime());
	}

	public static void onAuthoritativeBlockUpdate(BlockPos pos, BlockState state) {
		Minecraft client = Minecraft.getInstance();
		SESSION.authoritativeUpdate(client == null ? null : client.level, pos, staticState(state));
	}

	public static InteractionResult interceptDirect(
			MultiPlayerGameMode manager, LocalPlayer player, ClientLevel world, InteractionHand hand, BlockHitResult hit) {
		if (Boolean.TRUE.equals(DISPATCHING.get()) || manager == null || player == null || world == null || hand == null || hit == null) {
			return null;
		}

		BlockPos pos = hit.getBlockPos();
		if (!LitematicaIntegration.isNearSchematic(pos)) {
			return null;
		}

		BlockState wanted = LitematicaIntegration.schematicState(pos).orElse(null);
		BlockState live = world.getBlockState(pos);
		RedstoneTuningPolicy.Decision decision = decision(live, wanted);
		if (decision == RedstoneTuningPolicy.Decision.NOT_TARGET) {
			return null;
		}

		BlockHitResult liveHit = exactLiveCrosshairHit(Minecraft.getInstance(), manager, player, world, pos);
		if (liveHit == null) {
			return InteractionResult.FAIL;
		}

		if (decision == RedstoneTuningPolicy.Decision.WRONG_FACING) {
			hint(player, "Replace this redstone gate; its facing does not match the schematic");
			return InteractionResult.FAIL;
		}

		if (decision != RedstoneTuningPolicy.Decision.ADVANCE) {
			ItemStack held = player.getItemInHand(hand);
			if (held.getItem() instanceof BlockItem) {
				return null;
			}

			return held.isEmpty() ? InteractionResult.SUCCESS : InteractionResult.FAIL;
		}

		if (player.isShiftKeyDown()) {
			hint(player, "Stand up to set this redstone gate before building on it");
			return InteractionResult.FAIL;
		}

		if (unsafeFenceGateOpen(live, wanted, player)) {
			hint(player, "Face another direction before opening this fence gate");
			return InteractionResult.FAIL;
		}

		return dispatchOne(manager, player, world, hand, liveHit, false, live);
	}

	public static InteractionResult interceptEasyPlace(Minecraft client) {
		if (Boolean.TRUE.equals(DISPATCHING.get())
				|| client == null
				|| client.player == null
				|| client.level == null
				|| client.gameMode == null
				|| client.screen != null
				|| !RelayClient.get().config().cantMiss()
				|| client.player.isSpectator()
				|| !client.player.mayBuild()) {
			return null;
		}

		BlockHitResult schematicHit = LitematicaIntegration.easyPlaceSchematicHit(client).orElse(null);
		if (schematicHit == null) {
			return null;
		}

		BlockPos pos = schematicHit.getBlockPos();
		if (!LitematicaIntegration.isNearSchematic(pos)) {
			return null;
		}

		BlockState wanted = LitematicaIntegration.schematicState(pos).orElse(null);
		BlockState live = client.level.getBlockState(pos);
		RedstoneTuningPolicy.Decision decision = decision(live, wanted);
		if (decision == RedstoneTuningPolicy.Decision.NOT_TARGET) {
			return null;
		}

		BlockHitResult liveHit = exactLiveCrosshairHit(client, client.gameMode, client.player, client.level, pos);
		if (liveHit == null) {
			return InteractionResult.FAIL;
		}

		if (decision == RedstoneTuningPolicy.Decision.WRONG_FACING) {
			hint(client.player, "Replace this redstone gate; its facing does not match the schematic");
			return InteractionResult.FAIL;
		}

		if (decision == RedstoneTuningPolicy.Decision.COMPLETE) {
			return InteractionResult.FAIL;
		}

		if (client.player.isShiftKeyDown()) {
			hint(client.player, "Stand up to set this redstone gate before building on it");
			return InteractionResult.FAIL;
		}

		if (unsafeFenceGateOpen(live, wanted, client.player)) {
			hint(client.player, "Face another direction before opening this fence gate");
			return InteractionResult.FAIL;
		}

		return dispatchOne(client.gameMode, client.player, client.level, InteractionHand.MAIN_HAND, liveHit, true, live);
	}

	private static InteractionResult dispatchOne(
			MultiPlayerGameMode manager,
			LocalPlayer player,
			ClientLevel world,
			InteractionHand hand,
			BlockHitResult hit,
			boolean swingHere,
			BlockState live) {
		BlockPos pos = hit.getBlockPos();
		StaticGateState expected = nextStaticState(live);
		if (expected == null || !SESSION.canBegin(world)) {
			return InteractionResult.FAIL;
		}

		if (!FastPlaceCompat.canUseBlockNow() || !PlacementRateLimiter.tryAcquire(world)) {
			return InteractionResult.FAIL;
		}

		DISPATCHING.set(Boolean.TRUE);
		try {
			if (!SESSION.begin(world, pos.immutable(), expected, System.nanoTime() + ACK_TIMEOUT_NANOS)) {
				return InteractionResult.FAIL;
			}

			InteractionResult result = CantMiss.passthrough(() -> manager.useItemOn(player, hand, hit));
			if (result == null || !result.consumesAction()) {
				SESSION.blockPending(world, pos);
			}

			if (swingHere && result instanceof InteractionResult.Success success
					&& success.swingSource() != InteractionResult.SwingSource.NONE) {
				player.swing(hand);
			}

			return result == null ? InteractionResult.FAIL : result;
		} catch (RuntimeException failed) {
			SESSION.blockPending(world, pos);
			hint(player, "Could not configure this redstone gate");
			return InteractionResult.FAIL;
		} finally {
			DISPATCHING.remove();
		}
	}

	private static RedstoneTuningPolicy.Decision decision(BlockState live, BlockState wanted) {
		RedstoneTuningPolicy.GateType liveType = gateType(live);
		RedstoneTuningPolicy.GateType wantedType = gateType(wanted);
		boolean sameBlock = live != null && wanted != null && live.getBlock() == wanted.getBlock();
		boolean facingMatches = live != null
				&& wanted != null
				&& live.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
				&& wanted.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
				&& live.getValue(BlockStateProperties.HORIZONTAL_FACING) == wanted.getValue(BlockStateProperties.HORIZONTAL_FACING);
		return RedstoneTuningPolicy.decide(
				liveType, wantedType, sameBlock, facingMatches, setting(live, liveType), setting(wanted, wantedType));
	}

	private static RedstoneTuningPolicy.GateType gateType(BlockState state) {
		if (state == null) {
			return RedstoneTuningPolicy.GateType.NONE;
		}

		if (state.getBlock() instanceof RepeaterBlock) {
			return RedstoneTuningPolicy.GateType.REPEATER;
		}

		if (state.getBlock() instanceof ComparatorBlock) {
			return RedstoneTuningPolicy.GateType.COMPARATOR;
		}

		return state.getBlock() instanceof FenceGateBlock
				? RedstoneTuningPolicy.GateType.FENCE_GATE
				: RedstoneTuningPolicy.GateType.NONE;
	}

	private static int setting(BlockState state, RedstoneTuningPolicy.GateType type) {
		if (state == null) {
			return -1;
		}

		if (type == RedstoneTuningPolicy.GateType.REPEATER && state.hasProperty(BlockStateProperties.DELAY)) {
			return state.getValue(BlockStateProperties.DELAY);
		}

		if (type == RedstoneTuningPolicy.GateType.COMPARATOR && state.hasProperty(BlockStateProperties.MODE_COMPARATOR)) {
			return state.getValue(BlockStateProperties.MODE_COMPARATOR) == ComparatorMode.COMPARE ? 0 : 1;
		}

		if (type == RedstoneTuningPolicy.GateType.FENCE_GATE && state.hasProperty(BlockStateProperties.OPEN)) {
			return state.getValue(BlockStateProperties.OPEN) ? 1 : 0;
		}

		return -1;
	}

	private static StaticGateState staticState(BlockState state) {
		RedstoneTuningPolicy.GateType type = gateType(state);
		if (type == RedstoneTuningPolicy.GateType.NONE || state == null || !state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
			return null;
		}

		int value = setting(state, type);
		return RedstoneTuningPolicy.nextSetting(type, value) < 0
				? null
				: new StaticGateState(state.getBlock(), type, state.getValue(BlockStateProperties.HORIZONTAL_FACING), value);
	}

	private static StaticGateState nextStaticState(BlockState state) {
		StaticGateState current = staticState(state);
		if (current == null) {
			return null;
		}

		int next = RedstoneTuningPolicy.nextSetting(current.type, current.setting);
		return next < 0 ? null : new StaticGateState(current.block, current.type, current.facing, next);
	}

	private static boolean unsafeFenceGateOpen(BlockState live, BlockState wanted, LocalPlayer player) {
		if (player == null
				|| gateType(live) != RedstoneTuningPolicy.GateType.FENCE_GATE
				|| gateType(wanted) != RedstoneTuningPolicy.GateType.FENCE_GATE
				|| !live.hasProperty(BlockStateProperties.OPEN)
				|| live.getValue(BlockStateProperties.OPEN)
				|| !wanted.hasProperty(BlockStateProperties.OPEN)
				|| !wanted.getValue(BlockStateProperties.OPEN)
				|| !live.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
			return false;
		}

		Direction facing = live.getValue(BlockStateProperties.HORIZONTAL_FACING);
		return facing == player.getDirection().getOpposite();
	}

	private static BlockHitResult exactLiveCrosshairHit(
			Minecraft client, MultiPlayerGameMode manager, LocalPlayer player, ClientLevel world, BlockPos expectedPos) {
		if (client == null
				|| manager == null
				|| player == null
				|| world == null
				|| expectedPos == null
				|| client.player != player
				|| client.level != world
				|| client.gameMode != manager
				|| client.screen != null
				|| manager.isDestroying()
				|| player.isHandsBusy()
				|| player.isSpectator()
				|| !player.mayBuild()
				|| !(client.hitResult instanceof BlockHitResult liveHit)
				|| liveHit.getType() != HitResult.Type.BLOCK
				|| !expectedPos.equals(liveHit.getBlockPos())) {
			return null;
		}

		try {
			double reach = player.blockInteractionRange();
			if (!Double.isFinite(reach) || reach <= 0.0) {
				return null;
			}

			return player.getEyePosition(1.0F).distanceToSqr(liveHit.getLocation()) <= reach * reach + 1.0E-4
					? liveHit
					: null;
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	private static void hint(LocalPlayer player, String message) {
		if (player == null || message == null || message.isBlank()) {
			return;
		}

		long now = System.currentTimeMillis();
		if (message.equals(lastHint) && now < nextHintAt) {
			return;
		}

		lastHint = message;
		nextHintAt = now + HINT_INTERVAL_MS;
		ChatMessages.hint(message);
	}

	private record StaticGateState(Block block, RedstoneTuningPolicy.GateType type, Direction facing, int setting) {
		@Override
		public boolean equals(Object other) {
			return other instanceof StaticGateState state
					&& block == state.block
					&& type == state.type
					&& facing == state.facing
					&& setting == state.setting;
		}

		@Override
		public int hashCode() {
			return Objects.hash(block, type, facing, setting);
		}
	}
}
