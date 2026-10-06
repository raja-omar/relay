package dev.relay.schematic;

/**
 * A local schematic that cannot be used, carrying the reason in words a player can act on.
 *
 * <p>The message goes straight into chat, so it says what went wrong rather than which check
 * failed.
 */
public class SchematicException extends Exception {
	private static final long serialVersionUID = 1L;

	public SchematicException(String playerFacingReason) {
		super(playerFacingReason);
	}

	public SchematicException(String playerFacingReason, Throwable cause) {
		super(playerFacingReason, cause);
	}
}
