package dev.relay.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import dev.relay.group.GroupState;
import dev.relay.schematic.SchematicFile;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;

import org.junit.jupiter.api.Test;

class ChatTextTest {
	private static final UUID ALICE = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID BETA = UUID.fromString("22222222-2222-2222-2222-222222222222");

	@Test
	void brandIsRelay() {
		assertEquals("RELAY", plain(ChatText.brand()));
	}

	@Test
	void userTextIsNeverParsedAsMiniMessage() {
		assertTrue(plain(ChatText.info("<red>hacked")).contains("<red>hacked"));
	}

	@Test
	void inviteKeepsNamesLiteralAndWiresBothButtons() {
		var invite = ChatText.invite("Alpha<script>", "Alice");
		String content = plain(invite);

		assertTrue(content.contains("[Relay]"));
		assertTrue(content.contains("Alice invited you to Alpha<script>"));
		assertTrue(content.contains("[Accept]"));
		assertTrue(content.contains("[Decline]"));
		assertEquals("/relay group accept Alpha<script>", ChatText.acceptCommand("Alpha<script>"));
		assertEquals("/relay group decline Alpha<script>", ChatText.declineCommand("Alpha<script>"));
		assertTrue(hasCommand(invite, ChatText.acceptCommand("Alpha<script>")));
		assertTrue(hasCommand(invite, ChatText.declineCommand("Alpha<script>")));
	}

	@Test
	void groupInfoListsOnlineOwnerAndOfflineMember() {
		GroupState group = new GroupState("Alpha", ALICE, List.of(
				new GroupState.Member(ALICE, "Alice", true),
				new GroupState.Member(BETA, "Beta", false)));

		String content = plain(ChatText.groupInfo(group));
		assertTrue(content.contains("Alpha"));
		assertTrue(content.contains("1 of 2 online"));
		assertTrue(content.contains("Alice"));
		assertTrue(content.contains("(owner)"));
		assertTrue(content.contains("Beta"));
	}

	@Test
	void shareOfferDownloadsTheTransferId() {
		var offer = ChatText.schematicOffered("Alice", "Wall", "11111111-1111-1111-1111-111111111111");
		assertTrue(plain(offer).contains("[Download]"));
		assertTrue(hasCommand(offer, ChatText.downloadCommand("11111111-1111-1111-1111-111111111111")));
	}

	@Test
	void schematicListUsesTheRelativePath() {
		SchematicFile file = new SchematicFile(Path.of("schematics"), Path.of("schematics", "Wall.litematic"), 2048);
		String content = plain(ChatText.schematicList(List.of(file)));
		assertTrue(content.contains("1 schematic"));
		assertTrue(content.contains("Wall.litematic"));
		assertTrue(content.contains("2.0 KB"));
	}

	@Test
	void errorAndSuccessStayPrefixed() {
		assertTrue(plain(ChatText.error("no")).startsWith("[Relay]"));
		assertTrue(plain(ChatText.success("yes")).startsWith("[Relay]"));
		assertFalse(plain(ChatText.hint("look")).contains("[Relay]"));
	}

	private static String plain(Component root) {
		StringBuilder text = new StringBuilder();
		walk(root, text);
		return text.toString();
	}

	private static void walk(Component node, StringBuilder text) {
		if (node instanceof TextComponent literal) {
			text.append(literal.content());
		}
		for (Component child : node.children()) {
			walk(child, text);
		}
	}

	private static boolean hasCommand(Component root, String command) {
		return root.clickEvent() != null && root.clickEvent().equals(ClickEvent.runCommand(command))
				|| root.children().stream().anyMatch(child -> hasCommand(child, command));
	}
}
