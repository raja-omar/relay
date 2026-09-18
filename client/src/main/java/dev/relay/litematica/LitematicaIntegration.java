package dev.relay.litematica;

import java.util.Optional;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;

/**
 * The one place in this mod that knows about Litematica.
 *
 * <p>Everything Litematica-specific belongs here, so a Litematica update can only ever break a
 * single file. Right now it only answers "is Litematica installed, and which build?"; finding,
 * reading and writing schematic files gets added in a later phase.
 */
public final class LitematicaIntegration {
	public static final String LITEMATICA_MOD_ID = "litematica";

	public static boolean isAvailable() {
		return FabricLoader.getInstance().isModLoaded(LITEMATICA_MOD_ID);
	}

	/** The installed Litematica version, or empty when Litematica is not installed. */
	public static Optional<String> version() {
		return FabricLoader.getInstance()
				.getModContainer(LITEMATICA_MOD_ID)
				.map(ModContainer::getMetadata)
				.map(ModMetadata::getVersion)
				.map(Object::toString);
	}

	private LitematicaIntegration() {
	}
}
