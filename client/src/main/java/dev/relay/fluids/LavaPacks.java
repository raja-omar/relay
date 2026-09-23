package dev.relay.fluids;

import java.util.LinkedHashSet;
import java.util.List;

import dev.relay.ModInfo;

import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.PackRepository;

/**
 * Translucent lava needs I See Lava's alpha textures because Sodium never sees vertex
 * alpha from {@code LiquidBlockRenderer}. Off does not use a pack: the mesh is skipped
 * and chunks are rebuilt.
 */
public final class LavaPacks {
	static final String TRANSLUCENT_PACK = ModInfo.ID + ":translucent_lava";
	/** Left in selected lists from older jars. Always stripped; never registered. */
	static final String HIDDEN_PACK = ModInfo.ID + ":hidden_lava";

	private LavaPacks() {
	}

	public static void register() {
		FabricLoader.getInstance().getModContainer(ModInfo.ID).ifPresent(container -> {
			ResourceLoader.registerBuiltinPack(
					Identifier.fromNamespaceAndPath(ModInfo.ID, "translucent_lava"),
					container,
					Component.literal("Relay Translucent Lava"),
					PackActivationType.NORMAL);
		});
	}

	public static void apply(LavaVisibility mode) {
		try {
			Minecraft client = Minecraft.getInstance();
			if (client == null) {
				return;
			}

			// Hidden only skips the mesh. Leave whatever pack is already selected so
			// See-through ↔ Hidden does not reload resources.
			if (mode == LavaVisibility.TRANSPARENT) {
				LavaVisibility.refreshChunks();
				return;
			}

			PackRepository repo = client.getResourcePackRepository();
			List<String> before = List.copyOf(repo.getSelectedIds());
			LinkedHashSet<String> selected = new LinkedHashSet<>(before);
			selected.remove(HIDDEN_PACK);
			if (mode == LavaVisibility.TRANSLUCENT) {
				selected.add(TRANSLUCENT_PACK);
			} else {
				selected.remove(TRANSLUCENT_PACK);
			}

			boolean packsChanged = !selected.equals(new LinkedHashSet<>(before));
			if (packsChanged) {
				repo.setSelected(selected);
				if (client.options != null) {
					client.options.updateResourcePacks(repo);
				}
			}
			LavaVisibility.refreshChunks();
		} catch (Throwable problem) {
			ModInfo.LOG.warn("Could not apply lava resource packs", problem);
			LavaVisibility.refreshChunks();
		}
	}
}
