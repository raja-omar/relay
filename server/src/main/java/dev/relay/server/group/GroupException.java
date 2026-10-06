package dev.relay.server.group;

/**
 * A group action that cannot be carried out, carrying the reason in words a player can act on.
 *
 * <p>The message goes straight into someone's chat, so it says what went wrong rather than which
 * check failed.
 */
public class GroupException extends Exception {
	private static final long serialVersionUID = 1L;

	public GroupException(String playerFacingReason) {
		super(playerFacingReason);
	}
}
