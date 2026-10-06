package dev.relay.patchcrumbs;

import dev.relay.RelayClient;

import net.minecraft.world.phys.AABB;

public final class PatchCrumb {
	public final int posX;
	public final int posY;
	public final int posZ;
	public final double firstVelocityX;
	public final double firstVelocityZ;
	public final long expiresAt;
	public final AABB boundingBox;

	public PatchCrumb(int x, int y, int z, double firstVelocityX, double firstVelocityZ, AABB bb) {
		this.posX = x;
		this.posY = y;
		this.posZ = z;
		this.firstVelocityX = firstVelocityX;
		this.firstVelocityZ = firstVelocityZ;
		this.expiresAt = System.currentTimeMillis()
				+ (long) RelayClient.get().config().patchcrumbsTimeout() * 1000L;
		this.boundingBox = bb;
	}
}
