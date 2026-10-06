package dev.relay.patchcrumbs;

import java.util.HashMap;
import java.util.UUID;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Groups primed TNT that share the same floored X/Z column for a short window.
 */
public final class WallCoord {
	public final int x;
	public final int z;
	public final int y;
	public final double firstVelocityX;
	public final double firstVelocityZ;
	public final double firstYLevel;
	public final long expiresAt;
	public final AABB boundingBox;
	public final HashMap<UUID, TNTSpot> tntSpots = new HashMap<>();
	/** Shot-block Y for this column; may rise, never lowers, except one overstack drop. */
	public Integer confirmedCrumbY;
	/** True after overstack correction lowers Y; later raises stay inside the shot band. */
	public boolean overstackDropUsed;

	public WallCoord(PrimedTnt tnt) {
		this.x = Mth.floor(tnt.getX());
		this.z = Mth.floor(tnt.getZ());
		this.y = Mth.floor(tnt.getY());
		this.firstYLevel = tnt.getY();
		this.tntSpots.put(tnt.getUUID(), new TNTSpot(tnt));
		this.expiresAt = System.currentTimeMillis() + 4500L;
		this.boundingBox = tnt.getBoundingBox();
		// Approximate backwards motion from last-tick position.
		double backwardX = tnt.xo - tnt.getX();
		double backwardZ = tnt.zo - tnt.getZ();
		if (Math.abs(backwardX) < 1.0E-4 && Math.abs(backwardZ) < 1.0E-4) {
			Vec3 velocity = tnt.getDeltaMovement();
			backwardX = -velocity.x;
			backwardZ = -velocity.z;
		}
		this.firstVelocityX = backwardX;
		this.firstVelocityZ = backwardZ;
	}

	public void addTNT(PrimedTnt tnt) {
		if (this.tntSpots.containsKey(tnt.getUUID())) {
			return;
		}
		this.tntSpots.put(tnt.getUUID(), new TNTSpot(tnt));
	}

	public boolean testTNT(PrimedTnt tnt) {
		if (Mth.floor(tnt.getX()) == this.x && Mth.floor(tnt.getZ()) == this.z) {
			this.addTNT(tnt);
			return true;
		}
		return false;
	}

	/**
	 * Confirm shot Y for this column. Raise-only by default. When {@code overstack} is
	 * true, Y may drop once from the sand-stack top down to the shot band; after that,
	 * it only rises by at most 1 (cuboid snap) and will not follow the overstack top.
	 */
	public int confirmCrumbY(int y, boolean overstack) {
		if (this.confirmedCrumbY == null) {
			this.confirmedCrumbY = y;
			if (overstack) {
				this.overstackDropUsed = true;
			}
			return y;
		}
		if (overstack && y < this.confirmedCrumbY) {
			this.overstackDropUsed = true;
			this.confirmedCrumbY = y;
			return this.confirmedCrumbY;
		}
		if (this.overstackDropUsed) {
			if (y > this.confirmedCrumbY && y <= this.confirmedCrumbY + 1) {
				this.confirmedCrumbY = y;
			}
			return this.confirmedCrumbY;
		}
		if (y > this.confirmedCrumbY) {
			this.confirmedCrumbY = y;
		}
		return this.confirmedCrumbY;
	}
}
