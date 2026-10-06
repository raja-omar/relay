package dev.relay.schematic;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Shared placements waiting for the player to click Download. Oldest are forgotten first, so a
 * long session cannot hold every share that ever arrived.
 */
public final class PendingShares {
	public record Share(UUID id, String senderName, String name, byte[] data) {
	}

	private final int limit;
	private final LinkedHashMap<UUID, Share> shares = new LinkedHashMap<>();

	public PendingShares(int limit) {
		this.limit = limit;
	}

	/** @return false when this id is already waiting */
	public boolean put(Share share) {
		if (shares.containsKey(share.id())) {
			return false;
		}

		shares.put(share.id(), share);

		while (shares.size() > limit) {
			shares.remove(shares.keySet().iterator().next());
		}

		return true;
	}

	/** Removes the share so Download cannot load the same bytes twice. */
	public Optional<Share> take(UUID id) {
		return Optional.ofNullable(shares.remove(id));
	}

	/** Waiting shares, oldest first. Does not take any of them. */
	public List<Share> list() {
		return List.copyOf(shares.values());
	}

	public int size() {
		return shares.size();
	}
}
