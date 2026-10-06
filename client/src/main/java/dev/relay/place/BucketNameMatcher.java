package dev.relay.place;

import java.text.Normalizer;
import java.util.Locale;

final class BucketNameMatcher {
	private BucketNameMatcher() {
	}

	static boolean matchesInfiniteWaterGenerator(String displayName) {
		if (displayName == null || displayName.isBlank()) {
			return false;
		}

		String normalized = Normalizer.normalize(displayName, Normalizer.Form.NFKC)
				.toLowerCase(Locale.ROOT)
				.replaceAll("[^a-z0-9]+", " ")
				.trim()
				.replaceAll("\\s+", " ");
		String padded = " " + normalized + " ";
		return padded.contains(" infinite gen bucket ") && padded.contains(" water ");
	}
}
