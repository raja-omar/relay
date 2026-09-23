package dev.relay.fluids;

import java.util.Locale;

import dev.relay.ModInfo;
import dev.relay.RelayClient;

import net.minecraft.client.Minecraft;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.FluidState;

/**
 * How lava is drawn. Translucent is the I See Lava approach: lava goes through the translucent
 * chunk pass with a uniform alpha of {@code 120 / 255}, which is what that mod's textures used.
 *
 * <p>Fabric 1.21.11 no longer ships {@code BlockRenderLayerMap}, so the layer change is a mixin
 * instead of {@code putFluid}. Vertex colour carries the same alpha those textures did, which
 * the terrain shader multiplies with the vanilla lava sprite.
 */
public enum LavaVisibility {
	NORMAL("Vanilla"),
	TRANSLUCENT("See-through"),
	TRANSPARENT("Hidden");

	/** Uniform alpha from I See Lava's {@code lava_still.png} / {@code lava_flow.png}. */
	public static final float TRANSLUCENT_ALPHA = 120.0F / 255.0F;

	private static final ThreadLocal<Boolean> TRANSLUCENT_MESH = ThreadLocal.withInitial(() -> Boolean.FALSE);

	private final String label;

	LavaVisibility(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}

	public String id() {
		return this == TRANSPARENT ? "off" : name().toLowerCase(Locale.ROOT);
	}

	public LavaVisibility next() {
		return values()[(ordinal() + 1) % values().length];
	}

	public LavaVisibility previous() {
		return values()[(ordinal() + values().length - 1) % values().length];
	}

	public static LavaVisibility fromName(String raw, LavaVisibility fallback) {
		if (raw == null || raw.isBlank()) {
			return fallback;
		}

		return switch (raw.trim().toLowerCase(Locale.ROOT)) {
			case "normal", "vanilla", "opaque" -> NORMAL;
			case "translucent", "see-thru", "see-through", "seethrough", "glass" -> TRANSLUCENT;
			case "transparent", "hidden", "off", "none", "clear" -> TRANSPARENT;
			default -> fallback;
		};
	}

	/** Safe to call from mixins before the client exists, and from chunk compile threads. */
	public static LavaVisibility current() {
		try {
			return RelayClient.get().config().lavaVisibility();
		} catch (Throwable failed) {
			return NORMAL;
		}
	}

	public static boolean hidesLava() {
		return current() == TRANSPARENT;
	}

	public static boolean seeThroughLava() {
		return current() == TRANSLUCENT;
	}

	/** Translucent lava needs the translucent chunk pass so texture alpha blends. */
	public static boolean usesTranslucentPass() {
		return current() == TRANSLUCENT;
	}

	public static void set(LavaVisibility visibility) {
		RelayClient.get().config().setLavaVisibility(visibility);
		LavaPacks.apply(current());
	}

	public static void beginMesh(FluidState fluid) {
		TRANSLUCENT_MESH.set(fluid != null && fluid.is(FluidTags.LAVA) && current() == TRANSLUCENT);
	}

	public static void endMesh() {
		TRANSLUCENT_MESH.set(Boolean.FALSE);
	}

	public static float meshAlpha(float original) {
		return Boolean.TRUE.equals(TRANSLUCENT_MESH.get()) ? TRANSLUCENT_ALPHA : original;
	}

	public static void refreshChunks() {
		try {
			Minecraft client = Minecraft.getInstance();
			if (client != null && client.levelRenderer != null) {
				client.levelRenderer.allChanged();
			}
		} catch (Throwable problem) {
			ModInfo.LOG.debug("Could not rebuild chunks after a lava visibility change", problem);
		}
	}

	public static String hint() {
		return "Vanilla is the usual look. See-through lets you look through lava. "
				+ "Hidden turns lava off. Only Vanilla ↔ See-through reloads a pack.";
	}
}
