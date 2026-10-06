package dev.relay.common;

import java.util.LinkedHashSet;
import java.util.UUID;

/**
 * A short memory of ids we have already seen, oldest forgotten first.
 *
 * <p>Used so the same share is not loaded or forwarded twice. Thread-safe: the server may see a
 * replay on a different connection while the client only touches this from Minecraft's thread.
 */
public final class RecentIds {
	private final int limit;
	private final LinkedHashSet<UUID> ids = new LinkedHashSet<>();

	public RecentIds(int limit) {
		this.limit = limit;
	}

	/** @return false when this id was already remembered */
	public synchronized boolean add(UUID id) {
		if (!ids.add(id)) {
			return false;
		}

		while (ids.size() > limit) {
			ids.remove(ids.iterator().next());
		}

		return true;
	}

	public synchronized boolean contains(UUID id) {
		return ids.contains(id);
	}

	public synchronized int size() {
		return ids.size();
	}
}
