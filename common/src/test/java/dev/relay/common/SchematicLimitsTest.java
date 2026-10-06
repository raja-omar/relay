package dev.relay.common;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SchematicLimitsTest {
	@Test
	void recognisesAGzipHeader() {
		assertTrue(SchematicLimits.looksLikeCompressedNbt(new byte[] { 0x1F, (byte) 0x8B, 0x08 }));
		assertFalse(SchematicLimits.looksLikeCompressedNbt(new byte[] { 0x0A, 0x00 }));
		assertFalse(SchematicLimits.looksLikeCompressedNbt(new byte[] { 0x1F }));
		assertFalse(SchematicLimits.looksLikeCompressedNbt(new byte[0]));
	}
}
