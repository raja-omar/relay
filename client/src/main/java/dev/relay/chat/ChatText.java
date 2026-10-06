package dev.relay.chat;

import java.util.ArrayList;
import java.util.List;

import dev.relay.ModInfo;
import dev.relay.group.GroupState;
import dev.relay.schematic.SchematicFile;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * Relay's player-facing copy as Lunar Adventure components. Names and paths are always
 * {@link Component#text(String)} so a MiniMessage payload in a group name cannot restyle chat.
 */
public final class ChatText {
	static final TextColor BRAND = TextColor.color(0x4DA3FF);

	private ChatText() {
	}

	public static Component brand() {
		return MiniMessage.miniMessage().deserialize("<gradient:#4DA3FF:#7EE8FF><bold>RELAY</bold></gradient>");
	}

	public static Component info(String text) {
		return prefixed(text, NamedTextColor.GRAY);
	}

	public static Component success(String text) {
		return prefixed(text, NamedTextColor.GREEN);
	}

	public static Component error(String text) {
		return prefixed(text, NamedTextColor.RED);
	}

	public static Component hint(String text) {
		return Component.text(text, NamedTextColor.GRAY);
	}

	public static Component invite(String groupName, String inviterName) {
		return prefixed(inviterName + " invited you to " + groupName + " ", NamedTextColor.WHITE)
				.append(button("[Accept]", NamedTextColor.GREEN,
						acceptCommand(groupName),
						"Join " + groupName))
				.append(Component.space())
				.append(button("[Decline]", NamedTextColor.RED,
						declineCommand(groupName),
						"Turn down the invitation to " + groupName));
	}

	public static Component groupInfo(GroupState group) {
		List<Component> lines = new ArrayList<>();
		lines.add(prefixed(group.name(), NamedTextColor.WHITE)
				.append(Component.text(" -- " + group.onlineCount() + " of " + group.members().size()
						+ " online", NamedTextColor.DARK_GRAY)));
		for (GroupState.Member member : group.members()) {
			lines.add(memberLine(group, member));
		}
		return Component.join(JoinConfiguration.newlines(), lines);
	}

	public static Component schematicList(List<SchematicFile> files) {
		List<Component> lines = new ArrayList<>();
		lines.add(prefixed(
				files.size() + (files.size() == 1 ? " schematic" : " schematics"),
				NamedTextColor.WHITE));
		for (SchematicFile file : files) {
			lines.add(schematicLine(file));
		}
		return Component.join(JoinConfiguration.newlines(), lines);
	}

	public static Component schematicOffered(String senderName, String placementName, String transferId) {
		return prefixed(senderName + " shared " + placementName + ". ", NamedTextColor.WHITE)
				.append(button("[Download]", NamedTextColor.GREEN,
						downloadCommand(transferId),
						"Load " + placementName + " in Litematica"));
	}

	public static Component schematicDownloaded(String placementName) {
		return prefixed("Loaded " + placementName + ".", NamedTextColor.GREEN);
	}

	public static Component alreadyHaveShare() {
		return prefixed("Already have that schematic.", NamedTextColor.GRAY);
	}

	public static Component shareUnavailable() {
		return prefixed("That share is no longer available.", NamedTextColor.GRAY);
	}

	public static Component schematicInfo(SchematicFile file) {
		return prefixed(file.relativePath(), NamedTextColor.WHITE)
				.append(Component.text("  " + file.sizeLabel(), NamedTextColor.DARK_GRAY));
	}

	public static String acceptCommand(String groupName) {
		return "/" + ModInfo.ID + " group accept " + groupName;
	}

	public static String declineCommand(String groupName) {
		return "/" + ModInfo.ID + " group decline " + groupName;
	}

	public static String downloadCommand(String transferId) {
		return "/" + ModInfo.ID + " schem download " + transferId;
	}

	private static Component memberLine(GroupState group, GroupState.Member member) {
		boolean owner = member.id().equals(group.ownerId());
		return Component.text()
				.append(Component.text("  "))
				.append(Component.text(member.online() ? "\u25cf " : "\u25cb ",
						member.online() ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY))
				.append(Component.text(member.name(),
						member.online() ? NamedTextColor.WHITE : NamedTextColor.GRAY))
				.append(Component.text(owner ? " (owner)" : "", NamedTextColor.GOLD))
				.build();
	}

	private static Component schematicLine(SchematicFile file) {
		return Component.text()
				.append(Component.text("  "))
				.append(Component.text(file.relativePath(), NamedTextColor.WHITE))
				.append(Component.text("  (" + file.sizeLabel() + ")", NamedTextColor.DARK_GRAY))
				.build();
	}

	private static Component button(String label, NamedTextColor colour, String command, String tooltip) {
		return Component.text(label, colour)
				.decorate(TextDecoration.BOLD)
				.clickEvent(ClickEvent.runCommand(command))
				.hoverEvent(HoverEvent.showText(Component.text(tooltip, NamedTextColor.YELLOW)));
	}

	private static Component prefixed(String text, TextColor colour) {
		return Component.text()
				.append(Component.text("[" + ModInfo.NAME + "] ", BRAND))
				.append(Component.text(text, colour))
				.build();
	}
}
