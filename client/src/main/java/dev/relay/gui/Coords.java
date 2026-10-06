package dev.relay.gui;

import java.util.OptionalInt;

/**
 * World positions shown in the overlay. Axis letters stay in the copy so a row of
 * numbers cannot be misread.
 */
public final class Coords {
	private Coords() {
	}

	public static String format(int x, int y, int z) {
		return "X " + x + "  Y " + y + "  Z " + z;
	}

	/** True while the player is typing a signed integer into a coordinate field. */
	public static boolean typing(String raw) {
		return raw != null && raw.matches("-?\\d{0,10}");
	}

	public static OptionalInt parse(String raw) {
		if (raw == null) {
			return OptionalInt.empty();
		}

		String text = raw.trim();
		if (text.isEmpty() || "-".equals(text)) {
			return OptionalInt.empty();
		}

		try {
			return OptionalInt.of(Integer.parseInt(text));
		} catch (NumberFormatException failed) {
			return OptionalInt.empty();
		}
	}
}
