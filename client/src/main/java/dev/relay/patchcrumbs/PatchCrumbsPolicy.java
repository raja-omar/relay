package dev.relay.patchcrumbs;

/**
 * Labels, ranges, and overlay colors for Patchcrumbs.
 */
public final class PatchCrumbsPolicy {
	public static final int MIN_TIMEOUT_SECONDS = 1;
	public static final int MAX_TIMEOUT_SECONDS = 60;
	public static final int DEFAULT_TIMEOUT_SECONDS = 12;

	public static final int MIN_LINE_WIDTH = 1;
	public static final int MAX_LINE_WIDTH = 10;
	public static final int DEFAULT_LINE_WIDTH = 2;

	/** Blocks each way from the crumb, as in Patch Guides. */
	public static final int LINE_REACH = 200;

	private PatchCrumbsPolicy() {
	}

	public enum DirectionMode {
		AUTO("Auto"),
		NORTH_SOUTH("N/S"),
		EAST_WEST("E/W"),
		BOTH("Both");

		private final String label;

		DirectionMode(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}

		public static DirectionMode fromName(String raw, DirectionMode fallback) {
			if (raw == null || raw.isBlank()) {
				return fallback;
			}

			return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
				case "auto" -> AUTO;
				case "n/s", "ns", "north_south", "north/south", "northsouth" -> NORTH_SOUTH;
				case "e/w", "ew", "east_west", "east/west", "eastwest" -> EAST_WEST;
				case "both" -> BOTH;
				default -> fallback;
			};
		}

		public DirectionMode next() {
			return values()[(ordinal() + 1) % values().length];
		}

		public DirectionMode previous() {
			return values()[(ordinal() + values().length - 1) % values().length];
		}
	}

	public enum Palette {
		RED,
		ORANGE,
		YELLOW,
		GREEN,
		CYAN,
		BLUE,
		MAGENTA,
		WHITE,
		RGB;

		public String label() {
			if (this == RGB) {
				return "RGB";
			}
			String name = name();
			return name.charAt(0) + name.substring(1).toLowerCase(java.util.Locale.ROOT);
		}

		public Palette next() {
			return values()[(ordinal() + 1) % values().length];
		}

		public Palette previous() {
			return values()[(ordinal() + values().length - 1) % values().length];
		}

		public static Palette fromName(String raw, Palette fallback) {
			if (raw == null || raw.isBlank()) {
				return fallback;
			}

			try {
				return valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
			} catch (IllegalArgumentException unknown) {
				String name = raw.trim().toLowerCase(java.util.Locale.ROOT);
				if (name.equals("rainbow") || name.equals("chroma") || name.equals("cycle")) {
					return RGB;
				}
				return fallback;
			}
		}

		/** Opaque ARGB used by the shot box, axis guides, and HUD. */
		public int argb() {
			return switch (this) {
				case RED -> 0xFFFF0000;
				case ORANGE -> 0xFFFF7F00;
				case YELLOW -> 0xFFFFFF00;
				case GREEN -> 0xFF00FF00;
				case CYAN -> 0xFF00FFFF;
				case BLUE -> 0xFF0000FF;
				case MAGENTA -> 0xFFFF00FF;
				case WHITE -> 0xFFFFFFFF;
				case RGB -> rgbCycle(System.currentTimeMillis());
			};
		}

		static int rgbCycle(long now) {
			float hue = (now % 3000L) / 3000.0F;
			int sector = (int) (hue * 6);
			float fraction = hue * 6 - sector;
			int rising = Math.round(255 * fraction);
			int falling = Math.round(255 * (1 - fraction));
			return switch (sector % 6) {
				case 0 -> 0xFF000000 | (255 << 16) | (rising << 8);
				case 1 -> 0xFF000000 | (falling << 16) | (255 << 8);
				case 2 -> 0xFF000000 | (255 << 8) | rising;
				case 3 -> 0xFF000000 | (falling << 8) | 255;
				case 4 -> 0xFF000000 | (255 << 16) | 255;
				default -> 0xFF000000 | (255 << 16) | falling;
			};
		}
	}

	public static int clampTimeout(int seconds) {
		return Math.max(MIN_TIMEOUT_SECONDS, Math.min(MAX_TIMEOUT_SECONDS, seconds));
	}

	public static int clampWidth(int width) {
		return Math.max(MIN_LINE_WIDTH, Math.min(MAX_LINE_WIDTH, width));
	}

	public static boolean canLowerTimeout(int seconds) {
		return clampTimeout(seconds) > MIN_TIMEOUT_SECONDS;
	}

	public static boolean canRaiseTimeout(int seconds) {
		return clampTimeout(seconds) < MAX_TIMEOUT_SECONDS;
	}

	public static boolean canLowerWidth(int width) {
		return clampWidth(width) > MIN_LINE_WIDTH;
	}

	public static boolean canRaiseWidth(int width) {
		return clampWidth(width) < MAX_LINE_WIDTH;
	}

	public static String timeoutLabel(int seconds) {
		return clampTimeout(seconds) + "s";
	}

	public static String timeoutHint() {
		return "How long the last shot stays marked after it is seen.";
	}

	public static String widthHint() {
		return "Thickness of the shot box and the four corner patch lines.";
	}

	public static String directionHint() {
		return "Which way the long patch lines run. Auto and Both draw forward/back and left/right from every corner.";
	}
}
