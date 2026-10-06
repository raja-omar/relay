package dev.relay.gui;

import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.OwoUIGraphics;

/**
 * Vector sidebar marks. Circles, rings, and thick lines stay clean at every GUI scale instead of a
 * pixel stamp.
 */
final class RelayIcons {
	static final int SIZE = 12;

	private RelayIcons() {
	}

	static void paint(OwoUIGraphics context, int x, int y, String id, int argb) {
		Color color = Color.ofArgb(argb);
		int cx = x + SIZE / 2;
		int cy = y + SIZE / 2;
		switch (id) {
			case "litematica" -> schematic(context, x, y, color, argb);
			case "fastplace" -> chevrons(context, x, y, color);
			case "patchcrumbs" -> crumbs(context, x, y, color);
			case "fluids" -> droplet(context, x, y, color);
			case "miscellaneous" -> beam(context, x, y, color);
			case "combat" -> plus(context, x, y, color);
			case "groups" -> people(context, x, y, color);
			case "hotkeys" -> key(context, x, y, color, argb);
			default -> context.drawCircle(cx, cy, 12, 2.4, color);
		}
	}

	private static void schematic(OwoUIGraphics context, int x, int y, Color color, int argb) {
		RelayTheme.drawRoundFrame(context, x + 1, y + 1, SIZE - 2, SIZE - 2, 2, argb);
		context.drawCircle(x + SIZE / 2, y + SIZE / 2, 12, 1.5, color);
	}

	private static void chevrons(OwoUIGraphics context, int x, int y, Color color) {
		double t = 1.25;
		context.drawLine(x + 2, y + 3, x + 5, y + 6, t, color);
		context.drawLine(x + 5, y + 6, x + 2, y + 9, t, color);
		context.drawLine(x + 6, y + 3, x + 9, y + 6, t, color);
		context.drawLine(x + 9, y + 6, x + 6, y + 9, t, color);
	}

	private static void crumbs(OwoUIGraphics context, int x, int y, Color color) {
		context.drawCircle(x + 3, y + 9, 10, 1.35, color);
		context.drawCircle(x + 6, y + 6, 10, 1.35, color);
		context.drawCircle(x + 9, y + 3, 10, 1.35, color);
	}

	private static void droplet(OwoUIGraphics context, int x, int y, Color color) {
		double t = 1.2;
		context.drawLine(x + 6, y + 1, x + 3, y + 6, t, color);
		context.drawLine(x + 6, y + 1, x + 9, y + 6, t, color);
		context.drawCircle(x + 6, y + 8, 12, 2.15, color);
	}

	private static void beam(OwoUIGraphics context, int x, int y, Color color) {
		context.drawLine(x + 6, y + 1, x + 6, y + 11, 1.35, color);
		context.drawCircle(x + 6, y + 6, 12, 1.55, color);
	}

	private static void plus(OwoUIGraphics context, int x, int y, Color color) {
		context.drawLine(x + 6, y + 2, x + 6, y + 10, 1.35, color);
		context.drawLine(x + 2, y + 6, x + 10, y + 6, 1.35, color);
	}

	private static void people(OwoUIGraphics context, int x, int y, Color color) {
		context.drawCircle(x + 4, y + 3, 12, 1.55, color);
		context.drawCircle(x + 8, y + 3, 12, 1.55, color);
		context.drawCircle(x + 4, y + 8, 12, 2.05, color);
		context.drawCircle(x + 8, y + 8, 12, 2.05, color);
	}

	private static void key(OwoUIGraphics context, int x, int y, Color color, int argb) {
		RelayTheme.drawRoundFrame(context, x + 1, y + 3, SIZE - 2, SIZE - 6, 2, argb);
		context.drawCircle(x + SIZE / 2, y + SIZE / 2, 10, 1.05, color);
	}
}
