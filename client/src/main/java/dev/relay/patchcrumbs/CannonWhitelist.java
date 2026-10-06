package dev.relay.patchcrumbs;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;

/**
 * Manual AABB whitelist for the player's cannon area.
 * Used with /cannon pos1|pos2 so own-cannon TNT can be ignored.
 */
public final class CannonWhitelist {
	private static BlockPos pos1;
	private static BlockPos pos2;

	private CannonWhitelist() {
	}

	public static void setPos1(BlockPos pos) {
		pos1 = pos.immutable();
	}

	public static void setPos2(BlockPos pos) {
		pos2 = pos.immutable();
	}

	public static void clear() {
		pos1 = null;
		pos2 = null;
	}

	public static BlockPos getPos1() {
		return pos1;
	}

	public static BlockPos getPos2() {
		return pos2;
	}

	public static boolean isInsideCannon(Entity entity) {
		if (pos1 == null || pos2 == null) {
			return false;
		}
		double x = entity.getX();
		double y = entity.getY();
		double z = entity.getZ();
		int minX = Math.min(pos1.getX(), pos2.getX());
		int maxX = Math.max(pos1.getX(), pos2.getX());
		int minY = Math.min(pos1.getY(), pos2.getY());
		int maxY = Math.max(pos1.getY(), pos2.getY());
		int minZ = Math.min(pos1.getZ(), pos2.getZ());
		int maxZ = Math.max(pos1.getZ(), pos2.getZ());
		return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
	}

	public static boolean isEnemyTNT(PrimedTnt tnt) {
		return !isInsideCannon(tnt);
	}

	public static boolean isPlayerInsideCannon() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player != null && isInsideCannon(mc.player);
	}
}
