package dev.relay.fluids;

/**
 * Clear water keeps the water mesh but drops underwater fog, FOV, and the overlay.
 * Hidden water also clears that view so swimming is not a blue wall.
 */
public final class ClearWater {
	private ClearWater() {
	}

	/** Safe to call from mixins before the client exists. */
	public static boolean enabled() {
		return WaterVisibility.current() == WaterVisibility.CLEAR;
	}

	/** Hidden water also drops fog, FOV, and overlay so you are not swimming in a blue wall. */
	public static boolean clearsView() {
		return WaterVisibility.clearsView();
	}

	public static void set(boolean on) {
		WaterVisibility.set(on ? WaterVisibility.CLEAR : WaterVisibility.NORMAL);
	}

	public static String hint() {
		return "Removes underwater fog, FOV zoom, and the blue overlay. Water itself still draws.";
	}
}
