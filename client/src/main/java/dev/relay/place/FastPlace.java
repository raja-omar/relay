package dev.relay.place;

import java.util.OptionalInt;

import dev.relay.RelayClient;
import dev.relay.RelayConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.HitResult;

/**
 * Caps Minecraft's right-click delay so held block places can run faster than vanilla.
 */
public final class FastPlace {
	private FastPlace() {
	}

	/**
	 * The delay Fast Place will allow this tick, or empty when vanilla should be left alone.
	 */
	public static OptionalInt delayCap(Minecraft client) {
		if (client == null || client.player == null) {
			return OptionalInt.empty();
		}

		RelayConfig config = RelayClient.get().config();
		if (!config.fastPlace()) {
			return OptionalInt.empty();
		}

		if (!(client.player.getMainHandItem().getItem() instanceof BlockItem)
				&& !(client.player.getOffhandItem().getItem() instanceof BlockItem)) {
			return OptionalInt.empty();
		}

		if (client.hitResult == null || client.hitResult.getType() != HitResult.Type.BLOCK) {
			return OptionalInt.empty();
		}

		return OptionalInt.of(FastPlacePolicy.clampDelay(config.fastPlaceDelay()));
	}
}
