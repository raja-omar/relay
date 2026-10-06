package dev.relay.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.relay.RelayClientMod;

import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Sizing;

import net.minecraft.client.KeyMapping;

/**
 * Global hotkeys, grouped by the module that owns them.
 */
final class HotkeysModule implements RelayModule {
	@Override
	public String id() {
		return "hotkeys";
	}

	@Override
	public String name() {
		return "Hotkeys";
	}

	@Override
	public String description() {
		return "Shortcuts for every Relay module.";
	}

	@Override
	public boolean available() {
		return true;
	}

	@Override
	public boolean utility() {
		return true;
	}

	@Override
	public int badge() {
		return 0;
	}

	@Override
	public List<HotkeyBinding> hotkeys() {
		return List.of();
	}

	@Override
	public void render(FlowLayout content, RelayScreen overlay) {
		FlowLayout inner = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
		inner.gap(14);
		inner.child(RelayUi.title(name()));

		Map<String, List<HotkeyBinding>> groups = new LinkedHashMap<>();
		for (HotkeyBinding binding : RelayModules.allHotkeys()) {
			groups.computeIfAbsent(binding.moduleName(), key -> new ArrayList<>()).add(binding);
		}

		for (Map.Entry<String, List<HotkeyBinding>> group : groups.entrySet()) {
			if (group.getValue().isEmpty()) {
				continue;
			}
			FlowLayout block = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
			block.gap(2);
			block.child(RelayUi.heading(group.getKey()));
			for (HotkeyBinding binding : group.getValue()) {
				KeyMapping mapping = binding.key();
				boolean active = overlay.listening() == mapping;
				String bound = mapping == null ? "" : RelayClientMod.keyHint(mapping);
				block.child(RelayUi.hotkeyRow(binding.action(), bound, active,
						() -> overlay.listen(mapping)));
			}
			inner.child(block);
		}
		content.child(RelayUi.pageScroll(inner));
	}
}
