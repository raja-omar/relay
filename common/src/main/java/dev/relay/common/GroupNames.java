package dev.relay.common;

/**
 * The rules for what a group may be called.
 *
 * <p>Lives in the shared module because both sides need it: the client so it can reject a typo
 * before opening a socket, and the server because it is the authority that actually creates
 * the group.
 */
public final class GroupNames {
	public static final int MIN_LENGTH = 3;
	public static final int MAX_LENGTH = 24;

	/** The rules written out for a player, used in chat error messages. */
	public static final String RULES = "Group names must be " + MIN_LENGTH + "-" + MAX_LENGTH
			+ " characters long and may only contain letters, digits, underscores and dashes.";

	public static boolean isValid(String name) {
		if (name == null || name.length() < MIN_LENGTH || name.length() > MAX_LENGTH) {
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
				|| character == '_'
				|| character == '-';
	}

	private GroupNames() {
	}
}
