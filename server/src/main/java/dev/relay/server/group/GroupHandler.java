package dev.relay.server.group;

import java.util.UUID;

import dev.relay.common.PlayerNames;
import dev.relay.common.protocol.Message;
import dev.relay.common.protocol.MessageType;
import dev.relay.common.protocol.PacketWriter;
import dev.relay.common.protocol.ProtocolException;
import dev.relay.server.ClientSession;
import dev.relay.server.Log;
import dev.relay.server.SessionRegistry;

/**
 * Turns group messages into {@link GroupManager} calls, and the results into messages for whoever
 * needs to know.
 *
 * <p>The rules live in {@link GroupManager}, which knows nothing about sockets. This class is only
 * translation: read the request, ask the manager, tell the group.
 */
public final class GroupHandler {
	private static final Message NOT_IN_A_GROUP = new PacketWriter()
			.writeBoolean(false)
			.toMessage(MessageType.GROUP_UPDATE);

	private final GroupManager groups;
	private final SessionRegistry sessions;

	public GroupHandler(GroupManager groups, SessionRegistry sessions) {
		this.groups = groups;
		this.sessions = sessions;
	}

	public void handle(ClientSession session, Message message) throws ProtocolException {
		try {
			switch (message.type()) {
				case GROUP_CREATE -> create(session, message);
				case GROUP_INVITE -> invite(session, message);
				case GROUP_ACCEPT -> accept(session, message);
				case GROUP_DECLINE -> decline(session, message);
				case GROUP_LEAVE -> leave(session);
				case GROUP_INFO -> sendGroupState(session);
				default -> throw new ProtocolException(message.type() + " is not a group message");
			}
		} catch (GroupException refused) {
			// Expected: the player asked for something they cannot have, and gets told why.
			session.connection().sendError(refused.getMessage());
		}
	}

	/** Sends a player their group as soon as they sign in, and tells the group they are back. */
	public void onSignedIn(ClientSession session) {
		groups.groupOf(session.playerId()).ifPresentOrElse(
				group -> {
					notify(group, session.playerName() + " is online.", session.playerId());
					broadcastState(group);
				},
				() -> session.send(NOT_IN_A_GROUP));
	}

	/** Group membership outlives a connection, so this only refreshes everyone's online column. */
	public void onSignedOut(ClientSession session) {
		groups.groupOf(session.playerId()).ifPresent(group -> {
			notify(group, session.playerName() + " went offline.", session.playerId());
			broadcastState(group);
		});
	}

	private void create(ClientSession session, Message message) throws ProtocolException, GroupException {
		String name = message.reader().readString();
		Group group = groups.create(session.playerId(), session.playerName(), name);

		Log.info(session.playerName() + " created group " + group.name());
		session.send(notice("Created " + group.name() + ". Invite people with /relay group invite <player>."));
		session.send(state(group));
	}

	private void invite(ClientSession session, Message message) throws ProtocolException, GroupException {
		String targetName = message.reader().readString();

		if (!PlayerNames.isValid(targetName)) {
			throw new GroupException("\"" + targetName + "\" is not a player name.");
		}

		ClientSession target = sessions.byPlayerName(targetName)
				.orElseThrow(() -> new GroupException(targetName + " is not connected to the schematic server."));

		Group group = groups.invite(session.playerId(), target.playerId());

		Log.info(session.playerName() + " invited " + target.playerName() + " to " + group.name());
		session.send(notice("Invited " + target.playerName() + " to " + group.name() + "."));
		target.send(new PacketWriter()
				.writeString(group.name())
				.writeString(session.playerName())
				.toMessage(MessageType.INVITE));
	}

	private void accept(ClientSession session, Message message) throws ProtocolException, GroupException {
		String groupName = message.reader().readString();
		Group group = groups.accept(session.playerId(), session.playerName(), groupName);

		Log.info(session.playerName() + " joined " + group.name());
		session.send(notice("You joined " + group.name() + "."));
		notify(group, session.playerName() + " joined " + group.name() + ".", session.playerId());
		broadcastState(group);
	}

	private void decline(ClientSession session, Message message) throws ProtocolException, GroupException {
		String groupName = message.reader().readString();
		GroupManager.Declined declined = groups.decline(session.playerId(), groupName);

		session.send(notice("Declined the invitation to " + declined.groupName() + "."));
		sessions.byPlayerId(declined.inviterId()).ifPresent(inviter -> inviter.send(
				notice(session.playerName() + " declined your invitation to " + declined.groupName() + ".")));
	}

	private void leave(ClientSession session) throws GroupException {
		boolean wasOwner = groups.groupOf(session.playerId())
				.map(group -> session.playerId().equals(group.ownerId()))
				.orElse(false);

		Group group = groups.leave(session.playerId());

		Log.info(session.playerName() + " left " + group.name());
		session.send(notice("You left " + group.name() + "."));
		session.send(NOT_IN_A_GROUP);

		if (group.isEmpty()) {
			Log.info(group.name() + " has no members left and is gone");
			return;
		}

		notify(group, session.playerName() + " left " + group.name() + ".", session.playerId());

		if (wasOwner) {
			notify(group, group.members().get(group.ownerId()) + " now owns " + group.name() + ".", null);
		}

		broadcastState(group);
	}

	private void sendGroupState(ClientSession session) {
		groups.groupOf(session.playerId())
				.ifPresentOrElse(group -> session.send(state(group)), () -> session.send(NOT_IN_A_GROUP));
	}

	private void broadcastState(Group group) {
		Message state = state(group);
		group.members().keySet().forEach(memberId -> sessions.byPlayerId(memberId)
				.ifPresent(member -> member.send(state)));
	}

	/** Same message to every online member, skipping {@code exceptPlayerId} when one is given. */
	private void notify(Group group, String text, UUID exceptPlayerId) {
		Message message = notice(text);
		group.members().keySet().stream()
				.filter(memberId -> !memberId.equals(exceptPlayerId))
				.forEach(memberId -> sessions.byPlayerId(memberId).ifPresent(member -> member.send(message)));
	}

	private static Message notice(String text) {
		return new PacketWriter().writeString(text).toMessage(MessageType.NOTICE);
	}

	/** Everything a client needs to show the group, including who is online right now. */
	private Message state(Group group) {
		PacketWriter writer = new PacketWriter()
				.writeBoolean(true)
				.writeString(group.name())
				.writeUuid(group.ownerId())
				.writeInt(group.size());

		group.members().forEach((memberId, memberName) -> writer
				.writeUuid(memberId)
				.writeString(memberName)
				.writeBoolean(sessions.byPlayerId(memberId).isPresent()));

		return writer.toMessage(MessageType.GROUP_UPDATE);
	}
}
