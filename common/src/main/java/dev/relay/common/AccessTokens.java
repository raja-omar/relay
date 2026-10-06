package dev.relay.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * Player ids the server issues and the client stores.
 *
 * <p>The id is the whole credential: whoever has it is that player. It is random, long enough that
 * guessing is not a plan, and typed in groups so a human can read it off a screen. The server
 * stores only a hash, so a leaked allowlist file does not give anyone a working id.
 */
public final class AccessTokens {
	public static final String PREFIX = "rly_";

	private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
	private static final int BODY_LENGTH = 16;
	private static final int BODY_BYTES = 10;
	private static final SecureRandom RANDOM = new SecureRandom();

	/** A fresh id, already in the form a player pastes into {@code /relay login}. */
	public static String issue() {
		byte[] bytes = new byte[BODY_BYTES];
		RANDOM.nextBytes(bytes);

		char[] body = new char[BODY_LENGTH];
		int buffer = 0;
		int bits = 0;
		int written = 0;

		for (byte value : bytes) {
			buffer = (buffer << 8) | (value & 0xFF);
			bits += 8;

			while (bits >= 5) {
				bits -= 5;
				body[written++] = ALPHABET.charAt((buffer >>> bits) & 31);
			}
		}

		return display(new String(body));
	}

	/**
	 * The compact 16-character body, or empty when {@code raw} is null.
	 *
	 * <p>Dashes, spaces and the {@code rly_} prefix are ignored. {@code O}/{@code I}/{@code L}
	 * become the digits they are usually meant to be, so a typed id still works.
	 */
	public static String normalize(String raw) {
		if (raw == null) {
			return "";
		}

		String compact = raw.trim().toUpperCase(Locale.ROOT).replace("-", "").replace(" ", "");

		if (compact.startsWith("RLY_")) {
			compact = compact.substring(PREFIX.length());
		}

		StringBuilder body = new StringBuilder(compact.length());

		for (int i = 0; i < compact.length(); i++) {
			body.append(canonical(compact.charAt(i)));
		}

		return body.toString();
	}

	public static boolean isWellFormed(String raw) {
		return isBodyWellFormed(normalize(raw));
	}

	/** The grouped form stored in the player's config and shown to the operator once. */
	public static String display(String raw) {
		String body = normalize(raw);

		if (!isBodyWellFormed(body)) {
			throw new IllegalArgumentException("Not a player id");
		}

		return PREFIX + body.substring(0, 4) + "-" + body.substring(4, 8) + "-"
				+ body.substring(8, 12) + "-" + body.substring(12, 16);
	}

	/** SHA-256 of the normalised body, hex. This is what the server writes down. */
	public static String fingerprint(String raw) {
		String body = normalize(raw);

		if (!isBodyWellFormed(body)) {
			throw new IllegalArgumentException("Not a player id");
		}

		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
					.digest(body.getBytes(StandardCharsets.US_ASCII));
			return hex(digest);
		} catch (NoSuchAlgorithmException missing) {
			throw new IllegalStateException("SHA-256 is required", missing);
		}
	}

	private static boolean isBodyWellFormed(String body) {
		if (body.length() != BODY_LENGTH) {
			return false;
		}

		for (int i = 0; i < body.length(); i++) {
			if (ALPHABET.indexOf(body.charAt(i)) < 0) {
				return false;
			}
		}

		return true;
	}

	private static char canonical(char character) {
		return switch (character) {
			case 'O' -> '0';
			case 'I', 'L' -> '1';
			default -> character;
		};
	}

	private static String hex(byte[] bytes) {
		StringBuilder hex = new StringBuilder(bytes.length * 2);

		for (byte value : bytes) {
			hex.append(Character.forDigit((value >>> 4) & 0xF, 16));
			hex.append(Character.forDigit(value & 0xF, 16));
		}

		return hex.toString();
	}

	private AccessTokens() {
	}
}
