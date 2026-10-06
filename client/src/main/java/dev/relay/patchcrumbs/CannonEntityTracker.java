package dev.relay.patchcrumbs;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.phys.AABB;

/**
 * Tracks live TNT and falling-block entities with a short TTL.
 */
public final class CannonEntityTracker {
	public static final CannonEntityTracker INSTANCE = new CannonEntityTracker();

	/** entityId -> (seenAtMillis, entity) */
	public final Map<Integer, TimedEntity> entityPositions = new LinkedHashMap<>();

	private static final long CLEAR_TIME_MS = 5000L;
	private static final double SCAN_RADIUS = 384.0;

	private CannonEntityTracker() {
	}

	public void clear() {
		this.entityPositions.clear();
	}

	public void tick(ClientLevel world, Entity around, long now) {
		cleanUp(now);
		AABB box = around.getBoundingBox().inflate(SCAN_RADIUS);

		List<PrimedTnt> tntList = world.getEntitiesOfClass(PrimedTnt.class, box, e -> true);
		for (PrimedTnt tnt : tntList) {
			this.entityPositions.put(tnt.getId(), new TimedEntity(now, tnt));
		}

		List<FallingBlockEntity> falling = world.getEntitiesOfClass(FallingBlockEntity.class, box, e -> true);
		for (FallingBlockEntity entity : falling) {
			this.entityPositions.put(entity.getId(), new TimedEntity(now, entity));
		}
	}

	private void cleanUp(long now) {
		Iterator<Map.Entry<Integer, TimedEntity>> it = this.entityPositions.entrySet().iterator();
		while (it.hasNext()) {
			TimedEntity timed = it.next().getValue();
			Entity entity = timed.entity;
			if (now - timed.seenAt > CLEAR_TIME_MS
					|| entity == null
					|| entity.isRemoved()
					|| entity.tickCount > 90) {
				it.remove();
			}
		}
	}

	public record TimedEntity(long seenAt, Entity entity) {
	}
}
