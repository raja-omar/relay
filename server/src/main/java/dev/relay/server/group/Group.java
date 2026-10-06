package dev.relay.server.group;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One group: a name, an owner, and the people in it.
 *
 * <p>Members are kept in join order, which gives ownership a sensible successor when the owner
 * leaves. Names are remembered alongside ids so an offline member can still be listed.
 *
 * <p>Not thread safe on its own. Every group is only ever touched while holding the
 * {@link GroupManager} lock.
 */
public final class Group {
	private final String name;
	private final Map<UUID, String> members = new LinkedHashMap<>();

	private UUID ownerId;

	Group(String name, UUID ownerId, String ownerName) {
		this.name = name;
		this.ownerId = ownerId;
		this.members.put(ownerId, ownerName);
	}

	/** The name as the creator typed it. */
	public String name() {
		return name;
	}

	/** Lower case name, used for lookups so {@code Alpha} and {@code alpha} are the same group. */
	public String key() {
		return key(name);
	}

	public static String key(String name) {
		return name.toLowerCase(java.util.Locale.ROOT);
	}

	public UUID ownerId() {
		return ownerId;
	}

	public boolean hasMember(UUID playerId) {
		return members.containsKey(playerId);
	}

	/** Member ids mapped to their last known name, in join order. */
	public Map<UUID, String> members() {
		return Collections.unmodifiableMap(new LinkedHashMap<>(members));
	}

	public int size() {
		return members.size();
	}

	public boolean isEmpty() {
		return members.isEmpty();
	}

	void addMember(UUID playerId, String playerName) {
		members.put(playerId, playerName);
	}

	/** Removes a member, handing ownership to the next longest-standing one if the owner left. */
	void removeMember(UUID playerId) {
		members.remove(playerId);

		if (playerId.equals(ownerId) && !members.isEmpty()) {
			ownerId = members.keySet().iterator().next();
		}
	}

	@Override
	public String toString() {
		return name + " (" + members.size() + " members)";
	}
}
