package dev.relay.server;

/**
 * How the server was started.
 *
 * @param port   TCP port to listen on. 0 asks the OS for any free port, which the tests use.
 * @param secret shared secret every client must present, or empty for "anyone may connect"
 */
public record ServerConfig(int port, String secret) {
	public static final int DEFAULT_PORT = 25599;

	public ServerConfig {
		if (port < 0 || port > 65535) {
			throw new IllegalArgumentException("Port " + port + " is not a port");
		}

		if (secret == null) {
			secret = "";
		}
	}

	public boolean requiresSecret() {
		return !secret.isEmpty();
	}

	/**
	 * Reads {@code --port <n>} and {@code --secret <s>}. The secret may also come from the
	 * {@code RELAY_SECRET} environment variable, which keeps it out of the process list.
	 */
	public static ServerConfig parse(String[] arguments, String secretFromEnvironment) {
		int port = DEFAULT_PORT;
		String secret = secretFromEnvironment == null ? "" : secretFromEnvironment.trim();

		for (int i = 0; i < arguments.length; i++) {
			String argument = arguments[i];

			switch (argument) {
				case "--port" -> port = parsePort(valueOf(arguments, ++i, "--port"));
				case "--secret" -> secret = valueOf(arguments, ++i, "--secret").trim();
				default -> throw new IllegalArgumentException("Unknown option " + argument);
			}
		}

		return new ServerConfig(port, secret);
	}

	private static String valueOf(String[] arguments, int index, String option) {
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
		return "Usage: relay-server [--port <" + DEFAULT_PORT + ">] [--secret <shared secret>]";
	}
}
