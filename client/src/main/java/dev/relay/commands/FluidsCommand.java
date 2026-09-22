package dev.relay.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.relay.RelayClient;
import dev.relay.chat.ChatMessages;
import dev.relay.fluids.LavaVisibility;
import dev.relay.fluids.WaterVisibility;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

/**
 * Set lava and water looks without opening the overlay.
 */
final class FluidsCommand {
	static LiteralArgumentBuilder<FabricClientCommandSource> node() {
		return ClientCommandManager.literal("fluids")
				.executes(context -> show(context.getSource()))
				.then(ClientCommandManager.literal("lava")
						.executes(context -> show(context.getSource()))
						.then(lava("vanilla", LavaVisibility.NORMAL))
						.then(lava("normal", LavaVisibility.NORMAL))
						.then(lava("see-through", LavaVisibility.TRANSLUCENT))
						.then(lava("translucent", LavaVisibility.TRANSLUCENT))
						.then(lava("hidden", LavaVisibility.TRANSPARENT))
						.then(lava("off", LavaVisibility.TRANSPARENT))
						.then(lava("transparent", LavaVisibility.TRANSPARENT)))
				.then(ClientCommandManager.literal("water")
						.executes(context -> show(context.getSource()))
						.then(water("vanilla", WaterVisibility.NORMAL))
						.then(water("normal", WaterVisibility.NORMAL))
						.then(water("clear", WaterVisibility.CLEAR))
						.then(water("hidden", WaterVisibility.OFF))
						.then(water("off", WaterVisibility.OFF)));
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> lava(String name, LavaVisibility visibility) {
		return ClientCommandManager.literal(name)
				.executes(context -> setLava(context.getSource(), visibility));
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> water(String name, WaterVisibility visibility) {
		return ClientCommandManager.literal(name)
				.executes(context -> setWater(context.getSource(), visibility));
	}

	private static int show(FabricClientCommandSource source) {
		LavaVisibility lava = RelayClient.get().config().lavaVisibility();
		WaterVisibility water = RelayClient.get().config().waterVisibility();
		source.sendFeedback(ChatMessages.info("Lava is " + lava.id() + " (" + lava.label() + "). "
				+ "vanilla, see-through, or hidden."));
		source.sendFeedback(ChatMessages.info("Water is " + water.id() + " (" + water.label() + "). "
				+ "vanilla, clear, or hidden."));
		return 1;
	}

	private static int setLava(FabricClientCommandSource source, LavaVisibility visibility) {
		LavaVisibility.set(visibility);
		source.sendFeedback(ChatMessages.success("Lava " + visibility.label().toLowerCase() + "."));
		return 1;
	}

	private static int setWater(FabricClientCommandSource source, WaterVisibility visibility) {
		WaterVisibility.set(visibility);
		source.sendFeedback(ChatMessages.success("Water " + visibility.label().toLowerCase() + "."));
		return 1;
	}

	private FluidsCommand() {
	}
}
