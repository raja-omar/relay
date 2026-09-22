package dev.relay.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TlsTest {
	@TempDir
	Path folder;

	@Test
	void reusesTheSamePinWhenTheKeystoreIsKept() throws Exception {
		Path file = folder.resolve("relay-tls.p12");
		Tls first = Tls.loadOrCreate(file);
		Tls second = Tls.loadOrCreate(file);

		assertTrue(Tls.isWellFormedPin(first.pin()));
		assertEquals(first.pin(), second.pin());
	}

	@Test
	void aFreshStoreGetsADifferentPin() throws Exception {
		Tls first = Tls.loadOrCreate(folder.resolve("one.p12"));
		Tls second = Tls.loadOrCreate(folder.resolve("two.p12"));

		assertNotEquals(first.pin(), second.pin());
	}

	@Test
	void normalizesColonsAndAShaPrefix() {
		String pin = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

		assertEquals(pin, Tls.normalizePin("SHA256:" + pin.toUpperCase()));
		assertTrue(Tls.isWellFormedPin("01:23:45:67:89:ab:cd:ef:01:23:45:67:89:ab:cd:ef:"
				+ "01:23:45:67:89:ab:cd:ef:01:23:45:67:89:ab:cd:ef"));
	}

	@Test
	void handshakeFailsWhenThePinIsWrong() throws Exception {
		Tls server = Tls.loadOrCreate(folder.resolve("server.p12"));
		Tls other = Tls.loadOrCreate(folder.resolve("other.p12"));

		try (var listener = server.bind(0)) {
			Thread acceptor = Thread.ofPlatform().name("tls-accept").start(() -> {
				try (var incoming = listener.accept()) {
					incoming.getInputStream().read();
				} catch (IOException ignored) {
					// Client aborts the handshake.
				}
			});

			try {
				IOException failed = assertThrows(IOException.class,
						() -> Tls.pinning(other.pin()).connect("127.0.0.1", listener.getLocalPort(), 3000));
				assertTrue(Tls.isPinMismatch(failed), failed.toString());
			} finally {
				listener.close();
				acceptor.join(3000);
			}
		}
	}
}
