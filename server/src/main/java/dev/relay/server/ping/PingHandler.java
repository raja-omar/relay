package dev.relay.server.ping;

import java.util.UUID;

import dev.relay.common.PingLocations;
import dev.relay.common.protocol.Message;
import dev.relay.common.protocol.PacketReader;
import dev.relay.common.protocol.PingPackets;
import dev.relay.common.protocol.ProtocolException;
import dev.relay.server.AbuseLimits;
import dev.relay.server.ClientSession;
import dev.relay.server.SessionRegistry;
import dev.relay.server.group.Group;
import dev.relay.server.group.GroupException;
import dev.relay.server.group.GroupManager;

/**
 * Copies a block ping to the other online members of the sender's group. Nothing is stored.
 */
public final class PingHandler {
	private final GroupManager groups;
	private final SessionRegistry sessions;
	private final AbuseLimits abuse;

	public PingHandler(GroupManager groups, SessionRegistry sessions, AbuseLimits abuse) {
		this.groups = groups;
		this.sessions = sessions;
		this.abuse = abuse;
	}

	public void handle(ClientSession session, Message message) throws ProtocolException {
		try {
			relay(session, message);
		} catch (GroupException refused) {
			session.connection().sendError(refused.getMessage());
		}
	}

	private void relay(ClientSession session, Message message) throws ProtocolException, GroupException {
		PacketReader reader = message.reader();
		int x = reader.readInt();
		int y = reader.readInt();
		int z = reader.readInt();
		int face = reader.readInt();
		String dimension = reader.readString();

		if (!reader.atEnd()) {
			throw new ProtocolException("Ping has trailing bytes");
		}

		if (!PingLocations.isValid(x, y, z, face, dimension)) {
			throw new GroupException("That ping is not a block in a world.");
		}

		Group group = groups.groupOf(session.playerId())
				.orElseThrow(() -> new GroupException("You are not in a group."));

		if (!abuse.allowPing(session.playerId())) {
			throw new GroupException("You are pinging too quickly. Wait a moment.");
		}

		Message pinged = PingPackets.pinged(session.playerName(), x, y, z, face, dimension);

		for (UUID memberId : group.members().keySet()) {
			if (memberId.equals(session.playerId())) {
				continue;
			}
			sessions.byPlayerId(memberId).ifPresent(member -> member.send(pinged));
		}
	}
}
