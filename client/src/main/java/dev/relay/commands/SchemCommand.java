package dev.relay.commands;

import java.util.List;
import java.util.UUID;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.relay.RelayClient;
import dev.relay.chat.ChatMessages;
import dev.relay.litematica.LitematicaIntegration;
import dev.relay.schematic.SchematicException;
import dev.relay.schematic.SchematicFile;
import dev.relay.schematic.SchematicLibrary;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

/**
 * The {@code schem} half of {@code /relay}: look at the schematics Litematica already has on disk.
 *
 * <p>Deliberately not registered as a bare {@code /schem}. These are client commands, so a bare
 * {@code /schem} would swallow anything of that name on the multiplayer server the player is on.
 *
 * <p>Sharing is not a command. It lives on the Share button in Litematica's Schematic Placements
 * list, so the thing that goes out is the placement already in the world. Download is a command
 * because the chat button has to run something.
 */
public final class SchemCommand {
	static LiteralArgumentBuilder<FabricClientCommandSource> node() {
		return ClientCommandManager.literal("schem")
				.executes(context -> list(context.getSource()))
				.then(ClientCommandManager.literal("list")
						.executes(context -> list(context.getSource())))
				.then(ClientCommandManager.literal("info")
						.then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
								.executes(context -> info(context.getSource(),
										StringArgumentType.getString(context, "name")))))
				.then(ClientCommandManager.literal("download")
						.then(ClientCommandManager.argument("id", StringArgumentType.word())
								.executes(context -> download(context.getSource(),
										StringArgumentType.getString(context, "id")))));
	}

	private static int list(FabricClientCommandSource source) {
		SchematicLibrary library = library(source);

		if (library == null) {
			return 0;
		}

		List<SchematicFile> files;

		try {
			files = library.list();
		} catch (SchematicException failed) {
			source.sendFeedback(ChatMessages.error(failed.getMessage()));
			return 0;
		}

		if (files.isEmpty()) {
			source.sendFeedback(ChatMessages.info("No schematics in the Litematica folder."));
			source.sendFeedback(ChatMessages.info("Save one with Litematica first."));
			return 1;
		}

		source.sendFeedback(ChatMessages.schematicList(files));
		return 1;
	}

	private static int info(FabricClientCommandSource source, String name) {
		SchematicLibrary library = library(source);

		if (library == null) {
			return 0;
		}

		try {
			SchematicFile file = library.require(name);
			library.validate(file);
			source.sendFeedback(ChatMessages.schematicInfo(file));
			return 1;
		} catch (SchematicException failed) {
			source.sendFeedback(ChatMessages.error(failed.getMessage()));
			return 0;
		}
	}

	private static int download(FabricClientCommandSource source, String rawId) {
		if (!LitematicaIntegration.isAvailable()) {
			source.sendFeedback(ChatMessages.error("Litematica is not installed, so schematics cannot be shared"));
			return 0;
		}

		UUID transferId;

		try {
			transferId = UUID.fromString(rawId);
		} catch (IllegalArgumentException bad) {
			source.sendFeedback(ChatMessages.shareUnavailable());
			return 0;
		}

		RelayClient.get().downloadShare(transferId);
		return 1;
	}

	private static SchematicLibrary library(FabricClientCommandSource source) {
		if (!LitematicaIntegration.isAvailable()) {
			source.sendFeedback(ChatMessages.error("Litematica is not installed, so schematics cannot be shared"));
			return null;
		}

		return new SchematicLibrary(LitematicaIntegration.schematicsDirectory());
	}

	private SchemCommand() {
	}
}
