package dev.relay.common;

/**
 * What counts as a player name.
 *
 * <p>A name arrives from a client, is stored by the server, and is then shown in other players'
 * chat. Restricting it to Minecraft's own alphabet means nobody can smuggle formatting codes,
 * newlines or an impersonation attempt into someone else's chat window.
 */
public final class PlayerNames {
	public static final int MAX_LENGTH = 16;

	public static boolean isValid(String name) {
		if (name == null || name.isEmpty() || name.length() > MAX_LENGTH) {
			return false;
		}

		for (int i = 0; i < name.length(); i++) {
			if (!isAllowed(name.charAt(i))) {
				return false;
			}
		}

		return true;
	}

	private static boolean isAllowed(char character) {
		return (character >= 'a' && character <= 'z')
				|| (character >= 'A' && character <= 'Z')
				|| (character >= '0' && character <= '9')
				|| character == '_';
	}

	private PlayerNames() {
	}
}
