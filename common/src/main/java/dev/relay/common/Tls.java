package dev.relay.common;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Locale;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;

/**
 * TLS for the schematic socket: a self-signed server certificate, and a client that pins its
 * fingerprint so a stranger cannot sit in the middle and copy player ids.
 *
 * <p>The pin is public (it goes in the client jar). The private key stays on the server, next to
 * the allowlist. Restarting keeps the same pin when the keystore file is reused.
 */
public final class Tls {
	public static final String DEFAULT_FILE_NAME = "relay-tls.p12";
	public static final int PIN_LENGTH = 64;

	private static final String ALIAS = "relay";
	private static final char[] PASSWORD = "relay-tls".toCharArray();
	private static final String[] PROTOCOLS = {"TLSv1.3", "TLSv1.2"};

	private final SSLContext context;
	private final String pin;

	private Tls(SSLContext context, String pin) {
		this.context = context;
		this.pin = pin;
	}

	/**
	 * Loads {@code file}, or creates it. {@code file} null means an ephemeral store that tests use
	 * and that must never be the production server.
	 */
	public static Tls loadOrCreate(Path file) throws IOException {
		if (file != null && Files.isRegularFile(file)) {
			return load(file);
		}

		Path source = file;

		if (source == null) {
			source = Files.createTempFile("relay-tls-", ".p12");
			Files.deleteIfExists(source);
		}

		try {
			if (file != null) {
				Path parent = file.toAbsolutePath().getParent();

				if (parent != null) {
					Files.createDirectories(parent);
				}
			}

			createKeyStore(source);
			return load(source);
		} finally {
			if (file == null) {
				Files.deleteIfExists(source);
			}
		}
	}

	public String pin() {
		return pin;
	}

	public SSLServerSocket bind(int port) throws IOException {
		SSLServerSocketFactory factory = context.getServerSocketFactory();
		SSLServerSocket socket = (SSLServerSocket) factory.createServerSocket(port);
		socket.setEnabledProtocols(PROTOCOLS);
		socket.setNeedClientAuth(false);
		return socket;
	}

	public SSLSocket connect(String host, int port, int timeoutMillis) throws IOException {
		SSLSocketFactory factory = context.getSocketFactory();
		SSLSocket socket = (SSLSocket) factory.createSocket();
		socket.setEnabledProtocols(PROTOCOLS);
		socket.setSoTimeout(Math.max(1, timeoutMillis));

		try {
			socket.connect(new InetSocketAddress(host, port), timeoutMillis);
			socket.startHandshake();
			verifyPin(socket);
			socket.setSoTimeout(0);
			return socket;
		} catch (IOException failed) {
			abort(socket);
			throw failed;
		}
	}

	/**
	 * The handshake is allowed to finish so JSSE cannot stall on a fatal alert. The pin is the
	 * actual check; AUTH is not sent until this returns.
	 */
	private void verifyPin(SSLSocket socket) throws IOException {
		try {
			Certificate[] chain = socket.getSession().getPeerCertificates();

			if (chain.length == 0 || !(chain[0] instanceof X509Certificate x509)) {
				throw new IOException("TLS pin mismatch");
			}

			if (!pin.equals(fingerprint(x509))) {
				throw new IOException("TLS pin mismatch");
			}
		} catch (GeneralSecurityException failed) {
			throw new IOException("TLS pin mismatch", failed);
		}
	}

	private static void abort(Socket socket) {
		try {
			socket.setSoLinger(true, 0);
		} catch (IOException ignored) {
			// Best-effort; close still follows.
		}

		closeQuietly(socket);
	}

	private static void closeQuietly(Socket socket) {
		try {
			socket.close();
		} catch (IOException ignored) {
			// The caller already has a better error to report.
		}
	}

	public static Tls pinning(String pin) throws IOException {
		String expected = normalizePin(pin);

		if (!isWellFormedPin(expected)) {
			throw new IOException("TLS pin is missing or not a SHA-256 fingerprint");
		}

		try {
			SSLContext context = SSLContext.getInstance("TLS");
			context.init(null, new TrustManager[] {new PinTrust()}, new SecureRandom());
			return new Tls(context, expected);
		} catch (GeneralSecurityException failed) {
			throw new IOException("Could not set up TLS", failed);
		}
	}

	public static String normalizePin(String raw) {
		if (raw == null) {
			return "";
		}

		String compact = raw.trim().toLowerCase(Locale.ROOT).replace(":", "").replace(" ", "");

		if (compact.startsWith("sha256")) {
			compact = compact.substring("sha256".length());

			if (compact.startsWith("-") || compact.startsWith("=")) {
				compact = compact.substring(1);
			}
		}

		return compact;
	}

	public static boolean isWellFormedPin(String raw) {
		String pin = normalizePin(raw);

		if (pin.length() != PIN_LENGTH) {
			return false;
		}

		for (int i = 0; i < pin.length(); i++) {
			char character = pin.charAt(i);

			if (Character.digit(character, 16) < 0) {
				return false;
			}
		}

		return true;
	}

	/** True when the handshake failed because the certificate was not the packed pin. */
	public static boolean isPinMismatch(Throwable error) {
		for (Throwable current = error; current != null; current = current.getCause()) {
			String message = current.getMessage();

			if (message != null && message.contains("TLS pin mismatch")) {
				return true;
			}
		}

		return false;
	}

	private static Tls load(Path file) throws IOException {
		try (InputStream in = Files.newInputStream(file)) {
			KeyStore store = KeyStore.getInstance("PKCS12");
			store.load(in, PASSWORD);
			Certificate certificate = store.getCertificate(ALIAS);

			if (!(certificate instanceof X509Certificate x509)) {
				throw new IOException(file + " has no relay certificate");
			}

			KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			keys.init(store, PASSWORD);
			SSLContext context = SSLContext.getInstance("TLS");
			context.init(keys.getKeyManagers(), null, new SecureRandom());
			return new Tls(context, fingerprint(x509));
		} catch (GeneralSecurityException failed) {
			throw new IOException("Could not read " + file, failed);
		}
	}

	private static void createKeyStore(Path file) throws IOException {
		String keytool = Path.of(System.getProperty("java.home"), "bin", keytoolName()).toString();
		Process process = new ProcessBuilder(
				keytool,
				"-genkeypair",
				"-alias", ALIAS,
				"-keyalg", "EC",
				"-groupname", "secp256r1",
				"-sigalg", "SHA256withECDSA",
				"-validity", "3650",
				"-dname", "CN=relay",
				"-storetype", "PKCS12",
				"-keystore", file.toAbsolutePath().toString(),
				"-storepass", new String(PASSWORD),
				"-keypass", new String(PASSWORD),
				"-noprompt")
				.redirectErrorStream(true)
				.start();

		String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		int code;

		try {
			code = process.waitFor();
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted while creating TLS keys", interrupted);
		}

		if (code != 0) {
			throw new IOException("Could not create TLS keys (" + code + "): " + output.trim());
		}
	}

	private static String keytoolName() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
				? "keytool.exe"
				: "keytool";
	}

	static String fingerprint(X509Certificate certificate) throws GeneralSecurityException {
		byte[] digest = MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
		StringBuilder hex = new StringBuilder(digest.length * 2);

		for (byte value : digest) {
			hex.append(Character.forDigit((value >>> 4) & 0xF, 16));
			hex.append(Character.forDigit(value & 0xF, 16));
		}

		return hex.toString();
	}

	/**
	 * Accepts the self-signed socket cert during the handshake. {@link Tls#verifyPin} is the check
	 * that the fingerprint matches; rejecting inside JSSE can leave startHandshake blocked.
	 */
	private static final class PinTrust extends X509ExtendedTrustManager {
		@Override
		public void checkClientTrusted(X509Certificate[] chain, String authType) {
			throw new UnsupportedOperationException("Relay does not use client certificates");
		}

		@Override
		public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) {
			checkClientTrusted(chain, authType);
		}

		@Override
		public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
			checkClientTrusted(chain, authType);
		}

		@Override
		public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
			if (chain == null || chain.length == 0) {
				throw new CertificateException("Server sent no certificate");
			}
		}

		@Override
		public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket)
				throws CertificateException {
			checkServerTrusted(chain, authType);
		}

		@Override
		public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
				throws CertificateException {
			checkServerTrusted(chain, authType);
		}

		@Override
		public X509Certificate[] getAcceptedIssuers() {
			return new X509Certificate[0];
		}
	}

}
