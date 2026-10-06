package dev.relay.place;

final class PlacementRateLimiter {
	private static final long INTERVAL_NANOS = 83_333_334L;
	private static final long MUTATION_GAP_NANOS = 50_000_000L;

	private static Object rateTrackedWorld;
	private static long nextPermitAt;
	private static Object mutationTrackedWorld;
	private static long mutationReadyAt;

	private PlacementRateLimiter() {
	}

	static boolean tryAcquire(Object world) {
		return tryAcquireAt(System.nanoTime(), world);
	}

	static synchronized boolean tryAcquireAt(long now, Object world) {
		if (world != rateTrackedWorld) {
			rateTrackedWorld = world;
			nextPermitAt = now;
		}

		if (now >= nextPermitAt && isReadyAfterMutationAt(now, world)) {
			nextPermitAt = saturatedAdd(now, INTERVAL_NANOS);
			return true;
		}

		return false;
	}

	static boolean isReadyAfterMutation(Object world) {
		return isReadyAfterMutationAt(System.nanoTime(), world);
	}

	static synchronized boolean isReadyAfterMutationAt(long now, Object world) {
		return world != mutationTrackedWorld || now >= mutationReadyAt;
	}

	static synchronized void deferAfterMutation(Object world) {
		deferAfterMutationAt(System.nanoTime(), world);
	}

	static synchronized void deferAfterMutationAt(long now, Object world) {
		if (world != mutationTrackedWorld) {
			mutationTrackedWorld = world;
			mutationReadyAt = now;
		}

		long deferred = saturatedAdd(now, MUTATION_GAP_NANOS);
		if (deferred > mutationReadyAt) {
			mutationReadyAt = deferred;
		}
	}

	static synchronized void resetForTests() {
		rateTrackedWorld = null;
		nextPermitAt = 0L;
		mutationTrackedWorld = null;
		mutationReadyAt = 0L;
	}

	private static long saturatedAdd(long value, long increment) {
		long result = value + increment;
		return result < value ? Long.MAX_VALUE : result;
	}
}
