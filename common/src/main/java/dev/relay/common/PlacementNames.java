package dev.relay.common;

/**
 * What a shared placement may be called. The name is shown in other people's chat, so it cannot
 * carry newlines or formatting.
 */
public final class PlacementNames {
	public static final int MAX_LENGTH = 64;

	public static final String RULES = "Placement names must be 1-" + MAX_LENGTH
			+ " characters and cannot contain line breaks or other invisible characters.";

	public static boolean isValid(String name) {
		if (name == null || name.isEmpty() || name.length() > MAX_LENGTH) {
			return false;
		}

		for (int i = 0; i < name.length(); i++) {
			if (Character.isISOControl(name.charAt(i))) {
				return false;
			}
		}

		return true;
	}

	/** Drops control characters and trims. Empty when nothing usable is left. */
	public static String sanitise(String name) {
		if (name == null) {
			return "";
		}

		StringBuilder cleaned = new StringBuilder(Math.min(name.length(), MAX_LENGTH));

		for (int i = 0; i < name.length() && cleaned.length() < MAX_LENGTH; i++) {
			char character = name.charAt(i);

			if (!Character.isISOControl(character)) {
				cleaned.append(character);
			}
		}

		return cleaned.toString().trim();
	}

	private PlacementNames() {
	}
}
