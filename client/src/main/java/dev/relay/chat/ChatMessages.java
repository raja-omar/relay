package dev.relay.chat;

import java.util.List;

import dev.relay.ModInfo;
import dev.relay.group.GroupState;
import dev.relay.schematic.SchematicFile;

import net.kyori.adventure.text.Component;

import net.minecraft.client.Minecraft;

/**
 * Player-facing chat. Built with Lunar Adventure, then converted for Fabric's native chat HUD
 * and {@code sendFeedback}.
 */
public final class ChatMessages {
	public static net.minecraft.network.chat.Component info(String text) {
		return nativeComponent(ChatText.info(text));
	}

	public static net.minecraft.network.chat.Component success(String text) {
		return nativeComponent(ChatText.success(text));
	}

	public static net.minecraft.network.chat.Component error(String text) {
		return nativeComponent(ChatText.error(text));
	}

	public static net.minecraft.network.chat.Component invite(String groupName, String inviterName) {
		return nativeComponent(ChatText.invite(groupName, inviterName));
	}

	public static net.minecraft.network.chat.Component groupInfo(GroupState group) {
		return nativeComponent(ChatText.groupInfo(group));
	}

	public static net.minecraft.network.chat.Component schematicList(List<SchematicFile> files) {
		return nativeComponent(ChatText.schematicList(files));
	}

	public static net.minecraft.network.chat.Component schematicOffered(String senderName, String placementName,
			String transferId) {
		return nativeComponent(ChatText.schematicOffered(senderName, placementName, transferId));
	}

	public static net.minecraft.network.chat.Component schematicDownloaded(String placementName) {
		return nativeComponent(ChatText.schematicDownloaded(placementName));
	}

	public static net.minecraft.network.chat.Component alreadyHaveShare() {
		return nativeComponent(ChatText.alreadyHaveShare());
	}

	public static net.minecraft.network.chat.Component shareUnavailable() {
		return nativeComponent(ChatText.shareUnavailable());
	}

	public static net.minecraft.network.chat.Component schematicInfo(SchematicFile file) {
		return nativeComponent(ChatText.schematicInfo(file));
	}

	public static void show(net.minecraft.network.chat.Component message) {
		Minecraft client = Minecraft.getInstance();

		if (client.player != null) {
			RelayAdventure.audience().sendMessage(RelayAdventure.audiences().asAdventure(message));
			return;
		}

		if (client.gui != null) {
			client.gui.getChat().addMessage(message);
		} else {
			ModInfo.LOG.info(message.getString());
		}
	}

	public static void hint(String text) {
		RelayAdventure.actionBar(ChatText.hint(text));
	}

	private static net.minecraft.network.chat.Component nativeComponent(Component adventure) {
		return RelayAdventure.nativeComponent(adventure);
	}

	private ChatMessages() {
	}
}
