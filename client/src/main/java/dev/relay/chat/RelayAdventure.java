package dev.relay.chat;

import dev.relay.mixin.GuiAccessor;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.platform.modcommon.MinecraftClientAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.MutableComponent;

/**
 * The Lunar Adventure (Kyori) bridge: colour helpers and conversion onto Minecraft types.
 * Layout still lives in owo-ui; every string the player reads goes through Adventure first.
 */
public final class RelayAdventure {
	public static final Key OVERLAY_FONT = Key.key("relay", "overlay");
	public static final Key HEADING_FONT = Key.key("relay", "heading");
	public static final Key TITLE_FONT = Key.key("relay", "title");

	public static MinecraftClientAudiences audiences() {
		return MinecraftClientAudiences.of();
	}

	public static Audience audience() {
		return audiences().audience();
	}

	public static net.minecraft.network.chat.Component nativeComponent(Component adventure) {
		return audiences().asNative(adventure);
	}

	public static TextColor rgb(int argbOrRgb) {
		return TextColor.color(argbOrRgb & 0xFFFFFF);
	}

	public static Component text(String value, int argbOrRgb) {
		return Component.text(value, rgb(argbOrRgb));
	}

	/** Overlay copy uses Inter; chat stays on the vanilla font. */
	public static Component overlay(String value, int argbOrRgb) {
		return overlay(value, argbOrRgb, OVERLAY_FONT);
	}

	public static Component overlayHeading(String value, int argbOrRgb) {
		return overlay(value, argbOrRgb, HEADING_FONT);
	}

	public static Component overlayTitle(String value, int argbOrRgb) {
		return overlay(value, argbOrRgb, TITLE_FONT);
	}

	private static Component overlay(String value, int argbOrRgb, Key font) {
		return Component.text(value, Style.style(rgb(argbOrRgb)).font(font));
	}

	/**
	 * Writes an action-bar hint. No-ops on the title screen.
	 *
	 * <p>Written onto the HUD overlay fields as a vanilla literal. Lunar Client crashes if this
	 * goes through {@code Gui.setOverlayMessage} with adventure-platform {@code asNative} wrappers.
	 */
	public static void actionBar(Component message) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.gui == null) {
			return;
		}

		net.minecraft.network.chat.Component overlay = vanillaOverlay(message);
		if (client.gui instanceof GuiAccessor accessor) {
			accessor.relay$setOverlayMessageString(overlay);
			accessor.relay$setOverlayMessageTime(60);
			accessor.relay$setAnimateOverlayMessageColor(false);
			return;
		}

		client.gui.setOverlayMessage(overlay, false);
	}

	static net.minecraft.network.chat.Component vanillaOverlay(Component adventure) {
		MutableComponent overlay = net.minecraft.network.chat.Component.literal(plain(adventure));
		if (adventure.color() != null) {
			overlay = overlay.withColor(adventure.color().value());
		}
		return overlay;
	}

	static String plain(Component adventure) {
		StringBuilder text = new StringBuilder();
		appendPlain(adventure, text);
		return text.toString();
	}

	private static void appendPlain(Component node, StringBuilder text) {
		if (node instanceof TextComponent literal) {
			text.append(literal.content());
		}
		for (Component child : node.children()) {
			appendPlain(child, text);
		}
	}

	private RelayAdventure() {
	}
}
