package dev.relay.place;

final class RedstoneTuningPolicy {
	private RedstoneTuningPolicy() {
	}

	static Decision decide(
			GateType liveType,
			GateType wantedType,
			boolean sameBlock,
			boolean facingMatches,
			int liveSetting,
			int wantedSetting) {
		if (liveType == GateType.NONE || liveType != wantedType || !sameBlock) {
			return Decision.NOT_TARGET;
		}

		if (!facingMatches) {
			return Decision.WRONG_FACING;
		}

		if (validSetting(liveType, liveSetting) && validSetting(wantedType, wantedSetting)) {
			return liveSetting == wantedSetting ? Decision.COMPLETE : Decision.ADVANCE;
		}

		return Decision.NOT_TARGET;
	}

	static int nextSetting(GateType type, int setting) {
		if (!validSetting(type, setting)) {
			return -1;
		}

		if (type == GateType.REPEATER) {
			return setting == 4 ? 1 : setting + 1;
		}

		if (type == GateType.COMPARATOR || type == GateType.FENCE_GATE) {
			return setting == 0 ? 1 : 0;
		}

		return -1;
	}

	private static boolean validSetting(GateType type, int setting) {
		return type == GateType.REPEATER
				? setting >= 1 && setting <= 4
				: (type == GateType.COMPARATOR || type == GateType.FENCE_GATE) && (setting == 0 || setting == 1);
	}

	enum Decision {
		NOT_TARGET,
		WRONG_FACING,
		ADVANCE,
		COMPLETE
	}

	enum GateType {
		NONE,
		REPEATER,
		COMPARATOR,
		FENCE_GATE
	}
}
