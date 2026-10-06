package dev.relay.gui;

import java.util.List;

import dev.relay.RelayClient;
import dev.relay.RelayClientMod;
import dev.relay.RelayConfig;

import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Sizing;

/**
 * Combat helpers. Autpot throws the first Instant Health II splash on the hotbar.
 */
final class CombatModule implements RelayModule {
	@Override
	public String id() {
		return "combat";
	}

	@Override
	public String name() {
		return "Combat";
	}

	@Override
	public String description() {
		return "";
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
		return List.of(new HotkeyBinding(id(), name(), "Autpot", RelayClientMod::autopotKey));
	}

	@Override
	public String uiState() {
		RelayConfig config = RelayClient.get().config();
		return config.autopot() + "|" + config.autopotRefill() + "|" + RelayClientMod.keyHint(RelayClientMod.autopotKey());
	}

	@Override
	public void render(FlowLayout content, RelayScreen overlay) {
		RelayConfig config = RelayClient.get().config();
		FlowLayout inner = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
		inner.gap(2);
		inner.child(RelayUi.title(name()));
		inner.child(RelayUi.section("Autpot"));
		overlay.toggleRow(inner, "Enabled",
				"Throw the leftmost Instant Health II splash on the hotbar, then return to the previous slot.",
				RelayClientMod.keyHint(RelayClientMod.autopotKey()),
				config.autopot(), true, config::setAutopot);
		overlay.toggleRow(inner, "Auto refill",
				"After every second throw, move one or two Instant Health II potions from your inventory into empty hotbar slots.",
				"",
				config.autopotRefill(), true, config::setAutopotRefill);
		content.child(RelayUi.pageScroll(inner));
	}
}
