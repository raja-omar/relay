package dev.relay.patchcrumbs;

import java.util.ArrayList;
import java.util.List;

import dev.relay.ModInfo;
import dev.relay.RelayClient;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SandBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Core patch-guides detection, ticked every client tick.
 */
public final class PatchCrumbs {
	public static final List<WallCoord> wallCoords = new ArrayList<>();
	public static PatchCrumb currentCrumb;

	private PatchCrumbs() {
	}

	public static void clearSession() {
		wallCoords.clear();
		currentCrumb = null;
		CannonEntityTracker.INSTANCE.clear();
		SandStackTracker.INSTANCE.clear();
	}

	public static boolean shouldSuppress() {
		try {
			return RelayClient.get().config().patchcrumbsAvoidCannons()
					&& CannonWhitelist.isPlayerInsideCannon();
		} catch (Throwable t) {
			return false;
		}
	}

	public static void onClientTickStart(Minecraft client) {
		if (!RelayClient.get().config().patchcrumbs()) {
			return;
		}
		try {
			if (client.level == null || client.player == null) {
				if (client.level == null) {
					// Crumb tracking resets with the world.
					wallCoords.clear();
					currentCrumb = null;
					SandStackTracker.INSTANCE.clear();
				}
				return;
			}

			if (currentCrumb != null && System.currentTimeMillis() > currentCrumb.expiresAt) {
				currentCrumb = null;
			}

			long now = System.currentTimeMillis();
			CannonEntityTracker.INSTANCE.tick(client.level, client.player, now);

			for (CannonEntityTracker.TimedEntity entry : CannonEntityTracker.INSTANCE.entityPositions.values()) {
				Entity entity = entry.entity();
				if (!(entity instanceof PrimedTnt tnt)) {
					continue;
				}
				boolean matched = false;
				for (WallCoord wc : wallCoords) {
					if (wc.testTNT(tnt)) {
						matched = true;
						break;
					}
				}
				if (!matched) {
					wallCoords.add(new WallCoord(tnt));
				}
			}

			if (abcSandStacks()) {
				List<Entity> entities = new ArrayList<>();
				for (CannonEntityTracker.TimedEntity entry : CannonEntityTracker.INSTANCE.entityPositions.values()) {
					entities.add(entry.entity());
				}
				SandStackTracker.Hit hit = SandStackTracker.INSTANCE.tick(client.level, entities, currentCrumb);
				if (hit != null && !shouldKeepExistingCrumb(currentCrumb, hit)) {
					currentCrumb = crumbFromHit(hit);
				}
			} else {
				SandStackTracker.INSTANCE.clear();
			}

			for (int i = 0; i < wallCoords.size(); ++i) {
				WallCoord wc = wallCoords.get(i);
				if (!abcSandStacks()) {
					Integer crumbY = resolveCrumbY(client, wc);
					if (crumbY != null) {
						currentCrumb = new PatchCrumb(
								wc.x, crumbY, wc.z, wc.firstVelocityX, wc.firstVelocityZ, wc.boundingBox);
					}
				}
				if (System.currentTimeMillis() > wc.expiresAt) {
					wallCoords.remove(i);
					--i;
				}
			}
		} catch (Throwable t) {
			ModInfo.LOG.warn("patchcrumbs problem", t);
		}
	}

	/**
	 * Keep the first shot-height crumb. Overstack TNT/sand often re-fires 1 Y higher
	 * in the same or neighboring column; never raise or retarget that patch.
	 */
	private static boolean shouldKeepExistingCrumb(PatchCrumb crumb, SandStackTracker.Hit hit) {
		if (crumb == null) {
			return false;
		}
		int dx = Math.abs(crumb.posX - hit.x());
		int dz = Math.abs(crumb.posZ - hit.z());
		if (dx == 0 && dz == 0) {
			return true;
		}
		return dx <= 1 && dz <= 1 && hit.y() >= crumb.posY;
	}

	private static PatchCrumb crumbFromHit(SandStackTracker.Hit hit) {
		WallCoord wc = wallCoordAt(hit.x(), hit.z());
		if (wc != null) {
			return new PatchCrumb(hit.x(), hit.y(), hit.z(), wc.firstVelocityX, wc.firstVelocityZ, wc.boundingBox);
		}
		Entity entity = hit.entity();
		Vec3 velocity = entity.getDeltaMovement();
		AABB box = entity.getBoundingBox();
		return new PatchCrumb(hit.x(), hit.y(), hit.z(), -velocity.x, -velocity.z, box);
	}

	private static WallCoord wallCoordAt(int x, int z) {
		for (WallCoord wc : wallCoords) {
			if (wc.x == x && wc.z == z) {
				return wc;
			}
		}
		return null;
	}

	/**
	 * Fallback when ABC is off: snap TNT feet to the nearest block, then place the
	 * crumb on the air/TNT block above sand/gravel. Highest valid Y in the column wins.
	 */
	private static Integer resolveCrumbY(Minecraft client, WallCoord wc) {
		List<Integer> candidates = new ArrayList<>();
		addCandidate(candidates, crumbYFromRaw(client, wc, wc.firstYLevel));
		for (TNTSpot spot : wc.tntSpots.values()) {
			addCandidate(candidates, crumbYFromRaw(client, wc, spot.y));
		}
		for (CannonEntityTracker.TimedEntity entry : CannonEntityTracker.INSTANCE.entityPositions.values()) {
			Entity entity = entry.entity();
			if (!(entity instanceof PrimedTnt tnt)) {
				continue;
			}
			if (Mth.floor(tnt.getX()) != wc.x || Mth.floor(tnt.getZ()) != wc.z) {
				continue;
			}
			addCandidate(candidates, crumbYFromRaw(client, wc, tnt.getY()));
		}
		if (candidates.isEmpty()) {
			return wc.confirmedCrumbY;
		}
		return wc.confirmCrumbY(maxOf(candidates), false);
	}

	private static void addCandidate(List<Integer> candidates, Integer y) {
		if (y != null) {
			candidates.add(y);
		}
	}

	private static int maxOf(List<Integer> ys) {
		int max = ys.get(0);
		for (int i = 1; i < ys.size(); ++i) {
			max = Math.max(max, ys.get(i));
		}
		return max;
	}

	private static Integer crumbYFromRaw(Minecraft client, WallCoord wc, double rawY) {
		int snapY = Mth.floor(rawY + 0.5);
		if (isSandOrGravel(client, wc.x, snapY - 1, wc.z)) {
			return snapY;
		}
		if (isSandOrGravel(client, wc.x, snapY - 2, wc.z)) {
			return snapY - 1;
		}
		return null;
	}

	private static boolean isSandOrGravel(Minecraft client, int x, int y, int z) {
		Block block = client.level.getBlockState(new BlockPos(x, y, z)).getBlock();
		return block instanceof SandBlock || block == Blocks.GRAVEL;
	}

	/**
	 * Patch Guides ABC is the default detector. The existing sand-check toggle turns
	 * that off and uses the sand/gravel snap fallback instead.
	 */
	private static boolean abcSandStacks() {
		return !RelayClient.get().config().patchcrumbsSandCheck();
	}
}
