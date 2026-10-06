package dev.relay.network;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

/**
 * Who this Minecraft client is, from the schematic server's point of view.
 *
 * <p>Development clients run without a real account and all end up with the same blank UUID, which
 * would make two of them look like the same player reconnecting. Falling back to the usual
 * offline-mode UUID keeps them distinct, and costs nothing for real accounts.
 */
public final class PlayerIdentity {
	private static final UUID BLANK = new UUID(0L, 0L);

	public static String name(Minecraft client) {
		return client.getUser().getName();
	}

	public static UUID id(Minecraft client) {
		User user = client.getUser();
		UUID profileId = user.getProfileId();

		return profileId == null || BLANK.equals(profileId) ? offlineId(user.getName()) : profileId;
	}

	static UUID offlineId(String playerName) {
		return UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes(StandardCharsets.UTF_8));
	}

	private PlayerIdentity() {
	}
}
