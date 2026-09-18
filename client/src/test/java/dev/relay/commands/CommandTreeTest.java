package dev.relay.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;

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
			"relay group",
			"relay group info",
			"relay group create Alpha",
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
}
