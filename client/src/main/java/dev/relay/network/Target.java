package dev.relay.network;

import dev.relay.common.protocol.Message;
import dev.relay.common.protocol.MessageType;
import dev.relay.common.protocol.PacketWriter;
import dev.relay.common.protocol.Protocol;

/**
 * Where to connect, and the player id we present.
 *
 * @param token  issued player id; empty when the player has not logged in yet
 * @param tlsPin SHA-256 of the server certificate; packed in the jar for a sold build
 */
public record Target(String host, int port, String token, String tlsPin) {
	/** The first message on every connection. */
	Message authMessage() {
		return new PacketWriter()
				.writeInt(Protocol.VERSION)
				.writeString(token)
				.toMessage(MessageType.AUTH);
	}
}
