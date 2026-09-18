package dev.relay.server;

import java.io.IOException;

/** Starts the server and waits. */
public final class Main {
	private static final String SECRET_ENVIRONMENT_VARIABLE = "RELAY_SECRET";

	public static void main(String[] arguments) throws InterruptedException {
		ServerConfig config;

		try {
			config = ServerConfig.parse(arguments, System.getenv(SECRET_ENVIRONMENT_VARIABLE));
		} catch (IllegalArgumentException badArguments) {
			Log.error(badArguments.getMessage());
			Log.info(ServerConfig.usage());
			System.exit(2);
			return;
		}

		RelayServer server = new RelayServer(config);

		try {
			server.start();
		} catch (IOException couldNotListen) {
			Log.error("Could not listen on port " + config.port(), couldNotListen);
			System.exit(1);
			return;
		}

		Runtime.getRuntime().addShutdownHook(new Thread(server::close, "relay-shutdown"));
		server.awaitShutdown();
	}

	private Main() {
	}
}
