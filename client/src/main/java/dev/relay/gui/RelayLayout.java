package dev.relay.gui;

/**
 * Overlay size in Minecraft GUI units — the same units owo-ui lays out in.
 */
public final class RelayLayout {
	public static final int MARGIN = 16;
	public static final int MIN_WIDTH = 500;
	public static final int MAX_WIDTH = 600;
	public static final int MIN_HEIGHT = 340;
	public static final int MAX_HEIGHT = 420;
	public static final int SIDEBAR = 132;

	public record Panel(int width, int height, int sidebar) {
	}

	private RelayLayout() {
	}

	public static Panel panel(int guiWidth, int guiHeight) {
		int maxW = Math.max(1, guiWidth - MARGIN);
		int maxH = Math.max(1, guiHeight - MARGIN);
		int width = clamp((int) Math.round(guiWidth * 0.64), Math.min(MIN_WIDTH, maxW), Math.min(MAX_WIDTH, maxW));
		int height = clamp((int) Math.round(guiHeight * 0.80), Math.min(MIN_HEIGHT, maxH), Math.min(MAX_HEIGHT, maxH));
		int sidebar = Math.min(SIDEBAR, Math.max(120, width / 4));
		sidebar = Math.min(sidebar, Math.max(1, width / 3));
		return new Panel(width, height, sidebar);
	}

	private static int clamp(int value, int min, int max) {
		if (max < min) {
			return max;
		}
		return Math.max(min, Math.min(max, value));
	}
}
