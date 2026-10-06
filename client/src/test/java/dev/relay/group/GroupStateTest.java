package dev.relay.group;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import dev.relay.common.protocol.Message;
import dev.relay.common.protocol.MessageType;
import dev.relay.common.protocol.PacketWriter;
import dev.relay.common.protocol.ProtocolException;

import org.junit.jupiter.api.Test;

class GroupStateTest {
	private static final UUID ALICE = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID BETA = UUID.fromString("22222222-2222-2222-2222-222222222222");

	@Test
	void readsAnEmptyGroupAsNone() throws ProtocolException {
		Message message = new PacketWriter().writeBoolean(false).toMessage(MessageType.GROUP_UPDATE);

		assertTrue(GroupState.read(message).isEmpty());
	}

	@Test
	void readsAGroupWithOnlineAndOfflineMembers() throws ProtocolException {
		Message message = new PacketWriter()
				.writeBoolean(true)
				.writeString("Alpha")
				.writeUuid(ALICE)
				.writeInt(2)
				.writeUuid(ALICE)
				.writeString("Alice")
				.writeBoolean(true)
				.writeUuid(BETA)
				.writeString("Beta")
				.writeBoolean(false)
				.toMessage(MessageType.GROUP_UPDATE);

		GroupState group = GroupState.read(message).orElseThrow();

		assertEquals("Alpha", group.name());
		assertEquals(ALICE, group.ownerId());
		assertEquals("Alice", group.owner().name());
		assertTrue(group.owner().online());
		assertEquals(2, group.members().size());
		assertEquals("Beta", group.members().get(1).name());
		assertFalse(group.members().get(1).online());
		assertEquals(1, group.onlineCount());
	}

	@Test
	void refusesAMemberListThatStopsEarly() {
		Message message = new PacketWriter()
				.writeBoolean(true)
				.writeString("Alpha")
				.writeUuid(ALICE)
				.writeInt(2)
				.writeUuid(ALICE)
				.writeString("Alice")
				.writeBoolean(true)
				.toMessage(MessageType.GROUP_UPDATE);

		assertThrows(ProtocolException.class, () -> GroupState.read(message));
	}
}
