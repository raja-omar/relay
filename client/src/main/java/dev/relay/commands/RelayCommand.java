package dev.relay.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.relay.ModInfo;
import dev.relay.RelayClient;
import dev.relay.RelayConfig;
import dev.relay.chat.ChatMessages;
import dev.relay.common.Tls;
import dev.relay.gui.RelayScreen;
import dev.relay.litematica.LitematicaIntegration;
import dev.relay.network.SocketClient;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

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
				.then(ClientCommandManager.literal("connect")
						.executes(context -> connect(context.getSource()))
						.then(ClientCommandManager.argument("host", StringArgumentType.string())
								.executes(context -> connectTo(context.getSource(),
										StringArgumentType.getString(context, "host"),
										RelayConfig.DEFAULT_PORT))
								.then(ClientCommandManager.argument("port", IntegerArgumentType.integer(1, 65535))
										.executes(context -> connectTo(context.getSource(),
												StringArgumentType.getString(context, "host"),
												IntegerArgumentType.getInteger(context, "port")))
										.then(ClientCommandManager.argument("pin", StringArgumentType.greedyString())
												.executes(context -> connectTo(context.getSource(),
														StringArgumentType.getString(context, "host"),
														IntegerArgumentType.getInteger(context, "port"),
														StringArgumentType.getString(context, "pin")))))))
				.then(ClientCommandManager.literal("disconnect")
						.executes(context -> disconnect(context.getSource())))
				.then(ClientCommandManager.literal("login")
						.then(ClientCommandManager.argument("id", StringArgumentType.greedyString())
								.executes(context -> login(context.getSource(),
										StringArgumentType.getString(context, "id")))))
				.then(ClientCommandManager.literal("gui")
						.executes(context -> openGui()))
				.then(GroupCommand.node())
				.then(SchemCommand.node())
				.then(ShopCommand.cantMissNode())
				.then(ShopCommand.node())
				.then(ShopCommand.refillNode())
				.then(PatchcrumbsCommand.node())
				.then(CannonCommand.node())
				.then(FluidsCommand.node()));

		ModInfo.LOG.debug("Registered /{}", ModInfo.ID);
	}

	private static int showStatus(FabricClientCommandSource source) {
		RelayClient relay = RelayClient.get();

		source.sendFeedback(ChatMessages.info(ModInfo.NAME + " " + ModInfo.version()));

		source.sendFeedback(LitematicaIntegration.isAvailable()
				? ChatMessages.success("Litematica " + LitematicaIntegration.version().orElse("") + " detected")
				: ChatMessages.error("Litematica is not installed, so schematics cannot be shared"));

		source.sendFeedback(describeConnection(relay));
		source.sendFeedback(describeLogin(relay.config()));
		source.sendFeedback(describePlacement(relay.config()));

		relay.group().ifPresentOrElse(
				group -> source.sendFeedback(ChatMessages.groupInfo(group)),
				() -> source.sendFeedback(ChatMessages.info("Not in a group.")));

		return 1;
	}

	private static Component describeConnection(RelayClient relay) {
		RelayConfig config = relay.config();

		if (!config.hasServer()) {
			return ChatMessages.error("No schematic server set. Use /" + ModInfo.ID + " connect <host>.");
		}

		SocketClient.Status status = relay.status();

		return switch (status) {
			case CONNECTED -> relay.isSignedIn()
					? ChatMessages.success("Connected to " + relay.address())
					: ChatMessages.info("Signing in to " + relay.address() + "...");
			case CONNECTING -> ChatMessages.info("Connecting to " + config.address() + "...");
			case DISCONNECTED -> ChatMessages.error("Not connected to " + config.address());
		};
	}

	private static int connect(FabricClientCommandSource source) {
		RelayClient.get().connect();
		return 1;
	}

	private static int connectTo(FabricClientCommandSource source, String host, int port) {
		if (host.isBlank()) {
			source.sendFeedback(ChatMessages.error("Give the address of the schematic server."));
			return 0;
		}

		source.sendFeedback(ChatMessages.info("Connecting to " + host + ":" + port + "..."));
		RelayClient.get().connectTo(host, port);
		return 1;
	}

	private static int connectTo(FabricClientCommandSource source, String host, int port, String tlsPin) {
		if (host.isBlank()) {
			source.sendFeedback(ChatMessages.error("Give the address of the schematic server."));
			return 0;
		}

		if (!Tls.isWellFormedPin(tlsPin)) {
			source.sendFeedback(ChatMessages.error("That TLS pin is not a SHA-256 fingerprint."));
			return 0;
		}

		source.sendFeedback(ChatMessages.info("Connecting to " + host + ":" + port + "..."));
		RelayClient.get().connectTo(host, port, tlsPin);
		return 1;
	}

	private static int disconnect(FabricClientCommandSource source) {
		RelayClient.get().disconnect();
		return 1;
	}

	private static int login(FabricClientCommandSource source, String token) {
		RelayClient.get().login(token);
		return 1;
	}

	private static int openGui() {
		RelayScreen.openNextTick();
		return 1;
	}

	private static Component describeLogin(RelayConfig config) {
		return config.hasToken()
				? ChatMessages.info("Player id is set.")
				: ChatMessages.error("No player id. Use /" + ModInfo.ID + " login <id>.");
	}

	private static Component describePlacement(RelayConfig config) {
		return ChatMessages.info("cantMiss " + onOff(config.cantMiss())
				+ ", autoPurchase " + onOff(config.autoPurchase())
				+ ", prePurchase " + onOff(config.prePurchase())
				+ ", autoRefill " + onOff(config.autoRefill()));
	}

	private static String onOff(boolean enabled) {
		return enabled ? "on" : "off";
	}

	private RelayCommand() {
	}
}
