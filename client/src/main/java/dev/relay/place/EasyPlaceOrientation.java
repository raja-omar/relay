package dev.relay.place;

import java.util.ArrayList;
import java.util.List;

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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Chooses a click that actually predicts the schematic block, then places through
 * {@link PlacementUse}. Failed clicks uncache so the next held tick can retry immediately.
 */
final class EasyPlaceOrientation {
	private EasyPlaceOrientation() {
	}

	static InteractionResult interact(
			MultiPlayerGameMode manager, LocalPlayer player, InteractionHand hand, BlockHitResult hit) {
		if (manager == null || player == null || hand == null || hit == null) {
			return InteractionResult.FAIL;
		}

		if (!(player.getItemInHand(hand).getItem() instanceof BlockItem)) {
			return PlacementUse.interactBlock(manager, player, hand, hit, true);
		}

		ClientLevel world = Minecraft.getInstance().level;
		if (world == null) {
			return InteractionResult.FAIL;
		}

		boolean applyProtocol = LitematicaIntegration.accuratePlacementProtocolIsLive();
		CantMiss.Decision probe = CantMiss.evaluateEasyPlace(player, world, hand, hit, applyProtocol);
		if (probe.kind() == CantMiss.Kind.ALLOWED && isReachableAndVisible(player, world, hit)) {
			return PlacementUse.interactBlock(manager, player, hand, hit, true);
		}

		BlockHitResult solution = findClosestSolution(player, world, hand, hit, applyProtocol);
		if (solution != null) {
			return PlacementUse.interactBlock(manager, player, hand, solution, true);
		}

		if (!applyProtocol && probe.facingOnly() && probe.directionOnly()) {
			Float pitch = EasyPlaceHits.assistPitch(wantedFacing(probe.wanted()), player.getXRot());
			if (pitch != null && isPitchAssistBlock(probe.wanted())) {
				return PlacementUse.interactBlockWithTemporaryPitch(
						manager,
						player,
						hand,
						hit,
						pitch,
						() -> finalPitchValidation(player, world, hand, hit, applyProtocol));
			}
		}

		if (probe.kind() == CantMiss.Kind.BLOCKED && probe.reason() != null && !probe.reason().isBlank()) {
			CantMiss.showHint(player, probe.reason());
		}

		return InteractionResult.FAIL;
	}

	private static BlockHitResult findClosestSolution(
			LocalPlayer player,
			ClientLevel world,
			InteractionHand hand,
			BlockHitResult originalHit,
			boolean applyProtocol) {
		List<BlockHitResult> candidates = new ArrayList<>(16);
		BlockPos target = originalHit.getBlockPos();
		for (Direction side : Direction.values()) {
			addIfUsable(candidates, player, world, hand, EasyPlaceHits.faceHit(target, side, 0.5, 0.5), applyProtocol);
		}

		double[] quarters = {0.25, 0.75};
		for (Direction side : new Direction[] {Direction.UP, Direction.DOWN}) {
			for (double first : quarters) {
				for (double second : quarters) {
					addIfUsable(
							candidates,
							player,
							world,
							hand,
							EasyPlaceHits.faceHit(target, side, first, second),
							applyProtocol);
				}
			}
		}

		return candidates.isEmpty() ? null : candidates.get(0);
	}

	private static void addIfUsable(
			List<BlockHitResult> candidates,
			LocalPlayer player,
			ClientLevel world,
			InteractionHand hand,
			BlockHitResult hit,
			boolean applyProtocol) {
		if (!isReachableAndVisible(player, world, hit)) {
			return;
		}

		CantMiss.Decision evaluation = CantMiss.evaluateEasyPlace(player, world, hand, hit, applyProtocol);
		if (evaluation.kind() == CantMiss.Kind.ALLOWED) {
			candidates.add(hit);
		}
	}

	private static boolean finalPitchValidation(
			LocalPlayer player,
			ClientLevel world,
			InteractionHand hand,
			BlockHitResult hit,
			boolean applyProtocol) {
		if (!isReachableAndVisible(player, world, hit)) {
			CantMiss.showHint(player, "Easy Place target moved outside normal reach or line of sight");
			return false;
		}

		CantMiss.Decision evaluation = CantMiss.evaluateEasyPlace(player, world, hand, hit, applyProtocol);
		if (evaluation.kind() == CantMiss.Kind.ALLOWED) {
			return true;
		}

		CantMiss.showHint(player, evaluation.reason());
		return false;
	}

	private static boolean isReachableAndVisible(LocalPlayer player, ClientLevel world, BlockHitResult hit) {
		try {
			Vec3 eye = player.getEyePosition(1.0F);
			Vec3 target = hit.getLocation();
			Vec3 delta = target.subtract(eye);
			double distance = delta.length();
			double reach = Math.max(1.0, player.blockInteractionRange());
			if (!EasyPlaceHits.withinReach(distance, reach)) {
				return false;
			}

			if (distance < 1.0E-7) {
				return true;
			}

			Vec3 end = target.add(delta.scale(0.01 / distance));
			BlockHitResult obstruction = world.clip(
					new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
			boolean isBlock = obstruction.getType() == HitResult.Type.BLOCK;
			double obstructionDistance = isBlock
					? eye.distanceTo(obstruction.getLocation())
					: Double.POSITIVE_INFINITY;
			return EasyPlaceHits.visible(distance, obstructionDistance, isBlock);
		} catch (RuntimeException ignored) {
			return false;
		}
	}

	private static boolean isPitchAssistBlock(BlockState wanted) {
		return wanted != null
				&& (wanted.is(Blocks.OBSERVER)
						|| wanted.getBlock() instanceof ObserverBlock
						|| wanted.getBlock() instanceof DispenserBlock
						|| wanted.getBlock() instanceof PistonBaseBlock);
	}

	private static Direction wantedFacing(BlockState wanted) {
		if (wanted == null) {
			return null;
		}

		if (wanted.hasProperty(BlockStateProperties.FACING)) {
			return wanted.getValue(BlockStateProperties.FACING);
		}

		return null;
	}
}
