package dev.relay;

import net.fabricmc.loader.api.FabricLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Who we are, in one place.
 *
 * <p>{@link #ID} must match {@code mod_id} and {@link #NAME} must match {@code mod_name} in
 * gradle.properties. Nothing else in the client should spell the mod's name out by hand.
 */
public final class ModInfo {
	public static final String ID = "relay";
	public static final String NAME = "Relay";

	/** Shared logger. Technical detail goes here, never into a player's chat. */
	public static final Logger LOG = LoggerFactory.getLogger(NAME);

	/** Our own version, read from the jar metadata rather than written down a second time. */
	public static String version() {
		return FabricLoader.getInstance()
				.getModContainer(ID)
				.map(container -> container.getMetadata().getVersion().toString())
				.orElse("(unknown version)");
	}

	private ModInfo() {
	}
}
