package dev.relay.gui;

import java.util.List;

import io.wispforest.owo.ui.container.FlowLayout;

/**
 * A first-class Relay feature. Navigation, content, and hotkeys all come from the same
 * registration so a new module does not require a shell rewrite.
 */
public interface RelayModule {
	String id();

	String name();

	String description();

	/** When false, the nav row is visible but inert. */
	boolean available();

	/** Utility entries (Hotkeys) sit below a divider. */
	boolean utility();

	int badge();

	List<HotkeyBinding> hotkeys();

	void render(FlowLayout content, RelayScreen overlay);

	/** Extra UI state folded into the overlay fingerprint. */
	default String uiState() {
		return "";
	}
}
