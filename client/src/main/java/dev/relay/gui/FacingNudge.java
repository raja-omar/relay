package dev.relay.gui;

/**
 * Schematic move steps taken from the way the player is looking, not from world north.
 *
 * <p>Cardinal headings win unless the look is clearly between two sides. That keeps Forward as a
 * single block most of the time, and only steps on a diagonal when the player is actually facing
 * one.
 */
public final class FacingNudge {
	/**
	 * How far a look may wander from a compass point before Forward also takes the neighbouring
	 * side. Thirty degrees gives each cardinal a wide front, so a slight diagonal still prefers
	 * left/right/forward rather than sliding on two axes.
	 */
	public static final float CARDINAL_HOLD_DEGREES = 30.0F;

	public enum Move {
		FORWARD,
		BACK,
		LEFT,
		RIGHT,
		UP,
		DOWN
	}

	public record Step(int x, int y, int z) {
		public static final Step ZERO = new Step(0, 0, 0);

		public boolean isZero() {
			return x == 0 && y == 0 && z == 0;
		}
	}

	private static final double DIAGONAL_RATIO = Math.tan(Math.toRadians(CARDINAL_HOLD_DEGREES));

	private FacingNudge() {
	}

	public static Step step(Move move, float yaw) {
		if (move == Move.UP) {
			return new Step(0, 1, 0);
		}

		if (move == Move.DOWN) {
			return new Step(0, -1, 0);
		}

		Step forward = horizontal(yaw);
		return switch (move) {
			case FORWARD -> forward;
			case BACK -> new Step(-forward.x(), 0, -forward.z());
			case LEFT -> new Step(forward.z(), 0, -forward.x());
			case RIGHT -> new Step(-forward.z(), 0, forward.x());
			case UP, DOWN -> Step.ZERO;
		};
	}

	/**
	 * The horizontal step for a look yaw. Minecraft yaw 0 is south (+Z) and 90 is west (-X).
	 *
	 * <p>The stronger world axis wins. The weaker axis is included only when the look is at least
	 * {@link #CARDINAL_HOLD_DEGREES} away from that stronger axis, so a near-diagonal still stays
	 * on one side.
	 */
	public static Step horizontal(float yaw) {
		double radians = Math.toRadians(yaw);
		double lookX = -Math.sin(radians);
		double lookZ = Math.cos(radians);
		int signX = sign(lookX);
		int signZ = sign(lookZ);
		double absX = Math.abs(lookX);
		double absZ = Math.abs(lookZ);
		double primary = Math.max(absX, absZ);
		double secondary = Math.min(absX, absZ);

		if (primary == 0.0) {
			return new Step(0, 0, 1);
		}

		boolean diagonal = secondary / primary >= DIAGONAL_RATIO;

		if (diagonal) {
			return new Step(signX, 0, signZ);
		}

		if (absX > absZ) {
			return new Step(signX, 0, 0);
		}

		return new Step(0, 0, signZ);
	}

	public static String heading(float yaw) {
		return heading(horizontal(yaw));
	}

	public static String heading(Step step) {
		if (step.y() > 0 && step.x() == 0 && step.z() == 0) {
			return "up";
		}

		if (step.y() < 0 && step.x() == 0 && step.z() == 0) {
			return "down";
		}

		String northSouth = step.z() < 0 ? "north" : step.z() > 0 ? "south" : "";
		String eastWest = step.x() > 0 ? "east" : step.x() < 0 ? "west" : "";

		if (northSouth.isEmpty()) {
			return eastWest.isEmpty() ? "south" : eastWest;
		}

		if (eastWest.isEmpty()) {
			return northSouth;
		}

		return northSouth + eastWest;
	}

	public static String describe(Move move) {
		return switch (move) {
			case FORWARD -> "forward";
			case BACK -> "back";
			case LEFT -> "left";
			case RIGHT -> "right";
			case UP -> "up";
			case DOWN -> "down";
		};
	}

	public static String hint(Move move) {
		return switch (move) {
			case FORWARD, BACK, LEFT, RIGHT ->
					"Move one block " + describe(move) + ", relative to where you are looking.";
			case UP -> "Move one block up.";
			case DOWN -> "Move one block down.";
		};
	}

	private static int sign(double value) {
		if (value > 0.0) {
			return 1;
		}

		if (value < 0.0) {
			return -1;
		}

		return 0;
	}
}
