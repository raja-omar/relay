package dev.relay.patchcrumbs;

import dev.relay.RelayClient;
import dev.relay.RelayConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Shares the current crumb over chat as a callout.
 */
public final class Callouts {
	private static long lastCall;

	private Callouts() {
	}

	public static void onClientTick(Minecraft client) {
		RelayConfig cfg = RelayClient.get().config();
		if (!cfg.patchcrumbsCallouts() || !cfg.patchcrumbsConsistentCallouts()) {
			return;
		}
		if (client.level == null || client.player == null) {
			return;
		}
		long interval = (long) cfg.patchcrumbsCalloutTimeout() * 1000L;
		if (System.currentTimeMillis() - interval > lastCall) {
			lastCall = System.currentTimeMillis();
			calloutShot(client);
		}
	}

	public static void calloutShot(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null) {
			return;
		}
		PatchCrumb crumb = PatchCrumbs.currentCrumb;
		if (crumb == null) {
			return;
		}
		RelayConfig cfg = RelayClient.get().config();
		String body = "[OrbitCallout] x:" + crumb.posX + " y:" + crumb.posY + " z:" + crumb.posZ;
		if (cfg.patchcrumbsShareUsingFf()) {
			player.connection.sendCommand("ff " + body);
		} else {
			player.connection.sendChat(body);
		}
	}
}
