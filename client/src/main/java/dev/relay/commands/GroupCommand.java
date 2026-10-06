package dev.relay.commands;

import java.util.function.Predicate;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.relay.ModInfo;
import dev.relay.RelayClient;
import dev.relay.chat.ChatMessages;
import dev.relay.common.GroupNames;
import dev.relay.common.PlayerNames;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

/**
 * The {@code group} half of {@code /relay}: show the group, make one, invite people, answer
 * invitations, leave.
 *
 * <p>Deliberately not registered as a bare {@code /group}. These are client commands, so a bare
 * {@code /group} would swallow the {@code /group} belonging to whatever multiplayer server the
 * player is on, and faction servers tend to have one.
 *
 * <p>Nothing here decides anything. The server owns the rules, so each command checks only what it
 * can answer on its own -- is the name usable, are we connected -- and leaves the rest to the reply.
 */
public final class GroupCommand {
	static LiteralArgumentBuilder<FabricClientCommandSource> node() {
		return ClientCommandManager.literal("group")
				.executes(context -> showInfo(context.getSource()))
				.then(ClientCommandManager.literal("info")
						.executes(context -> showInfo(context.getSource())))
				.then(ClientCommandManager.literal("create")
						.then(ClientCommandManager.argument("name", StringArgumentType.word())
								.executes(context -> create(context.getSource(), name(context)))))
				.then(ClientCommandManager.literal("invite")
						.then(ClientCommandManager.argument("player", StringArgumentType.word())
								.executes(context -> invite(context.getSource(),
										StringArgumentType.getString(context, "player")))))
				.then(ClientCommandManager.literal("accept")
						.then(ClientCommandManager.argument("name", StringArgumentType.word())
								.executes(context -> answerInvite(context.getSource(), name(context), true))))
				.then(ClientCommandManager.literal("decline")
						.then(ClientCommandManager.argument("name", StringArgumentType.word())
								.executes(context -> answerInvite(context.getSource(), name(context), false))))
				.then(ClientCommandManager.literal("leave")
						.executes(context -> leave(context.getSource())));
	}

	private static int showInfo(FabricClientCommandSource source) {
		RelayClient relay = RelayClient.get();

		if (!relay.isSignedIn()) {
			return notConnected(source);
		}

		relay.group().ifPresentOrElse(
				group -> source.sendFeedback(ChatMessages.groupInfo(group)),
				() -> {
					source.sendFeedback(ChatMessages.info("You are not in a group."));
					source.sendFeedback(ChatMessages.info("Make one with /" + ModInfo.ID
							+ " group create <name>, or wait for an invitation."));
				});

		// The list is only as fresh as the last update, so ask for a new one for next time.
		relay.refreshGroup();
		return 1;
	}

	private static int create(FabricClientCommandSource source, String name) {
		if (!checked(source, name, GroupNames::isValid, "\"" + name + "\" is not a usable group name.",
				GroupNames.RULES)) {
			return 0;
		}

		return send(source, RelayClient.get().createGroup(name));
	}

	private static int invite(FabricClientCommandSource source, String playerName) {
		if (!checked(source, playerName, PlayerNames::isValid, "\"" + playerName + "\" is not a player name.",
				"Use the player's Minecraft name, as it appears in the tab list.")) {
			return 0;
		}

		return send(source, RelayClient.get().invite(playerName));
	}

	private static int answerInvite(FabricClientCommandSource source, String groupName, boolean accept) {
		RelayClient relay = RelayClient.get();
		return send(source, accept ? relay.accept(groupName) : relay.decline(groupName));
	}

	private static int leave(FabricClientCommandSource source) {
		return send(source, RelayClient.get().leave());
	}

	/** Reports the one failure the client can be sure of on its own. */
	private static int send(FabricClientCommandSource source, boolean sent) {
		return sent ? 1 : notConnected(source);
	}

	private static int notConnected(FabricClientCommandSource source) {
		source.sendFeedback(ChatMessages.error("Not connected to a schematic server."));
		source.sendFeedback(ChatMessages.info("Connect with /" + ModInfo.ID + " connect <host>."));
		return 0;
	}

	private static boolean checked(FabricClientCommandSource source, String value, Predicate<String> isValid,
			String complaint, String help) {
		if (isValid.test(value)) {
			return true;
		}

		source.sendFeedback(ChatMessages.error(complaint));
		source.sendFeedback(ChatMessages.info(help));
		return false;
	}

	private static String name(CommandContext<FabricClientCommandSource> context) {
		return StringArgumentType.getString(context, "name");
	}

	private GroupCommand() {
	}
}
