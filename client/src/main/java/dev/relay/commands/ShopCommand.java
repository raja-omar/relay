package dev.relay.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.relay.RelayClient;
import dev.relay.RelayConfig;
import dev.relay.chat.ChatMessages;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

/**
 * The shop half of {@code /relay}: turn cant-miss auto-buy and pre-buy on or off.
 *
 * <p>These only affect the server's {@code /shop} command. Nothing is sent until a schematic place
 * actually needs material.
 */
final class ShopCommand {
	static LiteralArgumentBuilder<FabricClientCommandSource> node() {
		return ClientCommandManager.literal("shop")
				.executes(context -> show(context.getSource()))
				.then(flag("auto", Flag.AUTO))
				.then(flag("pre", Flag.PRE));
	}

	static LiteralArgumentBuilder<FabricClientCommandSource> refillNode() {
		return ClientCommandManager.literal("refill")
				.executes(context -> showRefill(context.getSource()))
				.then(ClientCommandManager.literal("on")
						.executes(context -> setRefill(context.getSource(), true)))
				.then(ClientCommandManager.literal("off")
						.executes(context -> setRefill(context.getSource(), false)));
	}

	static LiteralArgumentBuilder<FabricClientCommandSource> cantMissNode() {
		return ClientCommandManager.literal("cantmiss")
				.executes(context -> showCantMiss(context.getSource()))
				.then(ClientCommandManager.literal("on")
						.executes(context -> setCantMiss(context.getSource(), true)))
				.then(ClientCommandManager.literal("off")
						.executes(context -> setCantMiss(context.getSource(), false)));
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> flag(String name, Flag flag) {
		return ClientCommandManager.literal(name)
				.executes(context -> show(context.getSource()))
				.then(ClientCommandManager.literal("on")
						.executes(context -> setShop(context.getSource(), flag, true)))
				.then(ClientCommandManager.literal("off")
						.executes(context -> setShop(context.getSource(), flag, false)));
	}

	private static int show(FabricClientCommandSource source) {
		RelayConfig config = RelayClient.get().config();
		source.sendFeedback(ChatMessages.info("autoPurchase is " + onOff(config.autoPurchase())
				+ ". Buys a missing schematic block with /shop."));
		source.sendFeedback(ChatMessages.info("prePurchase is " + onOff(config.prePurchase())
				+ ". Tops the stack up after a successful place."));
		return 1;
	}

	private static int showCantMiss(FabricClientCommandSource source) {
		source.sendFeedback(ChatMessages.info("cantMiss is "
				+ onOff(RelayClient.get().config().cantMiss())
				+ ". Right-clicks only place when they match the schematic."));
		return 1;
	}

	private static int setCantMiss(FabricClientCommandSource source, boolean enabled) {
		RelayClient.get().config().setCantMiss(enabled);
		source.sendFeedback(ChatMessages.success("cantMiss " + onOff(enabled) + "."));
		return 1;
	}

	private static int showRefill(FabricClientCommandSource source) {
		source.sendFeedback(ChatMessages.info("autoRefill is "
				+ onOff(RelayClient.get().config().autoRefill())
				+ ". Emptied build-hotbar slots are restocked from the backpack."));
		return 1;
	}

	private static int setRefill(FabricClientCommandSource source, boolean enabled) {
		RelayClient.get().config().setAutoRefill(enabled);
		source.sendFeedback(ChatMessages.success("autoRefill " + onOff(enabled) + "."));
		return 1;
	}

	private static int setShop(FabricClientCommandSource source, Flag flag, boolean enabled) {
		if (flag == Flag.AUTO) {
			RelayClient.get().config().setAutoPurchase(enabled);
			source.sendFeedback(ChatMessages.success("autoPurchase " + onOff(enabled) + "."));
		} else {
			RelayClient.get().config().setPrePurchase(enabled);
			source.sendFeedback(ChatMessages.success("prePurchase " + onOff(enabled) + "."));
		}

		return 1;
	}

	private enum Flag {
		AUTO,
		PRE
	}

	private static String onOff(boolean enabled) {
		return enabled ? "on" : "off";
	}

	private ShopCommand() {
	}
}
