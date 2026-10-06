package dev.relay.gui;

import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.ParentUIComponent;
import io.wispforest.owo.ui.core.Surface;

/**
 * Overlay palette. Translucent zinc, one quiet accent. The window and inner
 * controls are disc-cornered so edges stay smooth at every GUI scale.
 */
final class RelayTheme {
	/** rgba(9, 9, 11, 0.72) */
	static final int WINDOW = 0xB809090B;
	static final int HOVER = 0x0FFFFFFF;
	static final int PRESS = 0x33FFFFFF;
	static final int SELECTED = 0x1A7EB8D4;
	static final int SELECTED_HOVER = 0x2E7EB8D4;
	static final int BORDER = 0x14FFFFFF;
	static final int BORDER_STRONG = 0x22FFFFFF;
	/** Cool steel-blue, used for primary actions and selection. */
	static final int ACCENT = 0xFF7EB8D4;
	static final int TEXT = 0xF2FFFFFF;
	static final int TEXT_DIM = 0x99FFFFFF;
	static final int MUTED = 0x66FFFFFF;
	static final int GOOD = 0xFF7DDAA5;
	static final int BAD = 0xFFFF8B93;
	static final int AXIS_X = 0xFFFF9A9A;
	static final int AXIS_Y = 0xFF8EE0B0;
	static final int AXIS_Z = 0xFF8EC4E8;
	static final int TRACK_OFF = 0x33FFFFFF;
	static final int KNOB = 0xFFF4F4F5;
	static final int SCRIM = 0x33000000;
	static final int INPUT_FILL = 0x66000000;

	static final int WINDOW_RADIUS = 14;
	private static final int CORNER_SEGMENTS = 48;

	private RelayTheme() {
	}

	static Surface glass() {
		return Surface.blur(3.0F, 8.0F).and(RelayTheme::glassPanel);
	}

	static Surface sidebar() {
		return Surface.BLANK;
	}

	static Surface hairline() {
		return Surface.flat(BORDER);
	}

	static Surface hover() {
		return Surface.flat(HOVER);
	}

	static Surface press() {
		return Surface.flat(PRESS);
	}

	static Surface selected() {
		return Surface.flat(SELECTED);
	}

	static Surface selectedNav() {
		return (context, component) -> {
			int x = component.x();
			int y = component.y();
			int h = component.height();
			if (h <= 8) {
				return;
			}

			drawRoundRect(context, x + 2, y + 4, 2, h - 8, 1, ACCENT, 0);
		};
	}

	static Surface tabUnderline() {
		return (context, component) -> {
			int w = component.width();
			int h = component.height();
			if (w <= 10 || h <= 4) {
				return;
			}

			drawRoundRect(context, component.x() + 6, component.y() + h - 2, w - 12, 2, 1, ACCENT, 0);
		};
	}

	static Surface blank() {
		return Surface.BLANK;
	}

	static Surface input() {
		return roundRect(INPUT_FILL, BORDER, 4);
	}

	static Surface inputFocus() {
		return roundRect(INPUT_FILL, 0x667EB8D4, 4);
	}

	static Surface keycap(boolean listening) {
		int fill = listening ? 0x337EB8D4 : 0x14FFFFFF;
		int edge = listening ? 0x667EB8D4 : BORDER;
		return roundRect(fill, edge, 3);
	}

	static Surface button(int fill, int edge) {
		return roundRect(fill, edge, 4);
	}

	static Surface roundRect(int fill, int edge, int radius) {
		return (context, component) -> drawRoundRect(context, component.x(), component.y(),
				component.width(), component.height(), radius, fill, edge);
	}

	static Surface rect(int fill, int edge) {
		return roundRect(fill, edge, 0);
	}

	static Surface pill(int fill, int edge) {
		return (context, component) -> {
			int radius = Math.max(1, component.height() / 2);
			drawRoundRect(context, component.x(), component.y(), component.width(), component.height(),
					radius, fill, edge);
		};
	}

	static Surface disc(int fill) {
		return (context, component) -> {
			double radius = Math.min(component.width(), component.height()) / 2.0;
			centeredDisc(fill, radius).draw(context, component);
		};
	}

	static Surface centeredDisc(int fill, double radius) {
		return (context, component) -> {
			if (radius <= 0 || component.width() <= 0 || component.height() <= 0) {
				return;
			}

			int cx = component.x() + component.width() / 2;
			int cy = component.y() + component.height() / 2;
			context.drawCircle(cx, cy, Math.max(10, (int) Math.round(radius * 4)), radius, Color.ofArgb(fill));
		};
	}

	static void drawRoundRect(OwoUIGraphics context, int x, int y, int w, int h, int radius, int fill,
			int edge) {
		int r = clampRadius(w, h, radius);
		if (w <= 0 || h <= 0) {
			return;
		}

		boolean paintFill = ((fill >>> 24) & 0xFF) != 0;
		if (!paintFill && edge == 0) {
			return;
		}

		if (r <= 0) {
			if (paintFill) {
				context.fill(x, y, x + w, y + h, fill);
			}
			if (edge != 0) {
				context.drawRectOutline(x, y, w, h, edge);
			}
			return;
		}

		if (paintFill) {
			context.fill(x + r, y, x + w - r, y + h, fill);
			context.fill(x, y + r, x + r, y + h - r, fill);
			context.fill(x + w - r, y + r, x + w, y + h - r, fill);

			Color fillColor = Color.ofArgb(fill);
			int segments = Math.max(10, r * 4);
			context.drawCircle(x + r, y + r, 0, 90, segments, r, fillColor);
			context.drawCircle(x + w - r, y + r, 90, 180, segments, r, fillColor);
			context.drawCircle(x + w - r, y + h - r, 180, 270, segments, r, fillColor);
			context.drawCircle(x + r, y + h - r, 270, 360, segments, r, fillColor);
		}

		if (edge != 0) {
			drawRoundFrame(context, x, y, w, h, r, edge);
		}
	}

	static void drawRoundFrame(OwoUIGraphics context, int x, int y, int w, int h, int radius, int edge) {
		int r = clampRadius(w, h, radius);
		if (w <= 0 || h <= 0 || edge == 0) {
			return;
		}

		if (r <= 0) {
			context.drawRectOutline(x, y, w, h, edge);
			return;
		}

		context.fill(x + r, y, x + w - r, y + 1, edge);
		context.fill(x + r, y + h - 1, x + w - r, y + h, edge);
		context.fill(x, y + r, x + 1, y + h - r, edge);
		context.fill(x + w - 1, y + r, x + w, y + h - r, edge);

		Color edgeColor = Color.ofArgb(edge);
		int segments = Math.max(10, r * 4);
		double inner = Math.max(0.5, r - 1.0);
		context.drawRing(x + r, y + r, 0, 90, segments, inner, r, edgeColor, edgeColor);
		context.drawRing(x + w - r, y + r, 90, 180, segments, inner, r, edgeColor, edgeColor);
		context.drawRing(x + w - r, y + h - r, 180, 270, segments, inner, r, edgeColor, edgeColor);
		context.drawRing(x + r, y + h - r, 270, 360, segments, inner, r, edgeColor, edgeColor);
	}

	static int clampRadius(int width, int height, int radius) {
		int max = Math.min(width, height) / 2;
		if (max <= 0 || radius <= 0) {
			return 0;
		}

		return Math.min(radius, max);
	}

	static int blend(int from, int to, float amount) {
		float t = Math.max(0.0F, Math.min(1.0F, amount));
		int a = mix((from >>> 24) & 0xFF, (to >>> 24) & 0xFF, t);
		int r = mix((from >>> 16) & 0xFF, (to >>> 16) & 0xFF, t);
		int g = mix((from >>> 8) & 0xFF, (to >>> 8) & 0xFF, t);
		int b = mix(from & 0xFF, to & 0xFF, t);
		return a << 24 | r << 16 | g << 8 | b;
	}

	private static void glassPanel(OwoUIGraphics context, ParentUIComponent component) {
		int x = component.x();
		int y = component.y();
		int w = component.width();
		int h = component.height();
		int r = Math.min(WINDOW_RADIUS, Math.min(w, h) / 2);
		if (w <= 0 || h <= 0 || r <= 0) {
			return;
		}

		context.fill(x + r, y, x + w - r, y + h, WINDOW);
		context.fill(x, y + r, x + r, y + h - r, WINDOW);
		context.fill(x + w - r, y + r, x + w, y + h - r, WINDOW);

		context.fill(x + r, y, x + w - r, y + 1, BORDER_STRONG);
		context.fill(x + r, y + h - 1, x + w - r, y + h, BORDER_STRONG);
		context.fill(x, y + r, x + 1, y + h - r, BORDER_STRONG);
		context.fill(x + w - 1, y + r, x + w, y + h - r, BORDER_STRONG);

		Color fill = Color.ofArgb(WINDOW);
		Color edge = Color.ofArgb(BORDER_STRONG);
		corner(context, x + r, y + r, true, true, r, fill, edge);
		corner(context, x + w - r, y + r, false, true, r, fill, edge);
		corner(context, x + r, y + h - r, true, false, r, fill, edge);
		corner(context, x + w - r, y + h - r, false, false, r, fill, edge);
	}

	/**
	 * owo-ui plots circle vertices as {@code (cx - cos θ, cy - sin θ)}, so 0° faces
	 * left and 90° faces up. Outer rounded-rect corners use that space, not the
	 * inward quarter that looks like an inverted bite.
	 */
	static double cornerFrom(boolean left, boolean top) {
		if (top) {
			return left ? 0.0 : 90.0;
		}
		return left ? 270.0 : 180.0;
	}

	static double cornerTo(boolean left, boolean top) {
		return cornerFrom(left, top) + 90.0;
	}

	private static void corner(OwoUIGraphics context, int cx, int cy, boolean left, boolean top, int radius,
			Color fill, Color edge) {
		double from = cornerFrom(left, top);
		double to = cornerTo(left, top);
		context.drawCircle(cx, cy, from, to, CORNER_SEGMENTS, radius, fill);
		context.drawRing(cx, cy, from, to, CORNER_SEGMENTS, Math.max(0.5, radius - 1.0), radius, edge, edge);
	}

	private static int mix(int from, int to, float amount) {
		return Math.round(from + (to - from) * amount);
	}
}
