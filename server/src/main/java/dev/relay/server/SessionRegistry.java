package dev.relay.server;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who is connected right now, keyed by player id. In memory only: if the server restarts,
 * everybody reconnects.
 */
public final class SessionRegistry {
	private final Map<UUID, ClientSession> byPlayerId = new ConcurrentHashMap<>();

	/**
	 * Registers a session, replacing any earlier one for the same player.
	 *
	 * @return the session that was displaced, if the player was already connected somewhere else
	 */
	public Optional<ClientSession> login(ClientSession session) {
		return Optional.ofNullable(byPlayerId.put(session.playerId(), session));
	}

	/** Removes a session, unless the player has already reconnected on a newer one. */
	public void logout(ClientSession session) {
		byPlayerId.remove(session.playerId(), session);
	}

	public Optional<ClientSession> byPlayerId(UUID playerId) {
		return Optional.ofNullable(byPlayerId.get(playerId));
	}

	/** Case-insensitive, because players type each other's names by hand. */
	public Optional<ClientSession> byPlayerName(String playerName) {
		return byPlayerId.values().stream()
				.filter(session -> session.playerName().equalsIgnoreCase(playerName))
				.findFirst();
	}

	public Collection<ClientSession> all() {
		return Collections.unmodifiableCollection(byPlayerId.values());
	}

	public int size() {
		return byPlayerId.size();
	}
}
