package dev.relay.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ServerConfigTest {
	@Test
	void defaultsToTheStandardPortAndPlayersFile() {
		ServerConfig config = ServerConfig.parse(new String[0]);

		assertEquals(ServerConfig.DEFAULT_PORT, config.port());
		assertEquals(ServerConfig.DEFAULT_PLAYERS_FILE, config.playersFile());
		assertEquals(ServerConfig.DEFAULT_TLS_FILE, config.tlsFile());
	}

	@Test
	void readsPortAndPlayersFileFromArguments() {
		ServerConfig config = ServerConfig.parse(new String[] {
				"--port", "9000", "--players", "/var/relay/players", "--tls", "/var/relay/tls.p12"});

		assertEquals(9000, config.port());
		assertEquals(Path.of("/var/relay/players"), config.playersFile());
		assertEquals(Path.of("/var/relay/tls.p12"), config.tlsFile());
	}

	@Test
	void complainsAboutBadArguments() {
		assertThrows(IllegalArgumentException.class, () -> ServerConfig.parse(new String[] {"--nope"}));
		assertThrows(IllegalArgumentException.class, () -> ServerConfig.parse(new String[] {"--port"}));
		assertThrows(IllegalArgumentException.class, () -> ServerConfig.parse(new String[] {"--port", "http"}));
		assertThrows(IllegalArgumentException.class, () -> ServerConfig.parse(new String[] {"--port", "70000"}));
		assertThrows(IllegalArgumentException.class, () -> ServerConfig.parse(new String[] {"--port", "-1"}));
		assertThrows(IllegalArgumentException.class, () -> ServerConfig.parse(new String[] {"--tls"}));
		assertThrows(IllegalArgumentException.class, () -> ServerConfig.parse(new String[] {"--secret", "hunter2"}));
	}

	@Test
	void allowsPortZeroSoTestsCanAskForAnyFreePort() {
		assertEquals(0, ServerConfig.parse(new String[] {"--port", "0"}).port());
	}
}
