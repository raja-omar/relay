package dev.relay.gui;

import java.util.function.Supplier;

import net.minecraft.client.KeyMapping;

/**
 * One shortcut owned by a Relay module. The mapping stays on the Java side; the overlay
 * only displays and rebinds it.
 */
public record HotkeyBinding(String moduleId, String moduleName, String action, Supplier<KeyMapping> mapping) {
	public KeyMapping key() {
		return mapping.get();
	}
}
