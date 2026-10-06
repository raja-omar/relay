package dev.relay.patchcrumbs;

import net.minecraft.world.entity.item.PrimedTnt;

public final class TNTSpot {
	public final double x;
	public final double y;
	public final double z;
	public final long fuseExpires;

	public TNTSpot(PrimedTnt tnt) {
		this.x = tnt.getX();
		this.y = tnt.getY();
		this.z = tnt.getZ();
		// fuse ticks * 50ms
		this.fuseExpires = System.currentTimeMillis() + (long) tnt.getFuse() * 50L;
	}
}
