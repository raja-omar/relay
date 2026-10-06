package dev.relay.server;

import java.util.Optional;
import java.util.UUID;

import dev.relay.common.protocol.Message;
import dev.relay.common.protocol.MessageType;
import dev.relay.common.protocol.PacketReader;
import dev.relay.common.protocol.PacketWriter;
import dev.relay.common.protocol.Protocol;
import dev.relay.common.protocol.ProtocolException;
import dev.relay.server.group.GroupHandler;
import dev.relay.server.ping.PingHandler;
import dev.relay.server.player.PlayerDirectory;
import dev.relay.server.share.ShareHandler;

/**
 * Decides what a message means. Groups and in-memory schematic shares are handled here;
 * anything else is refused with an explanation.
 *
 * <p>Nothing here trusts what it is given. A malformed message raises {@link ProtocolException},
 * which the caller turns into an error reply and a disconnect. Identity comes from the issued
 * player id, never from a name the client claimed.
 */
public final class MessageRouter {
	private final PlayerDirectory players;
	private final SessionRegistry sessions;
	private final GroupHandler groups;
	private final ShareHandler shares;
	private final PingHandler pings;
	private final AbuseLimits abuse;

	public MessageRouter(PlayerDirectory players, SessionRegistry sessions, GroupHandler groups, ShareHandler shares,
			PingHandler pings, AbuseLimits abuse) {
		this.players = players;
		this.sessions = sessions;
		this.groups = groups;
		this.shares = shares;
		this.pings = pings;
		this.abuse = abuse;
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
			case GROUP_CREATE, GROUP_INVITE, GROUP_ACCEPT, GROUP_DECLINE, GROUP_LEAVE, GROUP_INFO ->
					groups.handle(connection.session(), message);
			case SCHEM_SHARE -> shares.handle(connection.session(), message);
			case BLOCK_PING -> pings.handle(connection.session(), message);
			default -> connection.sendError("The server does not handle " + message.type() + " yet");
		}
	}

	/** Called when a connection goes away, so the player's group sees them drop offline. */
	public void onDisconnected(ClientSession session) {
		groups.onSignedOut(session);
	}

	private void authenticate(ClientConnection connection, Message message) throws ProtocolException {
		PacketReader reader = message.reader();
		int version = reader.readInt();
		String token = reader.readString();

		if (version != Protocol.VERSION) {
			refuse(connection, "Client protocol " + version + " does not match server protocol "
					+ Protocol.VERSION + ". Update the mod or the server.");
			return;
		}

		if (token.isBlank()) {
			abuse.authFailed(connection.remoteAddress());
			refuse(connection, "This server needs a player id. Use /relay login <id>.");
			return;
		}

		if (!abuse.allowAuthAttempt(connection.remoteAddress())) {
			refuse(connection, "Too many failed sign-ins from this address. Wait a minute.");
			return;
		}

		Optional<PlayerDirectory.Entry> player = players.authenticate(token);

		if (player.isEmpty()) {
			abuse.authFailed(connection.remoteAddress());
			refuse(connection, "Unknown player id");
			return;
		}

		UUID playerId = player.get().playerId();
		String playerName = player.get().playerName();
		ClientSession session = new ClientSession(playerId, playerName, connection);
		connection.session(session);
		connection.admit();

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

		// They may already be in a group from an earlier connection, so send them its state.
		groups.onSignedIn(session);
	}

	private void refuse(ClientConnection connection, String reason) {
		Log.info("Refused " + connection + ": " + reason);
		connection.send(new PacketWriter()
				.writeBoolean(false)
				.writeString(reason)
				.toMessage(MessageType.AUTH_RESULT));
		connection.disconnect(reason);
	}
}
