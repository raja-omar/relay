package dev.relay.common;

/**
 * What a shared block ping is allowed to say. The server checks this before it copies the ping
 * to the rest of the group.
 */
public final class PingLocations {
	public static final int MAX_HORIZONTAL = 30_000_000;
	public static final int MIN_Y = -2048;
	public static final int MAX_Y = 2048;

	private PingLocations() {
	}

	public static boolean isValid(int x, int y, int z, int face, String dimension) {
		if (Math.abs(x) > MAX_HORIZONTAL || Math.abs(z) > MAX_HORIZONTAL) {
			return false;
		}
		if (y < MIN_Y || y > MAX_Y) {
			return false;
		}
		if (face < 0 || face > 5) {
			return false;
		}
		return isDimension(dimension);
	}

	/** A Minecraft dimension id, such as {@code minecraft:overworld}. */
	public static boolean isDimension(String dimension) {
		if (dimension == null || dimension.length() < 3 || dimension.length() > 128) {
			return false;
		}
		int colon = dimension.indexOf(':');
		if (colon <= 0 || colon != dimension.lastIndexOf(':') || colon == dimension.length() - 1) {
			return false;
		}
		for (int i = 0; i < dimension.length(); i++) {
			char c = dimension.charAt(i);
			boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
					|| c == '_' || c == '.' || c == '-' || c == '/' || c == ':';
			if (!ok) {
				return false;
			}
		}
		return true;
	}
}
