package dev.relay.server;

import java.nio.file.Path;

import dev.relay.common.Tls;

/**
 * How the server was started.
 *
 * @param port        TCP port to listen on. 0 asks the OS for any free port, which the tests use.
 * @param playersFile allowlist of issued player ids; created the first time someone is invited
 * @param tlsFile     PKCS12 of the socket certificate; {@code null} makes an ephemeral one for tests
 */
public record ServerConfig(int port, Path playersFile, Path tlsFile) {
	public static final int DEFAULT_PORT = 25599;
	public static final Path DEFAULT_PLAYERS_FILE = Path.of("relay-players");
	public static final Path DEFAULT_TLS_FILE = Path.of(Tls.DEFAULT_FILE_NAME);

	public ServerConfig(int port) {
		this(port, DEFAULT_PLAYERS_FILE, null);
	}

	public ServerConfig(int port, Path playersFile) {
		this(port, playersFile, null);
	}

	public ServerConfig {
		if (port < 0 || port > 65535) {
			throw new IllegalArgumentException("Port " + port + " is not a port");
		}

		if (playersFile == null) {
			playersFile = DEFAULT_PLAYERS_FILE;
		}
	}

	/**
	 * Reads {@code --port <n>}, {@code --players <file>} and {@code --tls <file>}. Invite and
	 * revoke are separate commands; see {@link #usage()}.
	 */
	public static ServerConfig parse(String[] arguments) {
		int port = DEFAULT_PORT;
		Path playersFile = DEFAULT_PLAYERS_FILE;
		Path tlsFile = DEFAULT_TLS_FILE;

		for (int i = 0; i < arguments.length; i++) {
			String argument = arguments[i];

			switch (argument) {
				case "--port" -> port = parsePort(valueOf(arguments, ++i, "--port"));
				case "--players" -> playersFile = Path.of(valueOf(arguments, ++i, "--players"));
				case "--tls" -> tlsFile = Path.of(valueOf(arguments, ++i, "--tls"));
				default -> throw new IllegalArgumentException("Unknown option " + argument);
			}
		}

		return new ServerConfig(port, playersFile, tlsFile);
	}

	static String valueOf(String[] arguments, int index, String option) {
		if (index >= arguments.length) {
			throw new IllegalArgumentException(option + " needs a value");
		}

		return arguments[index];
	}

	private static int parsePort(String value) {
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException notANumber) {
			throw new IllegalArgumentException("Port " + value + " is not a number");
		}
	}

	public static String usage() {
		return """
				Usage:
				  relay-server [--port <%d>] [--players <%s>] [--tls <%s>]
				  relay-server invite <name> [--players <%s>]
				  relay-server revoke <name> [--players <%s>]
				  relay-server list [--players <%s>]""".formatted(
				DEFAULT_PORT, DEFAULT_PLAYERS_FILE, DEFAULT_TLS_FILE, DEFAULT_PLAYERS_FILE,
				DEFAULT_PLAYERS_FILE, DEFAULT_PLAYERS_FILE);
	}
}
