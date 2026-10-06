package dev.relay.server.group;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The group rules, with no sockets in sight. */
class GroupManagerTest {
	private final UUID alice = UUID.randomUUID();
	private final UUID beta = UUID.randomUUID();
	private final UUID gamma = UUID.randomUUID();

	private MovableClock clock;
	private GroupManager groups;

	@BeforeEach
	void setUp() {
		clock = new MovableClock();
		groups = new GroupManager(clock);
	}

	@Nested
	class Creating {
		@Test
		void makesTheCreatorTheOwnerAndOnlyMember() throws GroupException {
			Group group = groups.create(alice, "Alice", "Alpha");

			assertEquals("Alpha", group.name());
			assertEquals(alice, group.ownerId());
			assertEquals(List.of(alice), List.copyOf(group.members().keySet()));
			assertEquals("Alpha", groups.groupOf(alice).orElseThrow().name());
		}

		@Test
		void refusesANameAlreadyTakenWhateverTheCase() throws GroupException {
			groups.create(alice, "Alice", "Alpha");

			GroupException thrown = assertThrows(GroupException.class, () -> groups.create(beta, "Beta", "alpha"));

			assertEquals("A group called alpha already exists.", thrown.getMessage());
		}

		@Test
		void refusesAnUnusableName() {
			assertThrows(GroupException.class, () -> groups.create(alice, "Alice", "no"));
			assertThrows(GroupException.class, () -> groups.create(alice, "Alice", "has space"));
			assertEquals(0, groups.groupCount());
		}

		@Test
		void refusesToCreateASecondGroup() throws GroupException {
			groups.create(alice, "Alice", "Alpha");

			GroupException thrown = assertThrows(GroupException.class, () -> groups.create(alice, "Alice", "Beta"));

			assertTrue(thrown.getMessage().contains("already in Alpha"));
		}
	}

	@Nested
	class Inviting {
		@Test
		void letsAnyMemberInviteNotJustTheOwner() throws GroupException {
			groups.create(alice, "Alice", "Alpha");
			groups.invite(alice, beta);
			groups.accept(beta, "Beta", "Alpha");

			// Beta joined a moment ago and can already invite.
			Group group = groups.invite(beta, gamma);

			assertEquals("Alpha", group.name());
		}

		@Test
		void refusesWhenTheInviterHasNoGroup() {
			GroupException thrown = assertThrows(GroupException.class, () -> groups.invite(alice, beta));

			assertEquals("You are not in a group.", thrown.getMessage());
		}

		@Test
		void refusesToInviteAnExistingMember() throws GroupException {
			groups.create(alice, "Alice", "Alpha");
			groups.invite(alice, beta);
			groups.accept(beta, "Beta", "Alpha");

			GroupException thrown = assertThrows(GroupException.class, () -> groups.invite(alice, beta));

			assertEquals("That player is already in the group.", thrown.getMessage());
		}

		@Test
		void refusesToInviteYourself() throws GroupException {
			groups.create(alice, "Alice", "Alpha");

			assertThrows(GroupException.class, () -> groups.invite(alice, alice));
		}

		@Test
		void refusesToInviteSomeoneElsesMember() throws GroupException {
			groups.create(alice, "Alice", "Alpha");
			groups.create(beta, "Beta", "Bravo");

			GroupException thrown = assertThrows(GroupException.class, () -> groups.invite(alice, beta));

			assertEquals("That player is already in another group.", thrown.getMessage());
		}

		@Test
		void remembersWhoSentTheInvitation() throws GroupException {
			groups.create(alice, "Alice", "Alpha");
			groups.invite(alice, beta);

			assertEquals(alice, groups.decline(beta, "Alpha").inviterId());
		}
	}

	@Nested
	class Accepting {
		@Test
		void addsTheMemberAndClearsTheInvitation() throws GroupException {
			groups.create(alice, "Alice", "Alpha");
			groups.invite(alice, beta);

			Group group = groups.accept(beta, "Beta", "Alpha");

			assertEquals(2, group.size());
			assertTrue(group.hasMember(beta));
			assertEquals(0, groups.pendingInviteCount());
		}

		@Test
		void worksWhateverCaseTheNameIsTypedIn() throws GroupException {
			groups.create(alice, "Alice", "Alpha");
			groups.invite(alice, beta);

			assertEquals(2, groups.accept(beta, "Beta", "ALPHA").size());
		}

		@Test
		void refusesWithoutAnInvitation() {
			GroupException thrown = assertThrows(GroupException.class, () -> groups.accept(beta, "Beta", "Alpha"));

			assertEquals("You have no invitation to Alpha.", thrown.getMessage());
		}

		@Test
		void refusesAnInvitationThatSatTooLong() throws GroupException {
			groups.create(alice, "Alice", "Alpha");
			groups.invite(alice, beta);

			clock.advance(GroupManager.INVITE_LIFETIME.plusSeconds(1));

			GroupException thrown = assertThrows(GroupException.class, () -> groups.accept(beta, "Beta", "Alpha"));

			assertTrue(thrown.getMessage().contains("expired"));
			assertEquals(0, groups.pendingInviteCount());
		}

		@Test
		void stillWorksJustInsideTheDeadline() throws GroupException {
			groups.create(alice, "Alice", "Alpha");
			groups.invite(alice, beta);

			clock.advance(GroupManager.INVITE_LIFETIME.minusSeconds(1));

			assertEquals(2, groups.accept(beta, "Beta", "Alpha").size());
		}

		@Test
		void refusesOnceTheGroupHasBeenDissolved() throws GroupException {
			groups.create(alice, "Alice", "Alpha");
			groups.invite(alice, beta);
			groups.leave(alice);

			// Dissolving the group takes its invitations with it, so there is nothing left to accept.
			GroupException thrown = assertThrows(GroupException.class, () -> groups.accept(beta, "Beta", "Alpha"));

			assertEquals("You have no invitation to Alpha.", thrown.getMessage());
		}

		@Test
		void dropsOtherInvitationsOnceSomeoneJoins() throws GroupException {
			groups.create(alice, "Alice", "Alpha");
			groups.create(gamma, "Gamma", "Bravo");
			groups.invite(alice, beta);
			groups.invite(gamma, beta);
			assertEquals(2, groups.pendingInviteCount());

			groups.accept(beta, "Beta", "Alpha");

			assertEquals(0, groups.pendingInviteCount());
		}
	}

	@Nested
	class Declining {
		@Test
		void removesTheInvitationWithoutJoining() throws GroupException {
			groups.create(alice, "Alice", "Alpha");
			groups.invite(alice, beta);

			GroupManager.Declined declined = groups.decline(beta, "Alpha");

			assertEquals("Alpha", declined.groupName());
			assertEquals(alice, declined.inviterId());
			assertEquals(0, groups.pendingInviteCount());
			assertTrue(groups.groupOf(beta).isEmpty());
		}

		@Test
		void cannotBeDoneTwice() throws GroupException {
			groups.create(alice, "Alice", "Alpha");
			groups.invite(alice, beta);
			groups.decline(beta, "Alpha");

			assertThrows(GroupException.class, () -> groups.decline(beta, "Alpha"));
		}
	}

	@Nested
	class Leaving {
		@Test
		void refusesWhenTheresNothingToLeave() {
			GroupException thrown = assertThrows(GroupException.class, () -> groups.leave(alice));

			assertEquals("You are not in a group.", thrown.getMessage());
		}

		@Test
		void removesAPlainMemberAndLeavesTheGroupStanding() throws GroupException {
			groups.create(alice, "Alice", "Alpha");
			groups.invite(alice, beta);
			groups.accept(beta, "Beta", "Alpha");

			Group group = groups.leave(beta);

			assertEquals(1, group.size());
			assertEquals(alice, group.ownerId());
			assertEquals(1, groups.groupCount());
			assertTrue(groups.groupOf(beta).isEmpty());
		}

		@Test
		void handsOwnershipToTheLongestStandingMember() throws GroupException {
			groups.create(alice, "Alice", "Alpha");
			groups.invite(alice, beta);
			groups.accept(beta, "Beta", "Alpha");
			groups.invite(alice, gamma);
			groups.accept(gamma, "Gamma", "Alpha");

			Group group = groups.leave(alice);

			assertEquals(beta, group.ownerId());
			assertEquals(2, group.size());
		}

		@Test
		void dissolvesTheGroupWhenTheLastMemberGoes() throws GroupException {
			groups.create(alice, "Alice", "Alpha");

			Group group = groups.leave(alice);

			assertTrue(group.isEmpty());
			assertEquals(0, groups.groupCount());
			assertTrue(groups.groupOf(alice).isEmpty());
		}

		@Test
		void freesTheNameForReuse() throws GroupException {
			groups.create(alice, "Alice", "Alpha");
			groups.leave(alice);

			assertEquals("Alpha", groups.create(beta, "Beta", "Alpha").name());
		}

		@Test
		void discardsInvitationsToADissolvedGroup() throws GroupException {
			groups.create(alice, "Alice", "Alpha");
			groups.invite(alice, beta);

			groups.leave(alice);

			assertEquals(0, groups.pendingInviteCount());
		}

		@Test
		void letsSomeoneJoinAnotherGroupAfterwards() throws GroupException {
			groups.create(alice, "Alice", "Alpha");
			groups.create(beta, "Beta", "Bravo");
			groups.leave(alice);
			groups.invite(beta, alice);

			assertEquals("Bravo", groups.accept(alice, "Alice", "Bravo").name());
		}
	}

	@Test
	void keepsMembershipWhileAPlayerIsOffline() throws GroupException {
		// Nothing here knows about connections: membership is not tied to being online.
		groups.create(alice, "Alice", "Alpha");
		groups.invite(alice, beta);
		groups.accept(beta, "Beta", "Alpha");

		assertFalse(groups.groupOf(beta).isEmpty());
	}

	/** A clock the test moves by hand, so invite expiry does not depend on waiting. */
	private static final class MovableClock extends Clock {
		private Instant now = Instant.parse("2026-01-01T12:00:00Z");

		void advance(Duration amount) {
			now = now.plus(amount);
		}

		@Override
		public Instant instant() {
			return now;
		}

		@Override
		public ZoneOffset getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}
	}
}
