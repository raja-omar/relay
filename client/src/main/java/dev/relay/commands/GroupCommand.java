package dev.relay.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.relay.ModInfo;
import dev.relay.chat.ChatMessages;
import dev.relay.common.GroupNames;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

/**
 * The {@code /group} command. Client-side only: it never reaches the Minecraft server you are
 * playing on.
 *
 * <p>There is no socket connection yet, so every subcommand reports the honest failure state.
 */
public final class GroupCommand {
	/** @return the registered node, so {@code /relay group} can point at the same tree. */
	public static LiteralCommandNode<FabricClientCommandSource> register(
			CommandDispatcher<FabricClientCommandSource> dispatcher) {
		LiteralCommandNode<FabricClientCommandSource> node = dispatcher.register(
				ClientCommandManager.literal("group")
						.executes(context -> showInfo(context.getSource()))
						.then(ClientCommandManager.literal("info")
								.executes(context -> showInfo(context.getSource())))
						.then(ClientCommandManager.literal("create")
								.then(ClientCommandManager.argument("name", StringArgumentType.word())
										.executes(context -> create(
												context.getSource(),
												StringArgumentType.getString(context, "name"))))));

		ModInfo.LOG.debug("Registered /group");
		return node;
	}

	static int showInfo(FabricClientCommandSource source) {
		source.sendFeedback(ChatMessages.error(
				"Not connected to a " + ModInfo.NAME + " server, so there is no group to show."));
		return 1;
	}

	private static int create(FabricClientCommandSource source, String name) {
		if (!GroupNames.isValid(name)) {
			source.sendFeedback(ChatMessages.error("\"" + name + "\" is not a usable group name."));
			source.sendFeedback(ChatMessages.info(GroupNames.RULES));
			return 0;
		}

		source.sendFeedback(ChatMessages.error(
				"Not connected to a " + ModInfo.NAME + " server, so \"" + name + "\" was not created."));
		return 1;
	}

	private GroupCommand() {
	}
}
