package dev.relay.server;

import java.io.IOException;
import java.nio.file.Path;

import dev.relay.server.player.PlayerDirectory;

/**
 * Starts the server, or issues and revokes player ids.
 *
 * <p>Listening and inviting are the same program so the allowlist file cannot drift from the
 * process that reads it. {@code invite} prints the id once; it is not stored in the clear.
 */
public final class Main {
	public static void main(String[] arguments) throws InterruptedException {
		if (arguments.length > 0 && isAdminCommand(arguments[0])) {
			try {
				runAdmin(arguments);
			} catch (IllegalArgumentException badArguments) {
				Log.error(badArguments.getMessage());
				Log.info(ServerConfig.usage());
				System.exit(2);
			} catch (IOException failed) {
				Log.error("Could not update the player list", failed);
				System.exit(1);
			}

			return;
		}

		ServerConfig config;

		try {
			config = ServerConfig.parse(arguments);
		} catch (IllegalArgumentException badArguments) {
			Log.error(badArguments.getMessage());
			Log.info(ServerConfig.usage());
			System.exit(2);
			return;
		}

		RelayServer server;

		try {
			server = new RelayServer(config);
			server.start();
		} catch (IOException failed) {
			Log.error("Could not start on port " + config.port(), failed);
			System.exit(1);
			return;
		}

		Runtime.getRuntime().addShutdownHook(new Thread(server::close, "relay-shutdown"));
		server.awaitShutdown();
	}

	private static boolean isAdminCommand(String command) {
		return command.equals("invite") || command.equals("revoke") || command.equals("list");
	}

	private static void runAdmin(String[] arguments) throws IOException {
		String command = arguments[0];
		String playerName = null;
		Path playersFile = ServerConfig.DEFAULT_PLAYERS_FILE;

		for (int i = 1; i < arguments.length; i++) {
			String argument = arguments[i];

			if (argument.equals("--players")) {
				playersFile = Path.of(ServerConfig.valueOf(arguments, ++i, "--players"));
			} else if (!argument.startsWith("-") && playerName == null) {
				playerName = argument;
			} else {
				throw new IllegalArgumentException("Unknown option " + argument);
			}
		}

		PlayerDirectory players = PlayerDirectory.load(playersFile);

		switch (command) {
			case "invite" -> invite(players, requireName(playerName, "invite"));
			case "revoke" -> revoke(players, requireName(playerName, "revoke"));
			case "list" -> list(players);
			default -> throw new IllegalArgumentException("Unknown command " + command);
		}
	}

	private static void invite(PlayerDirectory players, String playerName) throws IOException {
		PlayerDirectory.Issued issued = players.issue(playerName);

		if (issued.replaced()) {
			Log.info("Replaced the id for " + issued.playerName() + ". The old one no longer works.");
		} else {
			Log.info("Issued an id for " + issued.playerName() + ". Give them this once; it is not stored:");
		}

		System.out.println("  /relay login " + issued.token());
	}

	private static void revoke(PlayerDirectory players, String playerName) throws IOException {
		if (players.revoke(playerName)) {
			Log.info("Revoked " + playerName + ". That id can no longer sign in.");
			return;
		}

		Log.error("No player called " + playerName + " has an id.");
		System.exit(1);
	}

	private static void list(PlayerDirectory players) {
		if (players.size() == 0) {
			Log.info("No player ids yet. Issue one with: relay-server invite <name>");
			return;
		}

		Log.info(players.size() + " player id" + (players.size() == 1 ? "" : "s") + ":");

		for (PlayerDirectory.Entry entry : players.list()) {
			System.out.println("  " + entry.playerName());
		}
	}

	private static String requireName(String playerName, String command) {
		if (playerName == null || playerName.isBlank()) {
			throw new IllegalArgumentException(command + " needs a player name");
		}

		return playerName;
	}

	private Main() {
	}
}
