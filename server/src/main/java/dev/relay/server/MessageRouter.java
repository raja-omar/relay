package dev.relay.server;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.UUID;

import dev.relay.common.PlayerNames;
import dev.relay.common.protocol.Message;
import dev.relay.common.protocol.MessageType;
import dev.relay.common.protocol.PacketReader;
import dev.relay.common.protocol.PacketWriter;
import dev.relay.common.protocol.Protocol;
import dev.relay.common.protocol.ProtocolException;

/**
 * Decides what a message means. This is where group handling and schematic transfers will be added;
 * for now it covers logging in and the keepalive.
 *
 * <p>Nothing here trusts what it is given. A malformed message raises {@link ProtocolException},
 * which the caller turns into an error reply and a disconnect.
 */
public final class MessageRouter {
	private final ServerConfig config;
	private final SessionRegistry sessions;

	public MessageRouter(ServerConfig config, SessionRegistry sessions) {
		this.config = config;
		this.sessions = sessions;
	}

	public void handle(ClientConnection connection, Message message) throws ProtocolException {
		if (message.type() == MessageType.PING) {
			connection.send(Message.of(MessageType.PONG));
			return;
		}

		if (connection.session() == null) {
			if (message.type() == MessageType.AUTH) {
				authenticate(connection, message);
			} else {
				connection.reject("Send AUTH before anything else");
			}

			return;
		}

		switch (message.type()) {
			case AUTH -> connection.reject("Already signed in");
			// Groups arrive in phase 3, schematics in phases 5 and 6.
			default -> connection.sendError("The server does not handle " + message.type() + " yet");
		}
	}

	private void authenticate(ClientConnection connection, Message message) throws ProtocolException {
		PacketReader reader = message.reader();
		int version = reader.readInt();
		UUID playerId = reader.readUuid();
		String playerName = reader.readString();
		String secret = reader.readString();

		if (version != Protocol.VERSION) {
			refuse(connection, "Client protocol " + version + " does not match server protocol "
					+ Protocol.VERSION + ". Update the mod or the server.");
			return;
		}

		if (!PlayerNames.isValid(playerName)) {
			refuse(connection, "That player name is not usable");
			return;
		}

		if (!secretMatches(secret)) {
			refuse(connection, "Wrong server secret");
			return;
		}

		ClientSession session = new ClientSession(playerId, playerName, connection);
		connection.session(session);

		Optional<ClientSession> displaced = sessions.login(session);
		displaced.ifPresent(previous -> {
			Log.info(previous + " is being replaced by a newer connection");
			previous.connection().reject("Signed in from somewhere else");
		});

		Log.info("Signed in " + session + ", " + sessions.size() + " online");
		connection.send(new PacketWriter()
				.writeBoolean(true)
				.writeString("Signed in as " + playerName)
				.toMessage(MessageType.AUTH_RESULT));
	}

	private void refuse(ClientConnection connection, String reason) {
		Log.info("Refused " + connection + ": " + reason);
		connection.send(new PacketWriter()
				.writeBoolean(false)
				.writeString(reason)
				.toMessage(MessageType.AUTH_RESULT));
		connection.disconnect(reason);
	}

	private boolean secretMatches(String offered) {
		if (!config.requiresSecret()) {
			return true;
		}

		// Constant time, so a wrong secret does not leak how much of it was right.
		return MessageDigest.isEqual(
				offered.getBytes(StandardCharsets.UTF_8),
				config.secret().getBytes(StandardCharsets.UTF_8));
	}
}
