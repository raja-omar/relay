package dev.relay.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ServerConfigTest {
	@Test
	void defaultsToTheStandardPortAndNoSecret() {
		ServerConfig config = ServerConfig.parse(new String[0], null);

		assertEquals(ServerConfig.DEFAULT_PORT, config.port());
		assertFalse(config.requiresSecret());
	}

	@Test
	void readsPortAndSecretFromArguments() {
		ServerConfig config = ServerConfig.parse(new String[] {"--port", "9000", "--secret", "hunter2"}, null);

		assertEquals(9000, config.port());
		assertEquals("hunter2", config.secret());
		assertTrue(config.requiresSecret());
	}

	@Test
	void takesTheSecretFromTheEnvironment() {
		ServerConfig config = ServerConfig.parse(new String[0], "  from-env  ");

		assertEquals("from-env", config.secret());
		assertTrue(config.requiresSecret());
	}

	@Test
	void anArgumentBeatsTheEnvironment() {
		ServerConfig config = ServerConfig.parse(new String[] {"--secret", "explicit"}, "from-env");

		assertEquals("explicit", config.secret());
	}

	@Test
	void anEmptySecretMeansAnyoneMayConnect() {
		assertFalse(ServerConfig.parse(new String[0], "").requiresSecret());
		assertFalse(ServerConfig.parse(new String[] {"--secret", "   "}, null).requiresSecret());
	}

	@Test
	void complainsAboutBadArguments() {
		assertThrows(IllegalArgumentException.class, () -> ServerConfig.parse(new String[] {"--nope"}, null));
		assertThrows(IllegalArgumentException.class, () -> ServerConfig.parse(new String[] {"--port"}, null));
		assertThrows(IllegalArgumentException.class, () -> ServerConfig.parse(new String[] {"--port", "http"}, null));
		assertThrows(IllegalArgumentException.class, () -> ServerConfig.parse(new String[] {"--port", "70000"}, null));
		assertThrows(IllegalArgumentException.class, () -> ServerConfig.parse(new String[] {"--port", "-1"}, null));
	}

	@Test
	void allowsPortZeroSoTestsCanAskForAnyFreePort() {
		assertEquals(0, ServerConfig.parse(new String[] {"--port", "0"}, null).port());
	}
}
