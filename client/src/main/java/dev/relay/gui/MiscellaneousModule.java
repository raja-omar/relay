package dev.relay.gui;

import java.util.List;

import dev.relay.RelayClient;
import dev.relay.RelayClientMod;
import dev.relay.RelayConfig;
import dev.relay.patchcrumbs.PatchCrumbsPolicy.Palette;
import dev.relay.ping.PingPolicy;

import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Sizing;

/**
 * Small extras that are not their own feature. Ping Block lives here.
 */
final class MiscellaneousModule implements RelayModule {
	@Override
	public String id() {
		return "miscellaneous";
	}

	@Override
	public String name() {
		return "Miscellaneous";
	}

	@Override
	public String description() {
		return "Ping a block to mark it with a beam.";
	}

	@Override
	public boolean available() {
		return true;
	}

	@Override
	public boolean utility() {
		return false;
	}

	@Override
	public int badge() {
		return 0;
	}

	@Override
	public List<HotkeyBinding> hotkeys() {
		return List.of(new HotkeyBinding(id(), name(), "Ping Block", RelayClientMod::pingBlockKey));
	}

	@Override
	public String uiState() {
		RelayConfig config = RelayClient.get().config();
		return config.pingMultiple() + "|" + config.pingShare() + "|"
				+ config.pingTimeout() + "|" + config.pingPalette();
	}

	@Override
	public void render(FlowLayout content, RelayScreen overlay) {
		RelayConfig config = RelayClient.get().config();
		FlowLayout inner = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
		inner.gap(2);
		inner.child(RelayUi.title(name()));
		inner.child(RelayUi.muted("Look at a block and press the ping key."));
		inner.child(RelayUi.section("Ping Block"));
		overlay.toggleRow(inner, "Multiple beams",
				"Keep every ping until the timer runs out. Off replaces the previous beam. This applies to your pings and to pings from the group.",
				RelayClientMod.keyHint(RelayClientMod.pingBlockKey()),
				config.pingMultiple(), true, config::setPingMultiple);
		overlay.toggleRow(inner, "Share pings",
				"Send each ping to your group. Everyone draws it with their own color, timer, and single or multiple setting. Off keeps the ping on this client.",
				"",
				config.pingShare(), true, config::setPingShare);
		overlay.stepperRow(inner, "Timer", PingPolicy.timeoutHint(),
				PingPolicy.timeoutLabel(config.pingTimeout()),
				PingPolicy.canLowerTimeout(config.pingTimeout()),
				PingPolicy.canRaiseTimeout(config.pingTimeout()),
				() -> config.setPingTimeout(config.pingTimeout() - 1),
				() -> config.setPingTimeout(config.pingTimeout() + 1));
		overlay.stepperRow(inner, "Color",
				"Color of the beam and the coordinates. RGB cycles through every hue.",
				config.pingPalette().label(),
				true, true,
				() -> config.setPingPalette(config.pingPalette().previous()),
				() -> config.setPingPalette(config.pingPalette().next()));
		content.child(RelayUi.pageScroll(inner));
	}
}
