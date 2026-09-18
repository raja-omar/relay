package dev.relay.common.protocol;

import java.io.IOException;

/**
 * A message that does not make sense: bad length, unknown type, or fields that ran out early.
 *
 * <p>An {@link IOException} on purpose, because in practice it always ends the same way as one --
 * the connection is no longer trustworthy and gets closed.
 */
public class ProtocolException extends IOException {
	private static final long serialVersionUID = 1L;

	public ProtocolException(String message) {
		super(message);
	}
}
