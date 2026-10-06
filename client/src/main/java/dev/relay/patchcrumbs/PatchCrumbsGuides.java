package dev.relay.patchcrumbs;

/**
 * Geometry for the four corner patch lines. Minecraft's debug-line shader turns a segment
 * into a huge rectangle when one end is behind the camera, so the long axes are clipped to
 * the view-forward half-space before they are drawn.
 */
public final class PatchCrumbsGuides {
	static final double NEAR_PLANE = 0.25;

	private PatchCrumbsGuides() {
	}

	public record BlockSpan(int from, int to) {
		static final BlockSpan EMPTY = new BlockSpan(1, 0);

		public boolean isEmpty() {
			return from > to;
		}
	}

	public record ClippedLine(double x1, double y1, double z1, double x2, double y2, double z2) {
	}

	/**
	 * Inclusive block range along X after dropping the part behind the camera.
	 */
	public static BlockSpan clipEastWest(
			int originX, int y, int z, int reach,
			double camX, double camY, double camZ,
			double lookX, double lookY, double lookZ) {
		ClippedLine line = clipLine(
				originX - reach, y + 0.5, z + 0.5,
				originX + reach + 1, y + 0.5, z + 0.5,
				camX, camY, camZ, lookX, lookY, lookZ);
		return spanOnAxis(line, true, originX, reach);
	}

	/**
	 * Inclusive block range along Z after dropping the part behind the camera.
	 */
	public static BlockSpan clipNorthSouth(
			int x, int y, int originZ, int reach,
			double camX, double camY, double camZ,
			double lookX, double lookY, double lookZ) {
		ClippedLine line = clipLine(
				x + 0.5, y + 0.5, originZ - reach,
				x + 0.5, y + 0.5, originZ + reach + 1,
				camX, camY, camZ, lookX, lookY, lookZ);
		return spanOnAxis(line, false, originZ, reach);
	}

	public static ClippedLine clipLine(
			double ax, double ay, double az,
			double bx, double by, double bz,
			double camX, double camY, double camZ,
			double lookX, double lookY, double lookZ) {
		return clipLine(ax, ay, az, bx, by, bz, camX, camY, camZ, lookX, lookY, lookZ, NEAR_PLANE);
	}

	public static ClippedLine clipLine(
			double ax, double ay, double az,
			double bx, double by, double bz,
			double camX, double camY, double camZ,
			double lookX, double lookY, double lookZ,
			double near) {
		double depthA = depth(ax, ay, az, camX, camY, camZ, lookX, lookY, lookZ);
		double depthB = depth(bx, by, bz, camX, camY, camZ, lookX, lookY, lookZ);
		if (depthA < near && depthB < near) {
			return null;
		}
		if (depthA >= near && depthB >= near) {
			return new ClippedLine(ax, ay, az, bx, by, bz);
		}

		double span = depthB - depthA;
		if (Math.abs(span) < 1.0E-8) {
			return null;
		}

		double t = (near - depthA) / span;
		double mx = ax + t * (bx - ax);
		double my = ay + t * (by - ay);
		double mz = az + t * (bz - az);
		if (depthA >= near) {
			return new ClippedLine(ax, ay, az, mx, my, mz);
		}
		return new ClippedLine(mx, my, mz, bx, by, bz);
	}

	private static double depth(
			double x, double y, double z,
			double camX, double camY, double camZ,
			double lookX, double lookY, double lookZ) {
		return (x - camX) * lookX + (y - camY) * lookY + (z - camZ) * lookZ;
	}

	private static BlockSpan spanOnAxis(ClippedLine line, boolean alongX, int origin, int reach) {
		if (line == null) {
			return BlockSpan.EMPTY;
		}

		double a = alongX ? line.x1() : line.z1();
		double b = alongX ? line.x2() : line.z2();
		double min = Math.min(a, b);
		double max = Math.max(a, b);
		int from = clampBlock((int) Math.ceil(min - 1.0E-9), origin, reach);
		int to = clampBlock((int) Math.floor(max - 1.0E-9), origin, reach);
		if (from > to) {
			return BlockSpan.EMPTY;
		}
		return new BlockSpan(from, to);
	}

	private static int clampBlock(int value, int origin, int reach) {
		int lo = origin - reach;
		int hi = origin + reach;
		return Math.max(lo, Math.min(hi, value));
	}
}
