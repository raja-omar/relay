package dev.relay.server.group;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import dev.relay.common.GroupNames;

/**
 * All group state, held in memory.
 *
 * <p>Restarting the server loses every group, which is the deal: groups are cheap to make again and
 * a faction's schematic drop box does not need a database.
 *
 * <p>Rules, kept as small as they can be:
 *
 * <ul>
 *   <li>A player is in at most one group. Leave before joining another.
 *   <li>Group names are unique regardless of case.
 *   <li>Any member may invite. That is the whole permission model.
 *   <li>Invites expire, so a forgotten one cannot be accepted weeks later.
 *   <li>When the owner leaves, the longest-standing remaining member takes over. When the last
 *       member leaves, the group is gone.
 * </ul>
 *
 * <p>Every method is synchronized on this manager. The operations are all map lookups, and one lock
 * is far easier to reason about than several.
 */
public final class GroupManager {
	public static final Duration INVITE_LIFETIME = Duration.ofMinutes(5);

	private final Map<String, Group> groupsByKey = new HashMap<>();
	private final Map<UUID, String> groupKeyByPlayer = new HashMap<>();
	private final Map<InviteKey, Invite> invites = new HashMap<>();
	private final Clock clock;

	public GroupManager() {
		this(Clock.systemUTC());
	}

	/** For tests that need to control invite expiry. */
	public GroupManager(Clock clock) {
		this.clock = clock;
	}

	public synchronized Group create(UUID playerId, String playerName, String groupName) throws GroupException {
		if (!GroupNames.isValid(groupName)) {
			throw new GroupException("\"" + groupName + "\" is not a usable group name. " + GroupNames.RULES);
		}

		requireNoGroup(playerId);

		if (groupsByKey.containsKey(Group.key(groupName))) {
			throw new GroupException("A group called " + groupName + " already exists.");
		}

		Group group = new Group(groupName, playerId, playerName);
		groupsByKey.put(group.key(), group);
		groupKeyByPlayer.put(playerId, group.key());
		forgetInvitesTo(playerId);
		return group;
	}

	/**
	 * Records an invitation. The caller resolves the target's name to an id first, which is also
	 * where "that player is not online" is decided.
	 *
	 * @return the group the target has been invited to
	 */
	public synchronized Group invite(UUID inviterId, UUID targetId) throws GroupException {
		Group group = requireGroup(inviterId);

		if (inviterId.equals(targetId)) {
			throw new GroupException("You are already in that group.");
		}

		if (group.hasMember(targetId)) {
			throw new GroupException("That player is already in the group.");
		}

		if (groupOf(targetId).isPresent()) {
			throw new GroupException("That player is already in another group.");
		}

		invites.put(new InviteKey(targetId, group.key()), new Invite(inviterId, clock.instant()));
		return group;
	}

	public synchronized Group accept(UUID playerId, String playerName, String groupName) throws GroupException {
		InviteKey key = new InviteKey(playerId, Group.key(groupName));
		Invite invite = invites.get(key);

		if (invite == null) {
			throw new GroupException("You have no invitation to " + groupName + ".");
		}

		if (hasExpired(invite.sentAt())) {
			invites.remove(key);
			throw new GroupException("Your invitation to " + groupName + " has expired.");
		}

		Group group = groupsByKey.get(key.groupKey());

		if (group == null) {
			invites.remove(key);
			throw new GroupException("The group " + groupName + " no longer exists.");
		}

		requireNoGroup(playerId);

		group.addMember(playerId, playerName);
		groupKeyByPlayer.put(playerId, group.key());
		forgetInvitesTo(playerId);
		return group;
	}

	/** @return what was declined, so whoever sent the invitation can be told */
	public synchronized Declined decline(UUID playerId, String groupName) throws GroupException {
		InviteKey key = new InviteKey(playerId, Group.key(groupName));
		Invite invite = invites.remove(key);

		if (invite == null) {
			throw new GroupException("You have no invitation to " + groupName + ".");
		}

		Group group = groupsByKey.get(key.groupKey());
		return new Declined(group == null ? groupName : group.name(), invite.inviterId());
	}

	/**
	 * Removes a player from their group.
	 *
	 * @return the group as it now stands. It is empty and no longer registered when that player was
	 *         the last one in it.
	 */
	public synchronized Group leave(UUID playerId) throws GroupException {
		Group group = requireGroup(playerId);
		group.removeMember(playerId);
		groupKeyByPlayer.remove(playerId);

		if (group.isEmpty()) {
			groupsByKey.remove(group.key());
			invites.keySet().removeIf(key -> key.groupKey().equals(group.key()));
		}

		return group;
	}

	public synchronized Optional<Group> groupOf(UUID playerId) {
		String key = groupKeyByPlayer.get(playerId);
		return key == null ? Optional.empty() : Optional.ofNullable(groupsByKey.get(key));
	}

	public synchronized int groupCount() {
		return groupsByKey.size();
	}

	public synchronized int pendingInviteCount() {
		invites.values().removeIf(invite -> hasExpired(invite.sentAt()));
		return invites.size();
	}

	private Group requireGroup(UUID playerId) throws GroupException {
		return groupOf(playerId).orElseThrow(() -> new GroupException("You are not in a group."));
	}

	private void requireNoGroup(UUID playerId) throws GroupException {
		Optional<Group> existing = groupOf(playerId);

		if (existing.isPresent()) {
			throw new GroupException("You are already in " + existing.get().name()
					+ ". Leave it first with /relay group leave.");
		}
	}

	private void forgetInvitesTo(UUID playerId) {
		invites.keySet().removeIf(key -> key.targetId().equals(playerId));
	}

	private boolean hasExpired(Instant sentAt) {
		return sentAt.plus(INVITE_LIFETIME).isBefore(clock.instant());
	}

	/** What a declined invitation was for. */
	public record Declined(String groupName, UUID inviterId) {
	}

	private record InviteKey(UUID targetId, String groupKey) {
	}

	private record Invite(UUID inviterId, Instant sentAt) {
	}
}
