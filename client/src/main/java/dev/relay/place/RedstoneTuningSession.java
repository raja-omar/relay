package dev.relay.place;

import java.util.Objects;

final class RedstoneTuningSession<W, P, S> {
	private W world;
	private P pendingPos;
	private S expectedState;
	private long deadlineNanos;
	private boolean blockedUntilUseRelease;

	void tick(W currentWorld, boolean usePressed, long nowNanos) {
		synchronizeWorld(currentWorld);
		if (expectedState != null && nowNanos - deadlineNanos >= 0L) {
			clearPending();
			blockedUntilUseRelease = true;
		}

		if (blockedUntilUseRelease && !usePressed) {
			blockedUntilUseRelease = false;
		}
	}

	boolean begin(W currentWorld, P pos, S expected, long deadlineNanos) {
		synchronizeWorld(currentWorld);
		if (currentWorld != null && pos != null && expected != null && expectedState == null && !blockedUntilUseRelease) {
			pendingPos = pos;
			expectedState = expected;
			this.deadlineNanos = deadlineNanos;
			return true;
		}

		return false;
	}

	boolean canBegin(W currentWorld) {
		synchronizeWorld(currentWorld);
		return currentWorld != null && expectedState == null && !blockedUntilUseRelease;
	}

	Update authoritativeUpdate(W currentWorld, P pos, S actualState) {
		synchronizeWorld(currentWorld);
		if (expectedState == null || !Objects.equals(pendingPos, pos)) {
			return Update.IGNORED;
		}

		if (Objects.equals(expectedState, actualState)) {
			clearPending();
			return Update.ACKNOWLEDGED;
		}

		clearPending();
		blockedUntilUseRelease = true;
		return Update.BLOCKED;
	}

	void blockPending(W currentWorld, P pos) {
		synchronizeWorld(currentWorld);
		if (expectedState != null && Objects.equals(pendingPos, pos)) {
			clearPending();
			blockedUntilUseRelease = true;
		}
	}

	private void synchronizeWorld(W currentWorld) {
		if (world != currentWorld) {
			world = currentWorld;
			clearPending();
			blockedUntilUseRelease = false;
		}
	}

	private void clearPending() {
		pendingPos = null;
		expectedState = null;
		deadlineNanos = 0L;
	}

	enum Update {
		IGNORED,
		ACKNOWLEDGED,
		BLOCKED
	}
}
