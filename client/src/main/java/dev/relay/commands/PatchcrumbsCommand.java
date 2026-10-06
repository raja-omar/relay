package dev.relay.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.relay.RelayClient;
import dev.relay.chat.ChatMessages;
import dev.relay.patchcrumbs.Callouts;
import dev.relay.patchcrumbs.CannonEntityTracker;
import dev.relay.patchcrumbs.PatchCrumb;
import dev.relay.patchcrumbs.PatchCrumbs;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;

/**
 * Turn the wall-shot marker on or off without opening the overlay.
 */
final class PatchcrumbsCommand {
	static LiteralArgumentBuilder<FabricClientCommandSource> node() {
		return ClientCommandManager.literal("patchcrumbs")
				.executes(context -> show(context.getSource()))
				.then(ClientCommandManager.literal("on")
						.executes(context -> set(context.getSource(), true)))
				.then(ClientCommandManager.literal("off")
						.executes(context -> set(context.getSource(), false)))
				.then(ClientCommandManager.literal("clear")
						.executes(context -> clear(context.getSource())))
				.then(ClientCommandManager.literal("callout")
						.executes(context -> callout()));
	}

	private static int show(FabricClientCommandSource source) {
		boolean on = RelayClient.get().config().patchcrumbs();
		PatchCrumb crumb = PatchCrumbs.currentCrumb;
		String crumbLine = crumb == null
				? "none"
				: ("x=" + crumb.posX + " y=" + crumb.posY + " z=" + crumb.posZ
				+ " (expires in " + Math.max(0, (crumb.expiresAt - System.currentTimeMillis()) / 1000) + "s)");
		source.sendFeedback(ChatMessages.info("Patchcrumbs is " + onOff(on)
				+ ". Marks the last TNT or falling-sand shot so you can patch the wall."));
		source.sendFeedback(ChatMessages.info("tracking TNT/falling: "
				+ CannonEntityTracker.INSTANCE.entityPositions.size()
				+ " | wall columns: " + PatchCrumbs.wallCoords.size()
				+ " | current crumb: " + crumbLine));
		return 1;
	}

	private static int set(FabricClientCommandSource source, boolean enabled) {
		RelayClient.get().config().setPatchcrumbs(enabled);
		source.sendFeedback(ChatMessages.success("Patchcrumbs " + onOff(enabled) + "."));
		return 1;
	}

	private static int clear(FabricClientCommandSource source) {
		PatchCrumbs.clearSession();
		source.sendFeedback(ChatMessages.success("Patchcrumbs cleared tracked crumbs."));
		return 1;
	}

	private static int callout() {
		Callouts.calloutShot(Minecraft.getInstance());
		return 1;
	}

	private static String onOff(boolean enabled) {
		return enabled ? "on" : "off";
	}

	private PatchcrumbsCommand() {
	}
}
