package dev.relay.ping;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * A beam through the center of a block, along the axis of the face that was pinged.
 * Opposite faces share one segment: north and south are Z, east and west are X, up and down are Y.
 */
public final class PingBeam {
	/** Blocks the line extends past the center in each direction. Long enough to read as endless. */
	public static final int REACH = 1024;

	/**
	 * Half the shaft width. A beacon's solid beam is 0.2 across from the center
	 * (0.4 blocks wide); this stays a little under that.
	 */
	public static final float HALF_WIDTH = 0.16F;

	private PingBeam() {
	}

	public static Segment through(BlockPos pos, Direction front) {
		return through(pos, front, REACH);
	}

	public static Segment through(BlockPos pos, Direction front, int reach) {
		return through(pos.getX(), pos.getY(), pos.getZ(), front, reach);
	}

	private static Segment through(int x, int y, int z, Direction front, int reach) {
		double cx = x + 0.5;
		double cy = y + 0.5;
		double cz = z + 0.5;
		int step = Math.max(0, reach);
		return switch (front.getAxis()) {
			case X -> new Segment(cx - step, cy, cz, cx + step, cy, cz);
			case Y -> new Segment(cx, cy - step, cz, cx, cy + step, cz);
			case Z -> new Segment(cx, cy, cz - step, cx, cy, cz + step);
		};
	}

	public record Segment(double x1, double y1, double z1, double x2, double y2, double z2) {
	}
}
