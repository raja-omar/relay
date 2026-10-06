package dev.relay.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AccessTokensTest {
	@Test
	void issuesDistinctWellFormedIds() {
		String first = AccessTokens.issue();
		String second = AccessTokens.issue();

		assertNotEquals(first, second);
		assertTrue(AccessTokens.isWellFormed(first), first);
		assertTrue(first.startsWith(AccessTokens.PREFIX));
		assertEquals(first, AccessTokens.display(first));
	}

	@Test
	void treatsGroupedAndBareFormsAsTheSameId() {
		String issued = AccessTokens.issue();
		String body = AccessTokens.normalize(issued);

		assertEquals(body, AccessTokens.normalize(body));
		assertEquals(AccessTokens.fingerprint(issued), AccessTokens.fingerprint(body.toLowerCase()));
		assertEquals(issued, AccessTokens.display("  " + body + "  "));
	}

	@Test
	void forgivesTheLettersPeopleTypeForDigits() {
		String body = "OILU0123ABCDEFGH";
		// O→0, I→1, L→1; U is not in the alphabet and stays U, so this is not well-formed.
		assertEquals("011U0123ABCDEFGH", AccessTokens.normalize(body));

		String typed = "rly_01O1-A23B-CDEF-GHJK";
		assertEquals("0101A23BCDEFGHJK", AccessTokens.normalize(typed));
		assertTrue(AccessTokens.isWellFormed(typed));
	}

	@Test
	void rejectsJunk() {
		assertFalse(AccessTokens.isWellFormed(""));
		assertFalse(AccessTokens.isWellFormed("steve"));
		assertFalse(AccessTokens.isWellFormed("rly_short"));
		assertFalse(AccessTokens.isWellFormed(null));
		assertThrows(IllegalArgumentException.class, () -> AccessTokens.fingerprint("nope"));
	}

	@Test
	void fingerprintsAreStableAndNotTheIdItself() {
		String id = AccessTokens.issue();

		assertEquals(64, AccessTokens.fingerprint(id).length());
		assertEquals(AccessTokens.fingerprint(id), AccessTokens.fingerprint(id));
		assertNotEquals(id.toLowerCase(), AccessTokens.fingerprint(id));
	}
}
