package dev.relay.chat;

import dev.relay.ModInfo;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Builds the chat lines this mod sends, so every message looks the same and carries the mod name.
 */
public final class ChatMessages {
	public static Component info(String text) {
		return prefixed(text, ChatFormatting.GRAY);
	}

	public static Component success(String text) {
		return prefixed(text, ChatFormatting.GREEN);
	}

	public static Component error(String text) {
		return prefixed(text, ChatFormatting.RED);
	}

	private static Component prefixed(String text, ChatFormatting colour) {
		return Component.literal("[" + ModInfo.NAME + "] ")
				.withStyle(ChatFormatting.AQUA)
				.append(Component.literal(text).withStyle(colour));
	}

	private ChatMessages() {
	}
}
