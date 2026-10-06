package dev.relay.gui;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;

import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.UIComponent;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;

import org.lwjgl.glfw.GLFW;

/**
 * Clickable pane whose hover follows the pointer bounding box, not owo's deepest-child hover.
 * Labels no longer steal the highlight, and a rebuild while the mouse is still over the control
 * keeps it lit.
 */
final class HoverPane extends FlowLayout {
	private static final Map<HoverPane, Boolean> HELD = new IdentityHashMap<>();

	private int idleFill;
	private int hoverFill;
	private int pressFill;
	private int edge;
	private int radius;
	private Surface extra;
	private float amount;
	private boolean seeded;
	private boolean down;
	private long lastNs;
	private Runnable action;
	private boolean repeat;
	private long nextRepeatNs;

	private HoverPane(Sizing horizontal, Sizing vertical, Algorithm algorithm) {
		super(horizontal, vertical, algorithm);
	}

	static HoverPane horizontal(Sizing horizontal, Sizing vertical) {
		return new HoverPane(horizontal, vertical, Algorithm.HORIZONTAL);
	}

	static HoverPane vertical(Sizing horizontal, Sizing vertical) {
		return new HoverPane(horizontal, vertical, Algorithm.VERTICAL);
	}

	static void tickHolds() {
		if (HELD.isEmpty()) {
			return;
		}

		long now = System.nanoTime();
		for (HoverPane pane : new ArrayList<>(HELD.keySet())) {
			if (!pane.hasParent() || !pane.down || pane.action == null || !Pointers.leftHeld()
					|| !Pointers.over(pane)) {
				pane.down = false;
				HELD.remove(pane);
				continue;
			}

			if (now >= pane.nextRepeatNs) {
				pane.action.run();
				pane.nextRepeatNs = now + 70_000_000L;
			}
		}
	}

	static void clearHolds() {
		for (HoverPane pane : HELD.keySet()) {
			pane.down = false;
		}
		HELD.clear();
	}

	HoverPane interact(int idleFill, int hoverFill, int pressFill, int edge, int radius,
			Runnable action, Runnable activate, boolean repeat) {
		return interact(idleFill, hoverFill, pressFill, edge, radius, null, action, activate, repeat);
	}

	HoverPane interact(int idleFill, int hoverFill, int pressFill, int edge, int radius, Surface extra,
			Runnable action, Runnable activate, boolean repeat) {
		this.idleFill = idleFill;
		this.hoverFill = hoverFill;
		this.pressFill = pressFill;
		this.edge = edge;
		this.radius = radius;
		this.extra = extra;
		this.action = action;
		this.repeat = repeat;
		this.surface((context, component) -> {
			boolean over = Pointers.over(component);
			advance(over);
			boolean held = down && Pointers.leftHeld();
			if (!held) {
				down = false;
			}

			int fill = RelayTheme.blend(this.idleFill, this.hoverFill, amount);
			if (held && over) {
				fill = this.pressFill;
			}

			RelayTheme.drawRoundRect(context, component.x(), component.y(), component.width(),
					component.height(), this.radius, fill, this.edge);
			if (this.extra != null) {
				this.extra.draw(context, component);
			}
		});
		this.mouseDown().subscribe((event, doubled) -> {
			if (event.button() != 0) {
				return false;
			}

			down = true;
			if (doubled && activate != null) {
				activate.run();
				return true;
			}

			if (this.action != null) {
				this.action.run();
				if (this.repeat) {
					nextRepeatNs = System.nanoTime() + 280_000_000L;
					HELD.put(this, Boolean.TRUE);
				}
			}

			return true;
		});
		this.mouseUp().subscribe(event -> {
			if (event.button() != 0) {
				return false;
			}

			down = false;
			HELD.remove(this);
			return true;
		});
		return this;
	}

	private void advance(boolean over) {
		float target = over ? 1.0F : 0.0F;
		if (!seeded) {
			amount = HoverMotion.seed(over);
			seeded = true;
			lastNs = System.nanoTime();
			return;
		}

		long now = System.nanoTime();
		float delta = (now - lastNs) / 50_000_000.0F;
		lastNs = now;
		if (delta > 4.0F) {
			delta = 4.0F;
		}

		amount = HoverMotion.approach(amount, target, delta, HoverMotion.HOVER_MS);
	}

	/** GUI-space pointer, so hover still works after a rebuild while the mouse has not moved. */
	static final class Pointers {
		private Pointers() {
		}

		static boolean over(UIComponent component) {
			Minecraft client = Minecraft.getInstance();
			if (client == null || client.getWindow() == null) {
				return false;
			}

			MouseHandler mouse = client.mouseHandler;
			double mx = mouse.getScaledXPos(client.getWindow());
			double my = mouse.getScaledYPos(client.getWindow());
			return component.isInBoundingBox(mx, my);
		}

		static boolean leftHeld() {
			Minecraft client = Minecraft.getInstance();
			if (client == null || client.getWindow() == null) {
				return false;
			}

			return GLFW.glfwGetMouseButton(client.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT)
					== GLFW.GLFW_PRESS;
		}
	}
}
