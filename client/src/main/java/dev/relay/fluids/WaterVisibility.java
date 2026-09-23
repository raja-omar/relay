package dev.relay.fluids;

import java.util.Locale;

import dev.relay.RelayClient;

import net.minecraft.client.Minecraft;

/**
 * How water is drawn. Hidden skips the mesh. Clear keeps the water but removes the
 * underwater fog, FOV, and overlay.
 */
public enum WaterVisibility {
	NORMAL("Vanilla"),
	CLEAR("Clear"),
	OFF("Hidden");

	private final String label;

	WaterVisibility(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}

	public String id() {
		return switch (this) {
			case NORMAL -> "vanilla";
			case CLEAR -> "clear";
			case OFF -> "hidden";
		};
	}

	public WaterVisibility next() {
		return values()[(ordinal() + 1) % values().length];
	}

	public WaterVisibility previous() {
		return values()[(ordinal() + values().length - 1) % values().length];
	}

	public static WaterVisibility fromName(String raw, WaterVisibility fallback) {
		if (raw == null || raw.isBlank()) {
			return fallback;
		}

		return switch (raw.trim().toLowerCase(Locale.ROOT)) {
			case "normal", "vanilla", "on", "show" -> NORMAL;
			case "clear", "clear-water", "fogless" -> CLEAR;
			case "off", "hidden", "transparent", "none", "hide" -> OFF;
			default -> fallback;
		};
	}

	public static WaterVisibility current() {
		try {
			return RelayClient.get().config().waterVisibility();
		} catch (Throwable failed) {
			return NORMAL;
		}
	}

	public static boolean hidesWater() {
		return current() == OFF;
	}

	public static boolean clearsView() {
		return current() == CLEAR || current() == OFF;
	}

	public static void set(WaterVisibility visibility) {
		WaterVisibility next = visibility == null ? NORMAL : visibility;
		RelayClient.get().config().setWaterVisibility(next);
		refreshChunks();
	}

	public static void refreshChunks() {
		try {
			Minecraft client = Minecraft.getInstance();
			if (client != null && client.levelRenderer != null) {
				client.levelRenderer.allChanged();
			}
		} catch (Throwable problem) {
			// Chunk rebuild is best-effort; the next movement rebuilds anyway.
		}
	}

	public static String hint() {
		return "Vanilla is the usual look. Clear keeps water visible but removes the "
				+ "underwater fog. Hidden turns water off.";
	}
}
