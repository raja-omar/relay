package dev.relay.place;

import java.lang.reflect.Method;

/**
 * If FastPlace is installed, wait for its own place window so we do not fire a second use packet
 * in the same tick.
 */
final class FastPlaceCompat {
	private static final String FAST_PLACE_CLASS = "dev.daexe.fastplace.FastPlaceMod";

	private static boolean lookupComplete;
	private static boolean modPresent;
	private static Method canUseBlockNow;

	private FastPlaceCompat() {
	}

	static boolean canUseBlockNow() {
		resolve();
		if (!modPresent) {
			return true;
		}

		if (canUseBlockNow == null) {
			return false;
		}

		try {
			return Boolean.TRUE.equals(canUseBlockNow.invoke(null));
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			return false;
		}
	}

	private static synchronized void resolve() {
		if (lookupComplete) {
			return;
		}

		lookupComplete = true;
		try {
			Class<?> fastPlace = Class.forName(FAST_PLACE_CLASS);
			modPresent = true;
			canUseBlockNow = fastPlace.getMethod("canUseBlockNow");
		} catch (ClassNotFoundException missing) {
			modPresent = false;
		} catch (NoSuchMethodException | RuntimeException missingMethod) {
			modPresent = true;
			canUseBlockNow = null;
		}
	}
}
