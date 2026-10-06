package dev.relay.gui;

import dev.relay.RelayClient;

import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.VerticalAlignment;

/**
 * Relay ID — header Settings, not a feature module.
 */
final class SettingsView {
	private SettingsView() {
	}

	static void render(FlowLayout content, RelayScreen overlay) {
		FlowLayout inner = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
		inner.gap(8);
		inner.child(RelayUi.title("Settings"));
		inner.child(RelayUi.heading("Relay ID"));
		inner.child(RelayUi.muted("Signs you in to the Relay server. Groups live in their own tab."));
		login(inner, overlay);
		content.child(RelayUi.pageScroll(inner));
	}

	private static void login(FlowLayout content, RelayScreen overlay) {
		overlay.ensureTokenDraft();
		FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
		row.gap(6);
		row.verticalAlignment(VerticalAlignment.CENTER);
		row.child(overlay.field(Sizing.expand(), "rly_…", overlay.tokenDraft(), 80, null,
				overlay::tokenDraft, () -> RelayClient.get().login(overlay.tokenDraft().trim())));
		row.child(RelayUi.button("Login", RelayUi.Kind.PRIMARY, true, "",
				() -> RelayClient.get().login(overlay.tokenDraft().trim())));
		content.child(row);
	}
}
