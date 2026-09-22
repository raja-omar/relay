package dev.relay.gui;

import java.util.List;

import dev.relay.RelayClient;
import dev.relay.RelayClientMod;
import dev.relay.RelayConfig;
import dev.relay.fluids.LavaVisibility;
import dev.relay.fluids.WaterVisibility;

import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Sizing;

/**
 * Client-only fluid drawing. Lava and water each have one look control.
 */
final class FluidsModule implements RelayModule {
	@Override
	public String id() {
		return "fluids";
	}

	@Override
	public String name() {
		return "Fluids";
	}

	@Override
	public String description() {
		return "Hide lava or water, make lava see-through, or swim without underwater fog.";
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
		return List.of(new HotkeyBinding(id(), name(), "Cycle Lava", RelayClientMod::lavaKey));
	}

	@Override
	public String uiState() {
		RelayConfig config = RelayClient.get().config();
		return config.lavaVisibility().id() + "/" + config.waterVisibility().id();
	}

	@Override
	public void render(FlowLayout content, RelayScreen overlay) {
		RelayConfig config = RelayClient.get().config();
		LavaVisibility lava = config.lavaVisibility();
		WaterVisibility water = config.waterVisibility();
		FlowLayout inner = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
		inner.gap(2);
		inner.child(RelayUi.title(name()));
		inner.child(RelayUi.muted("Each row is one fluid. Cycle how it looks, or hide it."));
		inner.child(RelayUi.muted(statusLine(lava, water)));
		overlay.stepperRow(inner, "Lava", LavaVisibility.hint(),
				lava.label(),
				true, true,
				() -> LavaVisibility.set(lava.previous()),
				() -> LavaVisibility.set(lava.next()),
				96);
		overlay.stepperRow(inner, "Water", WaterVisibility.hint(),
				water.label(),
				true, true,
				() -> WaterVisibility.set(water.previous()),
				() -> WaterVisibility.set(water.next()),
				96);
		content.child(RelayUi.pageScroll(inner));
	}

	private static String statusLine(LavaVisibility lava, WaterVisibility water) {
		return lavaStatus(lava) + " " + waterStatus(water);
	}

	private static String lavaStatus(LavaVisibility lava) {
		return switch (lava) {
			case NORMAL -> "Lava is vanilla.";
			case TRANSLUCENT -> "Lava is see-through.";
			case TRANSPARENT -> "Lava is hidden.";
		};
	}

	private static String waterStatus(WaterVisibility water) {
		return switch (water) {
			case NORMAL -> "Water is vanilla.";
			case CLEAR -> "Water is clear: still visible, no underwater fog.";
			case OFF -> "Water is hidden.";
		};
	}
}
