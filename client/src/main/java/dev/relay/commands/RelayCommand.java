package dev.relay.commands;

import com.mojang.brigadier.CommandDispatcher;
import dev.relay.ModInfo;
import dev.relay.chat.ChatMessages;
import dev.relay.litematica.LitematicaIntegration;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

/**
 * Everything this mod adds lives under {@code /relay}, so no command of the multiplayer server the
 * player is on is ever shadowed by ours.
 */
public final class RelayCommand {
	public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(ClientCommandManager.literal(ModInfo.ID)
				.executes(context -> showStatus(context.getSource()))
				.then(ClientCommandManager.literal("status")
						.executes(context -> showStatus(context.getSource())))
				.then(GroupCommand.node()));

		ModInfo.LOG.debug("Registered /{}", ModInfo.ID);
	}

	private static int showStatus(FabricClientCommandSource source) {
		source.sendFeedback(ChatMessages.info(ModInfo.NAME + " " + ModInfo.version()));

		source.sendFeedback(LitematicaIntegration.isAvailable()
				? ChatMessages.success("Litematica " + LitematicaIntegration.version().orElse("") + " detected")
				: ChatMessages.error("Litematica is not installed, so schematics cannot be shared"));

		// Phase 3 replaces this with the real socket connection state.
		source.sendFeedback(ChatMessages.error("Disconnected: no schematic server configured yet"));
		return 1;
	}

	private RelayCommand() {
	}
}
