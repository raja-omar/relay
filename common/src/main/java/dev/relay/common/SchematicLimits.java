package dev.relay.common;

/**
 * How big a shared schematic may be. Both sides use this: the client so it does not read a huge
 * placement into memory, the server so a bogus share cannot fill the process.
 */
public final class SchematicLimits {
	public static final int MAX_BYTES = 16 * 1024 * 1024;

	public static boolean looksLikeCompressedNbt(byte[] bytes) {
		return bytes != null && bytes.length >= 2 && bytes[0] == 0x1F && bytes[1] == (byte) 0x8B;
	}

	private SchematicLimits() {
	}
}
