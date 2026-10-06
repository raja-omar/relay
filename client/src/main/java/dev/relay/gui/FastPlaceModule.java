package dev.relay.gui;

import java.util.List;

import dev.relay.RelayClient;
import dev.relay.RelayClientMod;
import dev.relay.RelayConfig;
import dev.relay.place.FastPlacePolicy;

import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Sizing;

/**
 * Held right-click places blocks faster than vanilla. Lives next to Litematica, not inside it.
 */
final class FastPlaceModule implements RelayModule {
	@Override
	public String id() {
		return "fastplace";
	}

	@Override
	public String name() {
		return "Fast Place";
	}

	@Override
	public String description() {
		return "Hold right-click to place blocks faster than vanilla.";
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
		return List.of(new HotkeyBinding(id(), name(), "Toggle Fast Place", RelayClientMod::fastPlaceKey));
	}

	@Override
	public void render(FlowLayout content, RelayScreen overlay) {
		RelayConfig config = RelayClient.get().config();
		FlowLayout inner = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
		inner.gap(2);
		inner.child(RelayUi.title(name()));
		inner.child(RelayUi.muted("Hold right-click with a block to skip vanilla's place cooldown."));
		overlay.toggleRow(inner, "Fast Place",
				"Place blocks as fast as the speed setting allows while right-click is held.",
				RelayClientMod.keyHint(RelayClientMod.fastPlaceKey()),
				config.fastPlace(), true, config::setFastPlace);
		overlay.stepperRow(inner, "Speed", FastPlacePolicy.speedHint(),
				FastPlacePolicy.speedLabel(config.fastPlaceDelay()),
				FastPlacePolicy.canGoSlower(config.fastPlaceDelay()),
				FastPlacePolicy.canGoFaster(config.fastPlaceDelay()),
				() -> config.setFastPlaceDelay(FastPlacePolicy.slower(config.fastPlaceDelay())),
				() -> config.setFastPlaceDelay(FastPlacePolicy.faster(config.fastPlaceDelay())));
		content.child(RelayUi.pageScroll(inner));
	}
}
