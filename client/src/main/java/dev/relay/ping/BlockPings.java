package dev.relay.ping;

import java.util.ArrayList;
import java.util.List;

import dev.relay.RelayClient;
import dev.relay.RelayConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Beams on this client. One is kept unless Multiple is on, and each fades after this client's timer.
 * A ping from the group is drawn the same way, with these settings rather than the sender's.
 */
public final class BlockPings {
	private static final List<BlockPing> ACTIVE = new ArrayList<>();

	private BlockPings() {
	}

	public static void onClientTick(Minecraft client) {
		if (client.level == null) {
			ACTIVE.clear();
			return;
		}
		keepOnlyTheLatestWhenSingle();
		prune(System.currentTimeMillis());
	}

	/** Record the block under the crosshair. Air and entities are ignored. */
	public static void ping(Minecraft client) {
		if (client.player == null || client.level == null) {
			return;
		}
		if (!(client.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
			return;
		}

		BlockPos pos = hit.getBlockPos();
		Direction face = hit.getDirection();
		add(pos, face);
		RelayConfig config = RelayClient.get().config();
		if (config.pingShare()) {
			RelayClient.get().sharePing(
					pos.getX(), pos.getY(), pos.getZ(), face.get3DDataValue(),
					client.level.dimension().identifier().toString());
		}
	}

	/**
	 * A ping another group member sent. Ignored when it is for a different dimension.
	 * Duration and single-or-multiple come from this client.
	 */
	public static void receive(Minecraft client, int x, int y, int z, int face, String dimension) {
		if (client.player == null || client.level == null) {
			return;
		}
		if (!client.level.dimension().identifier().toString().equals(dimension)) {
			return;
		}
		Direction direction = Direction.from3DDataValue(face);
		if (direction.get3DDataValue() != face) {
			return;
		}
		add(new BlockPos(x, y, z), direction);
	}

	private static void add(BlockPos pos, Direction face) {
		long now = System.currentTimeMillis();
		prune(now);
		if (!RelayClient.get().config().pingMultiple()) {
			ACTIVE.clear();
		}
		long duration = PingPolicy.clampTimeout(RelayClient.get().config().pingTimeout()) * 1000L;
		ACTIVE.add(new BlockPing(pos, face, now + duration));
	}

	public static List<BlockPing> live() {
		prune(System.currentTimeMillis());
		return List.copyOf(ACTIVE);
	}

	public static void clear() {
		ACTIVE.clear();
	}

	private static void keepOnlyTheLatestWhenSingle() {
		if (RelayClient.get().config().pingMultiple() || ACTIVE.size() <= 1) {
			return;
		}
		BlockPing latest = ACTIVE.get(ACTIVE.size() - 1);
		ACTIVE.clear();
		ACTIVE.add(latest);
	}

	private static void prune(long now) {
		ACTIVE.removeIf(ping -> now >= ping.expiresAt());
	}
}
