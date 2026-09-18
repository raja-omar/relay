package dev.relay.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.relay.ModInfo;
import dev.relay.chat.ChatMessages;
import dev.relay.common.GroupNames;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

/**
 * The {@code group} half of {@code /relay}: {@code /relay group}, {@code ... info},
 * {@code ... create <name>}.
 *
 * <p>Deliberately not registered as a bare {@code /group}. These are client commands, so a bare
 * {@code /group} would swallow the {@code /group} belonging to whatever multiplayer server the
 * player is on, and faction servers tend to have one.
 *
 * <p>There is no socket connection yet, so every subcommand reports the honest failure state.
 */
public final class GroupCommand {
	static LiteralArgumentBuilder<FabricClientCommandSource> node() {
		return ClientCommandManager.literal("group")
				.executes(context -> showInfo(context.getSource()))
				.then(ClientCommandManager.literal("info")
						.executes(context -> showInfo(context.getSource())))
				.then(ClientCommandManager.literal("create")
						.then(ClientCommandManager.argument("name", StringArgumentType.word())
								.executes(context -> create(
										context.getSource(),
										StringArgumentType.getString(context, "name")))));
	}

	private static int showInfo(FabricClientCommandSource source) {
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
