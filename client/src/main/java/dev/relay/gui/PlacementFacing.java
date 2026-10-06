package dev.relay.gui;

/**
 * Labels for a schematic placement's rotation and mirror. Ordinals match Minecraft's
 * {@code Rotation} and {@code Mirror} enums, which is also the order Litematica cycles.
 */
public final class PlacementFacing {
	public static final int ROTATION_COUNT = 4;
	public static final int MIRROR_COUNT = 3;

	private PlacementFacing() {
	}

	public static String rotationLabel(int ordinal) {
		return switch (ordinal) {
			case 1 -> "90°";
			case 2 -> "180°";
			case 3 -> "270°";
			default -> "None";
		};
	}

	public static String mirrorLabel(int ordinal) {
		return switch (ordinal) {
			case 1 -> "Left/Right";
			case 2 -> "Front/Back";
			default -> "Off";
		};
	}

	public static String rotateHint() {
		return "Turn this placement 90° clockwise.";
	}

	public static String mirrorHint() {
		return "Mirror this placement. Click to cycle Off, Left/Right and Front/Back.";
	}

	public static String lockedHint() {
		return "That placement is locked.";
	}

	public static String materialsHint() {
		return "Open Litematica's material list for this placement.";
	}

	public static String verifierHint() {
		return "Open Litematica's schematic verifier for this placement.";
	}
}
