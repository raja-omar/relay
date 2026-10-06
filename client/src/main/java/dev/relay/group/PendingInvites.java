package dev.relay.group;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

/**
 * Invitations waiting for the player to accept or decline. Chat still shows the buttons; this is
 * so the GUI can offer the same choice after those lines have scrolled away.
 *
 * <p>Lifetime matches the server: five minutes, then the invite is gone.
 */
public final class PendingInvites {
	public static final Duration LIFETIME = Duration.ofMinutes(5);

	private static final int LIMIT = 16;

	public record Invite(String groupName, String inviterName, Instant receivedAt) {
	}

	private final LinkedHashMap<String, Invite> invites = new LinkedHashMap<>();

	public void put(String groupName, String inviterName) {
		put(groupName, inviterName, Instant.now());
	}

	/** Tests pass an instant so expiry does not wait five minutes. */
	void put(String groupName, String inviterName, Instant receivedAt) {
		invites.remove(groupName);
		invites.put(groupName, new Invite(groupName, inviterName, receivedAt));

		while (invites.size() > LIMIT) {
			invites.remove(invites.keySet().iterator().next());
		}
	}

	public List<Invite> list() {
		return list(Instant.now());
	}

	List<Invite> list(Instant now) {
		forgetExpired(now);
		return List.copyOf(invites.values());
	}

	public Optional<Invite> take(String groupName) {
		return take(groupName, Instant.now());
	}

	Optional<Invite> take(String groupName, Instant now) {
		forgetExpired(now);
		return Optional.ofNullable(invites.remove(groupName));
	}

	public void clear() {
		invites.clear();
	}

	public int size() {
		return size(Instant.now());
	}

	int size(Instant now) {
		forgetExpired(now);
		return invites.size();
	}

	private void forgetExpired(Instant now) {
		invites.values().removeIf(invite -> invite.receivedAt().plus(LIFETIME).isBefore(now));
	}
}
