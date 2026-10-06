package dev.relay.place;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Pure Easy Place click geometry: which face to hit, whether that ghost is actually visible,
 * and the one pitch tweak observers and pistons need.
 */
final class EasyPlaceHits {
	private EasyPlaceHits() {
	}

	static BlockHitResult faceHit(BlockPos target, Direction side, double first, double second) {
		double x = target.getX() + 0.5;
		double y = target.getY() + 0.5;
		double z = target.getZ() + 0.5;
		switch (side) {
			case DOWN -> {
				x = target.getX() + first;
				y = target.getY();
				z = target.getZ() + second;
			}
			case UP -> {
				x = target.getX() + first;
				y = target.getY() + 1.0;
				z = target.getZ() + second;
			}
			case NORTH -> {
				x = target.getX() + first;
				y = target.getY() + second;
				z = target.getZ();
			}
			case SOUTH -> {
				x = target.getX() + first;
				y = target.getY() + second;
				z = target.getZ() + 1.0;
			}
			case WEST -> {
				x = target.getX();
				y = target.getY() + second;
				z = target.getZ() + first;
			}
			case EAST -> {
				x = target.getX() + 1.0;
				y = target.getY() + second;
				z = target.getZ() + first;
			}
		}

		return new BlockHitResult(new Vec3(x, y, z), side, target, false);
	}

	static boolean withinReach(double distance, double reach) {
		return distance <= reach + 1.0E-4;
	}

	/**
	 * A miss, or a collision at/behind the ghost face, is visible. A solid in front is not.
	 *
	 * @param obstructionDistance {@link Double#POSITIVE_INFINITY} when nothing was hit
	 */
	static boolean visible(double targetDistance, double obstructionDistance, boolean obstructionIsBlock) {
		if (targetDistance < 1.0E-7) {
			return true;
		}

		if (!obstructionIsBlock) {
			return true;
		}

		return obstructionDistance + 0.01 >= targetDistance;
	}

	static Float assistPitch(Direction wantedFacing, float currentPitch) {
		if (wantedFacing == null) {
			return null;
		}

		float candidate;
		if (wantedFacing.getAxis().isHorizontal()) {
			candidate = 0.0F;
		} else if (currentPitch == 0.0F) {
			return null;
		} else {
			candidate = currentPitch < 0.0F ? -89.0F : 89.0F;
		}

		return Math.abs(candidate - currentPitch) < 0.01F ? null : candidate;
	}
}
