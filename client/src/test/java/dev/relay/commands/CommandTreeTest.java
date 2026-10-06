package dev.relay.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;

import dev.relay.chat.ChatText;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Checks the shape of the command tree without starting Minecraft: registering has to not throw,
 * and every command we advertise has to parse all the way to the end of the input.
 *
 * <p>Parsing only. Running a command builds chat components, which needs a real game.
 */
class CommandTreeTest {
	private CommandDispatcher<FabricClientCommandSource> dispatcher;

	@BeforeEach
	void registerCommands() {
		dispatcher = new CommandDispatcher<>();
		RelayCommand.register(dispatcher);
	}

	@Test
	void registersOneRootCommand() {
		assertNotNull(dispatcher.getRoot().getChild("relay"));
	}

	@Test
	void doesNotShadowTheServersOwnGroupCommand() {
		assertNull(dispatcher.getRoot().getChild("group"), "/group must stay with the multiplayer server");
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"relay",
			"relay status",
			"relay connect",
			"relay connect schem.example.com",
			"relay connect schem.example.com 25599",
			"relay connect schem.example.com 25599 0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
			"relay disconnect",
			"relay login rly_7K2M-X9QP-4NWD-T8JH",
			"relay gui",
			"relay group",
			"relay group info",
			"relay group create Alpha",
			"relay group invite Alice",
			"relay group accept Alpha",
			"relay group decline Alpha",
			"relay group leave",
			"relay schem",
			"relay schem list",
			"relay schem info Wall",
			"relay schem info Fortress_Wall_03.litematic",
			"relay schem info houses/Starter",
			"relay schem download 11111111-1111-1111-1111-111111111111",
			"relay cantmiss",
			"relay cantmiss on",
			"relay cantmiss off",
			"relay shop",
			"relay shop auto",
			"relay shop auto on",
			"relay shop auto off",
			"relay shop pre",
			"relay shop pre on",
			"relay shop pre off",
			"relay refill",
			"relay refill on",
			"relay refill off",
			"relay patchcrumbs",
			"relay patchcrumbs on",
			"relay patchcrumbs off",
			"relay patchcrumbs clear",
			"relay patchcrumbs callout",
			"relay cannon pos1",
			"relay cannon pos2",
			"relay cannon clear",
			"relay fluids",
			"relay fluids lava",
			"relay fluids lava normal",
			"relay fluids lava translucent",
			"relay fluids lava transparent",
			"relay fluids lava off",
			"relay fluids lava hidden",
			"relay fluids lava vanilla",
			"relay fluids lava see-through",
			"relay fluids water",
			"relay fluids water vanilla",
			"relay fluids water normal",
			"relay fluids water clear",
			"relay fluids water hidden",
			"relay fluids water off",
	})
	void commandParsesCompletely(String input) {
		ParseResults<FabricClientCommandSource> parse = dispatcher.parse(input, null);

		assertTrue(parse.getExceptions().isEmpty(), () -> input + " reported " + parse.getExceptions());
		assertEquals("", parse.getReader().getRemaining(), () -> input + " was not fully consumed");
		assertNotNull(parse.getContext().build(input).getCommand(), () -> input + " has nothing to run");
	}

	@Test
	void unknownSubcommandDoesNotParse() {
		ParseResults<FabricClientCommandSource> parse = dispatcher.parse("relay group destroy Alpha", null);

		assertTrue(parse.getReader().canRead(), "unknown subcommand should leave input unconsumed");
	}

	@Test
	void doesNotShadowTheServersOwnSchemCommand() {
		assertNull(dispatcher.getRoot().getChild("schem"), "/schem must stay with the multiplayer server");
	}

	@Test
	void shareIsNotACommandYet() {
		ParseResults<FabricClientCommandSource> parse = dispatcher.parse("relay schem share Wall", null);

		assertTrue(parse.getReader().canRead(), "sharing belongs to a later phase");
	}

	/**
	 * The buttons on an invitation or a share run these, so if the wording here and in the tree
	 * ever drift apart, clicking would quietly do nothing.
	 */
	@ParameterizedTest
	@ValueSource(strings = {
			"relay group accept Alpha",
			"relay group decline Alpha",
			"relay schem download 11111111-1111-1111-1111-111111111111",
	})
	void inviteButtonCommandsExistExactlyAsChatSendsThem(String input) {
		assertTrue(dispatcher.parse(input, null).getExceptions().isEmpty());
	}

	@Test
	void chatButtonsRunTheSameCommandsTheTreeAccepts() {
		assertEquals("relay group accept Alpha", ChatText.acceptCommand("Alpha").substring(1));
		assertEquals("relay group decline Alpha", ChatText.declineCommand("Alpha").substring(1));
		assertEquals("relay schem download 11111111-1111-1111-1111-111111111111",
				ChatText.downloadCommand("11111111-1111-1111-1111-111111111111").substring(1));
	}

	@Test
	void refusesAPortOutsideTheUsableRange() {
		ParseResults<FabricClientCommandSource> parse = dispatcher.parse("relay connect host 70000", null);

		assertFalse(parse.getReader().getRemaining().isBlank(), "70000 should not be taken as a port");
	}
}
