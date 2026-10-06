package dev.relay.place;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RedstoneTuningPolicyTest {
	@Test
	void advancesARepeaterUntilTheDelayMatches() {
		assertEquals(
				RedstoneTuningPolicy.Decision.ADVANCE,
				RedstoneTuningPolicy.decide(
						RedstoneTuningPolicy.GateType.REPEATER,
						RedstoneTuningPolicy.GateType.REPEATER,
						true,
						true,
						1,
						3));
		assertEquals(2, RedstoneTuningPolicy.nextSetting(RedstoneTuningPolicy.GateType.REPEATER, 1));
		assertEquals(1, RedstoneTuningPolicy.nextSetting(RedstoneTuningPolicy.GateType.REPEATER, 4));
	}

	@Test
	void refusesAGateFacingTheWrongWay() {
		assertEquals(
				RedstoneTuningPolicy.Decision.WRONG_FACING,
				RedstoneTuningPolicy.decide(
						RedstoneTuningPolicy.GateType.COMPARATOR,
						RedstoneTuningPolicy.GateType.COMPARATOR,
						true,
						false,
						0,
						1));
	}

	@Test
	void isDoneWhenTheSettingMatches() {
		assertEquals(
				RedstoneTuningPolicy.Decision.COMPLETE,
				RedstoneTuningPolicy.decide(
						RedstoneTuningPolicy.GateType.FENCE_GATE,
						RedstoneTuningPolicy.GateType.FENCE_GATE,
						true,
						true,
						1,
						1));
	}
}
