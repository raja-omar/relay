package dev.relay.group;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.relay.common.protocol.Message;
import dev.relay.common.protocol.PacketReader;
import dev.relay.common.protocol.ProtocolException;

/**
 * The group this client is in, as the server last described it.
 *
 * <p>A snapshot rather than something the client edits: every change arrives as a fresh
 * GROUP_UPDATE, so there is no local copy to get out of step with the server.
 *
 * @param members in join order, so the list reads the same for everyone
 */
public record GroupState(String name, UUID ownerId, List<Member> members) {
	public record Member(UUID id, String name, boolean online) {
	}

	/** @return empty when the server says this player is not in a group */
	public static Optional<GroupState> read(Message message) throws ProtocolException {
		PacketReader reader = message.reader();

		if (!reader.readBoolean()) {
			return Optional.empty();
		}

		String name = reader.readString();
		UUID ownerId = reader.readUuid();
		int count = reader.readInt();
		List<Member> members = new ArrayList<>(Math.min(count, 256));

		for (int i = 0; i < count; i++) {
			members.add(new Member(reader.readUuid(), reader.readString(), reader.readBoolean()));
		}

		return Optional.of(new GroupState(name, ownerId, List.copyOf(members)));
	}

	public Member owner() {
		return members.stream()
				.filter(member -> member.id().equals(ownerId))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("group " + name + " has no owner in its member list"));
	}

	public long onlineCount() {
		return members.stream().filter(Member::online).count();
	}
}
