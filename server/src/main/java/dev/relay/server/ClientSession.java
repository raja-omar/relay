package dev.relay.server;

import java.util.UUID;

import dev.relay.common.protocol.Message;

/**
 * A player who has authenticated, and the connection they are on.
 *
 * <p>The identity is the player the issued id belongs to. The client does not get to pick a name.
 */
public final class ClientSession {
	private final UUID playerId;
	private final String playerName;
	private final ClientConnection connection;

	public ClientSession(UUID playerId, String playerName, ClientConnection connection) {
		this.playerId = playerId;
		this.playerName = playerName;
		this.connection = connection;
	}

	public UUID playerId() {
		return playerId;
	}

	public String playerName() {
		return playerName;
	}

	public ClientConnection connection() {
		return connection;
	}

	public void send(Message message) {
		connection.send(message);
	}

	@Override
	public String toString() {
		return playerName + " (" + connection + ")";
	}
}
