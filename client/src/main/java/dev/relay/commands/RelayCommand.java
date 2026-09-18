package dev.relay.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.relay.ModInfo;
import dev.relay.chat.ChatMessages;
import dev.relay.litematica.LitematicaIntegration;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

/**
 * The {@code /relay} command: a status readout, plus a namespaced way to reach the group commands.
 *
 * <p>{@code /relay group ...} matters because plenty of faction servers have their own
 * {@code /group}, and a client command wins over the server's. Anyone caught by that can use the
 * namespaced form instead.
 */
public final class RelayCommand {
	public static void register(
			CommandDispatcher<FabricClientCommandSource> dispatcher,
			LiteralCommandNode<FabricClientCommandSource> groupCommand) {
		dispatcher.register(ClientCommandManager.literal(ModInfo.ID)
				.executes(context -> showStatus(context.getSource()))
				.then(ClientCommandManager.literal("status")
						.executes(context -> showStatus(context.getSource())))
				.then(ClientCommandManager.literal("group")
						.executes(context -> GroupCommand.showInfo(context.getSource()))
						.redirect(groupCommand)));

		ModInfo.LOG.debug("Registered /{}", ModInfo.ID);
	}

	private static int showStatus(FabricClientCommandSource source) {
		source.sendFeedback(ChatMessages.info(ModInfo.NAME + " " + ModInfo.version()));

		source.sendFeedback(LitematicaIntegration.isAvailable()
				? ChatMessages.success("Litematica " + LitematicaIntegration.version().orElse("") + " detected")
				: ChatMessages.error("Litematica is not installed, so schematics cannot be shared"));

		// Phase 2 replaces this with the real socket connection state.
		source.sendFeedback(ChatMessages.error("Disconnected: no schematic server configured yet"));
		return 1;
	}

	private RelayCommand() {
	}
}
