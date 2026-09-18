package dev.relay;

import dev.relay.commands.GroupCommand;
import dev.relay.commands.RelayCommand;
import dev.relay.litematica.LitematicaIntegration;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;

/**
 * Entry point. Registers commands and reports what we found at startup; nothing here may block,
 * because Minecraft has to keep working whether or not the rest of the system is reachable.
 */
public final class RelayClientMod implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ModInfo.LOG.info("Starting {} {}", ModInfo.NAME, ModInfo.version());
		logLitematicaStatus();

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			RelayCommand.register(dispatcher, GroupCommand.register(dispatcher));
		});

		ModInfo.LOG.info("{} ready", ModInfo.NAME);
	}

	private static void logLitematicaStatus() {
		if (LitematicaIntegration.isAvailable()) {
			ModInfo.LOG.info("Found Litematica {}", LitematicaIntegration.version().orElse("(unknown version)"));
		} else {
			// Not fatal for the mod itself, but sharing schematics cannot work without it.
			ModInfo.LOG.warn("Litematica is not installed; schematic sharing will be unavailable");
		}
	}
}
