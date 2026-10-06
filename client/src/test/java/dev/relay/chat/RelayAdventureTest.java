package dev.relay.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.junit.jupiter.api.Test;

class RelayAdventureTest {
	@Test
	void overlayTitleUsesTheTitleFontWithoutFakeBold() {
		Component title = RelayAdventure.overlayTitle("Hotkeys", 0xFFFFFF);
		assertEquals(RelayAdventure.TITLE_FONT, title.font());
		assertNotEquals(TextDecoration.State.TRUE, title.decoration(TextDecoration.BOLD));
	}

	@Test
	void overlayHeadingUsesTheHeadingFontWithoutFakeBold() {
		Component heading = RelayAdventure.overlayHeading("Relay", 0xFFFFFF);
		assertEquals(RelayAdventure.HEADING_FONT, heading.font());
		assertNotEquals(TextDecoration.State.TRUE, heading.decoration(TextDecoration.BOLD));
	}

	@Test
	void overlayBodyStaysOnTheCompactFont() {
		Component body = RelayAdventure.overlay("Open Relay", 0xFFFFFF);
		assertEquals(RelayAdventure.OVERLAY_FONT, body.font());
		assertNotEquals(TextDecoration.State.TRUE, body.decoration(TextDecoration.BOLD));
	}

	@Test
	void actionBarHintsArePlainGrayText() {
		Component hint = ChatText.hint("No schematic block belongs here");
		assertEquals("No schematic block belongs here", RelayAdventure.plain(hint));
		assertEquals(NamedTextColor.GRAY, hint.color());
	}
}
