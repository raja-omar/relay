package dev.relay.server.share;

import java.util.UUID;

import dev.relay.common.PlacementNames;
import dev.relay.common.RecentIds;
import dev.relay.common.SchematicLimits;
import dev.relay.common.protocol.Message;
import dev.relay.common.protocol.MessageType;
import dev.relay.common.protocol.PacketReader;
import dev.relay.common.protocol.PacketWriter;
import dev.relay.common.protocol.ProtocolException;
import dev.relay.common.protocol.SharePackets;
import dev.relay.server.AbuseLimits;
import dev.relay.server.ClientSession;
import dev.relay.server.Log;
import dev.relay.server.SessionRegistry;
import dev.relay.server.group.Group;
import dev.relay.server.group.GroupException;
import dev.relay.server.group.GroupManager;

/**
 * Relays a shared placement to the other online members of the sender's group.
 *
 * <p>The bytes are held only long enough to copy them onto each recipient's socket. Nothing is
 * written to disk, and nothing is kept after the send besides the transfer id, so a replay is
 * ignored.
 */
public final class ShareHandler {
	private static final int REMEMBERED_TRANSFERS = 128;

	private final GroupManager groups;
	private final SessionRegistry sessions;
	private final AbuseLimits abuse;
	private final RecentIds recent = new RecentIds(REMEMBERED_TRANSFERS);

	public ShareHandler(GroupManager groups, SessionRegistry sessions, AbuseLimits abuse) {
		this.groups = groups;
		this.sessions = sessions;
		this.abuse = abuse;
	}

	public void handle(ClientSession session, Message message) throws ProtocolException {
		try {
			share(session, message);
		} catch (GroupException refused) {
			session.connection().sendError(refused.getMessage());
		}
	}

	private void share(ClientSession session, Message message) throws ProtocolException, GroupException {
		PacketReader reader = message.reader();
		UUID transferId = reader.readUuid();
		String name = reader.readString().trim();
		byte[] data = reader.readBytes(SchematicLimits.MAX_BYTES);

		if (!PlacementNames.isValid(name)) {
			throw new GroupException(PlacementNames.RULES);
		}

		if (data.length == 0) {
			throw new GroupException("That schematic is empty.");
		}

		if (!SchematicLimits.looksLikeCompressedNbt(data)) {
			throw new GroupException("That file is not a usable schematic.");
		}

		Group group = groups.groupOf(session.playerId())
				.orElseThrow(() -> new GroupException("You are not in a group."));

		if (!abuse.allowShare(session.playerId(), data.length)) {
			throw new GroupException("You are sharing too quickly. Wait a moment.");
		}

		if (!recent.add(transferId)) {
			throw new GroupException("That share was already sent.");
		}

		Message shared = SharePackets.shared(transferId, session.playerName(), name, data);
		int delivered = 0;

		for (UUID memberId : group.members().keySet()) {
			if (memberId.equals(session.playerId())) {
				continue;
			}

			if (sessions.byPlayerId(memberId).map(member -> {
				member.send(shared);
				return true;
			}).orElse(false)) {
				delivered++;
			}
		}

		Log.info(session.playerName() + " shared " + name + " with " + group.name()
				+ " (" + delivered + " online)");

		if (delivered == 0) {
			session.send(notice("Shared " + name + ", but nobody else in " + group.name()
					+ " is online."));
			return;
		}

		session.send(notice("Shared " + name + " with " + group.name() + "."));
	}

	private static Message notice(String text) {
		return new PacketWriter().writeString(text).toMessage(MessageType.NOTICE);
	}
}
