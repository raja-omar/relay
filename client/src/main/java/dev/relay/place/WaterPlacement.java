package dev.relay.place;

import dev.relay.RelayClient;
import dev.relay.chat.ChatMessages;
import dev.relay.litematica.LitematicaIntegration;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import fi.dy.masa.litematica.world.WorldSchematic;

/**
 * Crouch + right-click places schematic source water with a water bucket.
 */
final class WaterPlacement {
	private static final long WATER_REQUEST_TIMEOUT_MS = 5000L;
	private static final long WATER_RETRY_DELAY_MS = 200L;
	private static final long WATER_DISPATCH_COOLDOWN_MS = 500L;
	private static final long HINT_REPEAT_MS = 450L;

	private static Cycle cycle = Cycle.IDLE;
	private static ClientLevel requestWorld;
	private static BlockPos requestTarget;
	private static long requestExpiresAt;
	private static long nextAttemptAt;
	private static long lastDispatchAt;
	private static boolean dispatching;
	private static long nextHintAt;
	private static String lastHint = "";

	private WaterPlacement() {
	}

	static boolean isDispatching() {
		return dispatching;
	}

	static boolean interceptCrouchWaterUse(Minecraft client) {
		if (client == null || client.options == null || !client.options.keyUse.isDown()) {
			reset();
			return false;
		}

		if (cycle == Cycle.NON_WATER) {
			return false;
		}

		if (cycle == Cycle.WATER_CONSUMED) {
			if (client.options.keyShift.isDown() && validContext(client)) {
				return true;
			}

			demote();
			return false;
		}

		if (cycle == Cycle.IDLE) {
			if (!client.options.keyShift.isDown() || !validContext(client)) {
				demote();
				return false;
			}

			WorldSchematic schematicWorld = LitematicaIntegration.schematicWorld().orElse(null);
			WaterTarget lookedAt = schematicWorld == null
					? null
					: findLookedAtSourceWater(client.player, client.level, schematicWorld, true);
			if (lookedAt == null) {
				demote();
				return false;
			}

			EmptyBucketDrop.cancelPending();
			if (lookedAt.sourcePresent) {
				complete();
				return true;
			}

			long now = System.currentTimeMillis();
			cycle = Cycle.WATER_PENDING;
			requestWorld = client.level;
			requestTarget = lookedAt.pos.immutable();
			requestExpiresAt = now + WATER_REQUEST_TIMEOUT_MS;
			nextAttemptAt = 0L;
		}

		if (!client.options.keyShift.isDown()) {
			demote();
			return false;
		}

		if (validContext(client) && requestWorld == client.level && System.currentTimeMillis() <= requestExpiresAt) {
			long now = System.currentTimeMillis();
			if (now >= nextAttemptAt) {
				attemptRequested(client, now);
			}

			return true;
		}

		demote();
		return false;
	}

	static void tick(Minecraft client) {
		if (cycle == Cycle.IDLE) {
			return;
		}

		if (client == null || client.options == null || !client.options.keyUse.isDown()) {
			reset();
			return;
		}

		if ((cycle == Cycle.WATER_PENDING || cycle == Cycle.WATER_CONSUMED)
				&& !(client.options.keyShift.isDown() && validContext(client))) {
			demote();
			return;
		}

		if (cycle == Cycle.WATER_PENDING
				&& (requestWorld != client.level || System.currentTimeMillis() > requestExpiresAt)) {
			demote();
		}
	}

	static InteractionResult interceptItemUse(LocalPlayer player, InteractionHand hand) {
		if (!RelayClient.get().config().cantMiss() || dispatching || player == null || hand == null) {
			return null;
		}

		ItemStack held = player.getItemInHand(hand);
		return WaterBuckets.isUsable(held) || held.is(Items.WATER_BUCKET) ? InteractionResult.FAIL : null;
	}

	private static boolean validContext(Minecraft client) {
		return client.player != null
				&& client.level != null
				&& client.gameMode != null
				&& client.screen == null
				&& RelayClient.get().config().cantMiss()
				&& LitematicaIntegration.isAvailable()
				&& !client.gameMode.isDestroying()
				&& !client.player.isHandsBusy()
				&& !client.player.isSpectator()
				&& !(client.hitResult instanceof EntityHitResult)
				&& !CantMiss.isPassthrough();
	}

	private static void attemptRequested(Minecraft client, long now) {
		if (requestTarget == null || requestWorld != client.level) {
			demote();
			return;
		}

		LocalPlayer player = client.player;
		ClientLevel world = client.level;
		if (isSourceWater(world.getFluidState(requestTarget))) {
			complete();
			return;
		}

		WorldSchematic schematicWorld = LitematicaIntegration.schematicWorld().orElse(null);
		if (schematicWorld == null) {
			demote();
			return;
		}

		WaterTarget lookedAt = findLookedAtSourceWater(player, world, schematicWorld, false);
		if (lookedAt == null || !requestTarget.equals(lookedAt.pos)) {
			hint(player, "Keep aiming at the selected schematic water source");
			delay(now);
			return;
		}

		ItemStack mainHand = player.getMainHandItem();
		if (!WaterBuckets.isUsable(mainHand)) {
			boolean alreadyOwned = findUsableWaterBucketSlot(player) >= 0;
			boolean buying = resolveWaterBucket(player, lookedAt.wanted);
			mainHand = player.getMainHandItem();
			if (WaterBuckets.isUsable(mainHand)) {
				hint(player, "Water Bucket ready — keep holding crouch and right-click");
			} else if (alreadyOwned) {
				hint(player, "Selecting a Water Bucket");
			} else {
				hint(player, buying ? "Buying a Water Bucket" : "Inventory full — no safe building stack to drop");
			}

			delay(now);
			return;
		}

		BlockHitResult liveBlockHit = client.hitResult instanceof BlockHitResult blockHit
				&& blockHit.getType() == HitResult.Type.BLOCK
				? blockHit
				: null;
		if (liveBlockHit == null) {
			hint(player, "Aim at a solid face directly beside the highlighted water source");
			delay(now);
			return;
		}

		BlockPos bucketTarget = bucketTarget(player, world, liveBlockHit);
		WaterTarget exact = bucketTarget.equals(lookedAt.pos)
				? stagedSourceWaterTargetAt(schematicWorld, world, bucketTarget, liveBlockHit)
				: null;
		if (exact == null || !exact.pos.equals(lookedAt.pos)) {
			hint(player, "Aim at a solid face directly beside the highlighted water source");
			delay(now);
			return;
		}

		if (now - lastDispatchAt < WATER_DISPATCH_COOLDOWN_MS
				|| !player.isShiftKeyDown()
				|| !FastPlaceCompat.canUseBlockNow()
				|| !PlacementRateLimiter.tryAcquire(world)) {
			delay(now);
			return;
		}

		dispatching = true;
		try {
			complete();
			lastDispatchAt = now;
			InteractionResult blockResult = CantMiss.passthrough(
					() -> client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, liveBlockHit));
			InteractionResult itemResult = null;
			if (blockResult == InteractionResult.SUCCESS) {
				itemResult = client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
			}

			InteractionResult swing = blockResult != null && blockResult.consumesAction() ? blockResult : itemResult;
			if (swing instanceof InteractionResult.Success success
					&& success.swingSource() != InteractionResult.SwingSource.NONE) {
				player.swing(InteractionHand.MAIN_HAND);
			}
		} catch (RuntimeException failed) {
			hint(player, "Water placement failed");
		} finally {
			dispatching = false;
		}
	}

	private static boolean resolveWaterBucket(LocalPlayer player, BlockState wanted) {
		int slot = findUsableWaterBucketSlot(player);
		if (slot >= 0) {
			return InventoryActions.selectBuildSlot(player, slot);
		}

		return ShopPurchases.tryBuyWaterBucket(player, wanted);
	}

	private static int findUsableWaterBucketSlot(LocalPlayer player) {
		var inventory = player.getInventory();
		int vanilla = -1;
		for (int slot = 3; slot <= 35; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (WaterBuckets.isNamedInfinite(stack)) {
				return slot;
			}

			if (vanilla < 0 && WaterBuckets.isVanilla(stack)) {
				vanilla = slot;
			}
		}

		return vanilla;
	}

	private static WaterTarget findLookedAtSourceWater(
			LocalPlayer player, ClientLevel world, WorldSchematic schematicWorld, boolean includePresentSource) {
		Vec3 start = player.getEyePosition(1.0F);
		Vec3 direction = player.getViewVector(1.0F);
		double reach = Math.max(1.0, player.blockInteractionRange());
		Vec3 end = start.add(direction.scale(reach));

		BlockHitResult liveFluidHit = world.clip(new ClipContext(
				start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY, player));
		if (liveFluidHit.getType() == HitResult.Type.BLOCK && isWater(world.getFluidState(liveFluidHit.getBlockPos()))) {
			WaterTarget liveTarget = includePresentSource
					? stagedWaterIntentAt(schematicWorld, world, liveFluidHit.getBlockPos(), null)
					: stagedSourceWaterTargetAt(schematicWorld, world, liveFluidHit.getBlockPos(), null);
			if (liveTarget != null) {
				return liveTarget;
			}
		}

		BlockHitResult liveBlockHit = raycastBlockOnly(player, world, start, end);
		Vec3 schematicEnd = liveBlockHit == null ? end : liveBlockHit.getLocation().add(direction.scale(0.01));
		BlockHitResult schematicFluidHit = schematicWorld.clip(new ClipContext(
				start, schematicEnd, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, player));
		if (schematicFluidHit.getType() != HitResult.Type.BLOCK) {
			return null;
		}

		return includePresentSource
				? stagedWaterIntentAt(schematicWorld, world, schematicFluidHit.getBlockPos(), null)
				: stagedSourceWaterTargetAt(schematicWorld, world, schematicFluidHit.getBlockPos(), null);
	}

	private static BlockHitResult raycastBlockOnly(LocalPlayer player, ClientLevel world, Vec3 start, Vec3 end) {
		BlockHitResult hit = world.clip(new ClipContext(
				start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.BLOCK ? hit : null;
	}

	private static BlockPos bucketTarget(LocalPlayer player, ClientLevel world, BlockHitResult blockHit) {
		BlockPos hitPos = blockHit.getBlockPos();
		BlockState hitState = world.getBlockState(hitPos);
		if (hitState.getBlock() instanceof LiquidBlockContainer fillable
				&& fillable.canPlaceLiquid(player, world, hitPos, hitState, Fluids.WATER)) {
			return hitPos;
		}

		return hitPos.relative(blockHit.getDirection());
	}

	private static WaterTarget stagedSourceWaterTargetAt(
			WorldSchematic schematicWorld, ClientLevel world, BlockPos pos, BlockHitResult replayHit) {
		BlockState wanted = schematicWorld.getBlockState(pos);
		return isSourceWater(wanted.getFluidState()) ? sourceWaterTargetAt(schematicWorld, world, pos, replayHit) : null;
	}

	private static WaterTarget stagedWaterIntentAt(
			WorldSchematic schematicWorld, ClientLevel world, BlockPos pos, BlockHitResult replayHit) {
		if (!LitematicaIntegration.isNearSchematic(pos)) {
			return null;
		}

		BlockState wanted = schematicWorld.getBlockState(pos);
		if (!isSourceWater(wanted.getFluidState())) {
			return null;
		}

		return isSourceWater(world.getFluidState(pos))
				? new WaterTarget(pos, wanted, replayHit, true)
				: sourceWaterTargetAt(schematicWorld, world, pos, replayHit);
	}

	private static WaterTarget sourceWaterTargetAt(
			WorldSchematic schematicWorld, ClientLevel world, BlockPos pos, BlockHitResult replayHit) {
		if (!LitematicaIntegration.isNearSchematic(pos)) {
			return null;
		}

		BlockState wanted = schematicWorld.getBlockState(pos);
		if (!isSourceWater(wanted.getFluidState())) {
			return null;
		}

		FluidState actualFluid = world.getFluidState(pos);
		if (isSourceWater(actualFluid)) {
			return null;
		}

		BlockState actual = world.getBlockState(pos);
		if (wanted.is(Blocks.WATER)) {
			return (actualFluid.isEmpty() || isWater(actualFluid))
					&& (actual.isAir() || actual.canBeReplaced(Fluids.WATER))
					? new WaterTarget(pos, wanted, replayHit, false)
					: null;
		}

		if (wanted.hasProperty(BlockStateProperties.WATERLOGGED)
				&& Boolean.TRUE.equals(wanted.getValue(BlockStateProperties.WATERLOGGED))
				&& wanted.getBlock() instanceof LiquidBlockContainer) {
			if (actual.hasProperty(BlockStateProperties.WATERLOGGED)
					&& Boolean.FALSE.equals(actual.getValue(BlockStateProperties.WATERLOGGED))
					&& actual.getBlock() instanceof LiquidBlockContainer fillable
					&& fillable.canPlaceLiquid(null, world, pos, actual, Fluids.WATER)) {
				try {
					if (actual.setValue(BlockStateProperties.WATERLOGGED, true).equals(wanted)) {
						return new WaterTarget(pos, wanted, replayHit, false);
					}
				} catch (RuntimeException ignored) {
				}
			}

			if (actual.isAir() || (actual.is(Blocks.WATER) && isWater(actualFluid) && !actualFluid.isSource())) {
				return new WaterTarget(pos, wanted, replayHit, false);
			}
		}

		return null;
	}

	private static boolean isWater(FluidState state) {
		return state != null && state.is(FluidTags.WATER);
	}

	private static boolean isSourceWater(FluidState state) {
		return isWater(state) && state.isSource();
	}

	private static void hint(LocalPlayer player, String text) {
		if (player == null || text == null || text.isBlank()) {
			return;
		}

		long now = System.currentTimeMillis();
		if (text.equals(lastHint) && now < nextHintAt) {
			return;
		}

		lastHint = text;
		nextHintAt = now + HINT_REPEAT_MS;
		ChatMessages.hint(text);
	}

	private static void delay(long now) {
		nextAttemptAt = now + WATER_RETRY_DELAY_MS;
	}

	private static void clearTarget() {
		requestWorld = null;
		requestTarget = null;
		requestExpiresAt = 0L;
		nextAttemptAt = 0L;
		dispatching = false;
	}

	private static void reset() {
		clearTarget();
		cycle = Cycle.IDLE;
	}

	private static void demote() {
		clearTarget();
		cycle = Cycle.NON_WATER;
	}

	private static void complete() {
		clearTarget();
		cycle = Cycle.WATER_CONSUMED;
	}

	private enum Cycle {
		IDLE,
		NON_WATER,
		WATER_PENDING,
		WATER_CONSUMED
	}

	private record WaterTarget(BlockPos pos, BlockState wanted, BlockHitResult replayHit, boolean sourcePresent) {
	}
}
