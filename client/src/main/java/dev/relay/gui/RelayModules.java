package dev.relay.gui;

import java.util.ArrayList;
import java.util.List;

import dev.relay.RelayClientMod;

/**
 * Feature modules, then utility modules (Groups, Hotkeys).
 */
public final class RelayModules {
	private static final RelayModule LITEMATICA = new LitematicaModule();
	private static final RelayModule FAST_PLACE = new FastPlaceModule();
	private static final RelayModule PATCHCRUMBS = new PatchcrumbsModule();
	private static final RelayModule FLUIDS = new FluidsModule();
	private static final RelayModule MISCELLANEOUS = new MiscellaneousModule();
	private static final RelayModule COMBAT = new CombatModule();
	private static final RelayModule GROUPS = new GroupsModule();
	private static final RelayModule HOTKEYS = new HotkeysModule();

	private static final List<RelayModule> ALL = List.of(
			LITEMATICA, FAST_PLACE, PATCHCRUMBS, FLUIDS, MISCELLANEOUS, COMBAT, GROUPS, HOTKEYS);

	private RelayModules() {
	}

	public static List<RelayModule> all() {
		return ALL;
	}

	public static List<RelayModule> navigation() {
		return ALL;
	}

	public static RelayModule byId(String id) {
		for (RelayModule module : ALL) {
			if (module.id().equals(id)) {
				return module;
			}
		}
		return LITEMATICA;
	}

	public static RelayModule litematica() {
		return LITEMATICA;
	}

	public static RelayModule fastPlace() {
		return FAST_PLACE;
	}

	public static RelayModule patchcrumbs() {
		return PATCHCRUMBS;
	}

	public static RelayModule fluids() {
		return FLUIDS;
	}

	public static RelayModule miscellaneous() {
		return MISCELLANEOUS;
	}

	public static RelayModule combat() {
		return COMBAT;
	}

	public static RelayModule groups() {
		return GROUPS;
	}

	public static RelayModule hotkeys() {
		return HOTKEYS;
	}

	public static List<HotkeyBinding> allHotkeys() {
		List<HotkeyBinding> keys = new ArrayList<>();
		keys.add(new HotkeyBinding("relay", "Relay", "Open Relay", RelayClientMod::openGuiKey));
		for (RelayModule module : ALL) {
			keys.addAll(module.hotkeys());
		}
		return List.copyOf(keys);
	}
}
