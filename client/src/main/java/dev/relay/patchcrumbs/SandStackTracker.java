package dev.relay.patchcrumbs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Sand-stack crumb detector: tag vertically falling TNT/sand, count FallingBlock
 * blocks below, and place the crumb at column-entry Y.
 */
public final class SandStackTracker {
	public static final SandStackTracker INSTANCE = new SandStackTracker();

	private static final int SAND_SCAN_RANGE = 15;
	private static final long COLUMN_IDLE_MS = 2000L;

	private final Map<Integer, Attachment> attachments = new HashMap<>();
	private final Map<Integer, ColumnEntry> columnEntries = new HashMap<>();
	private final Map<Long, Column> columns = new HashMap<>();
	private final Map<Long, Boolean> fallingBlockCache = new HashMap<>();
	private final Map<Long, Boolean> dispenserChunkCache = new HashMap<>();
	private ClientLevel world;

	private SandStackTracker() {
	}

	public void clear() {
		this.attachments.clear();
		this.columnEntries.clear();
		this.columns.clear();
		this.fallingBlockCache.clear();
		this.dispenserChunkCache.clear();
		this.world = null;
	}

	public Hit tick(ClientLevel world, Iterable<Entity> entities, PatchCrumb currentCrumb) {
		this.world = world;
		this.fallingBlockCache.clear();
		this.dispenserChunkCache.clear();
		Hit hit = null;

		for (Entity entity : entities) {
			if (!isCandidate(entity)) {
				continue;
			}
			updateColumnEntry(entity);
			tryTag(entity, currentCrumb);
		}

		Iterator<Map.Entry<Long, Column>> it = this.columns.entrySet().iterator();
		while (it.hasNext()) {
			Column column = it.next().getValue();
			Hit columnHit = column.process();
			// First hit wins: later overstack TNT/sand in another column must not raise Y.
			if (hit == null && columnHit != null) {
				hit = columnHit;
			}
			if (column.isExpired(currentCrumb)) {
				it.remove();
			}
		}

		this.world = null;
		this.fallingBlockCache.clear();
		this.dispenserChunkCache.clear();
		return hit;
	}

	private void tryTag(Entity entity, PatchCrumb currentCrumb) {
		if (this.attachments.containsKey(entity.getId())) {
			return;
		}
		if (entity.getDeltaMovement().x != 0.0 || entity.getDeltaMovement().z != 0.0) {
			return;
		}
		int x = Mth.floor(entity.getX());
		int z = Mth.floor(entity.getZ());
		if (currentCrumb != null
				&& Math.abs(currentCrumb.posX - x) <= 1
				&& Math.abs(currentCrumb.posZ - z) <= 1) {
			return;
		}
		int chunkX = x >> 4;
		int chunkZ = z >> 4;
		Attachment attachment = new Attachment(chunkX, chunkZ, countSandBelow(entity), chunkHasDispenser(chunkX, chunkZ));
		this.attachments.put(entity.getId(), attachment);
		columnAt(x, z).add(new TrackedEntity(entity));
	}

	private void updateColumnEntry(Entity entity) {
		// After tagging, freeze entry Y so sand push / integer XZ jitter cannot raise it.
		if (this.attachments.containsKey(entity.getId())) {
			return;
		}
		int id = entity.getId();
		int ix = (int) entity.getX();
		int iz = (int) entity.getZ();
		ColumnEntry entry = this.columnEntries.get(id);
		if (entry == null) {
			this.columnEntries.put(id, new ColumnEntry(ix, iz, entity.getY()));
			return;
		}
		if (ix != entry.lastIntX || iz != entry.lastIntZ) {
			entry.entryY = entity.getY();
			entry.lastIntX = ix;
			entry.lastIntZ = iz;
		}
	}

	private Column columnAt(int x, int z) {
		long key = pack(x, z);
		Column column = this.columns.get(key);
		if (column != null && column.done) {
			return column;
		}
		if (column == null || column.isExpired(null)) {
			column = new Column(x, z);
			this.columns.put(key, column);
		}
		return column;
	}

	private boolean isCandidate(Entity entity) {
		if (entity instanceof PrimedTnt) {
			return true;
		}
		return entity instanceof FallingBlockEntity falling
				&& falling.getBlockState().getBlock() == Blocks.SAND;
	}

	private int countSandBelow(Entity entity) {
		int count = 0;
		BlockPos pos = entity.blockPosition();
		for (int i = 1; i < SAND_SCAN_RANGE; i++) {
			if (isFallingBlock(pos.below(i))) {
				count++;
			}
		}
		return count;
	}

	private boolean isFallingBlock(BlockPos pos) {
		if (this.world == null) {
			return false;
		}
		return this.fallingBlockCache.computeIfAbsent(
				pos.asLong(),
				key -> this.world.getBlockState(pos).getBlock() instanceof FallingBlock);
	}

	private boolean chunkHasDispenser(int chunkX, int chunkZ) {
		if (this.world == null) {
			return false;
		}
		long key = ChunkPos.asLong(chunkX, chunkZ);
		return this.dispenserChunkCache.computeIfAbsent(key, k -> {
			if (!this.world.hasChunk(chunkX, chunkZ)) {
				return false;
			}
			LevelChunk chunk = this.world.getChunk(chunkX, chunkZ);
			if (chunk == null) {
				return false;
			}
			for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
				if (blockEntity instanceof DispenserBlockEntity) {
					return true;
				}
			}
			return false;
		});
	}

	private double entryY(Entity entity) {
		ColumnEntry entry = this.columnEntries.get(entity.getId());
		return entry != null ? entry.entryY : entity.getY();
	}

	private static long pack(int x, int z) {
		return ((long) x << 32) ^ (z & 0xffffffffL);
	}

	public record Hit(int x, int y, int z, Entity entity) {
	}

	private static final class Attachment {
		final int chunkX;
		final int chunkZ;
		final int sandUnder;
		final boolean cannonChunk;

		Attachment(int chunkX, int chunkZ, int sandUnder, boolean cannonChunk) {
			this.chunkX = chunkX;
			this.chunkZ = chunkZ;
			this.sandUnder = sandUnder;
			this.cannonChunk = cannonChunk;
		}
	}

	private static final class ColumnEntry {
		int lastIntX;
		int lastIntZ;
		double entryY;

		ColumnEntry(int lastIntX, int lastIntZ, double entryY) {
			this.lastIntX = lastIntX;
			this.lastIntZ = lastIntZ;
			this.entryY = entryY;
		}
	}

	private static final class TrackedEntity {
		final Entity entity;
		int deadTicks;

		TrackedEntity(Entity entity) {
			this.entity = entity;
		}

		boolean shouldDrop() {
			return !this.entity.isAlive() && ++this.deadTicks > 3;
		}
	}

	private final class Column {
		final int x;
		final int z;
		final List<TrackedEntity> entities = new ArrayList<>();
		boolean done;
		long lastActivity = System.currentTimeMillis();

		Column(int x, int z) {
			this.x = x;
			this.z = z;
		}

		void add(TrackedEntity tracked) {
			if (this.done) {
				return;
			}
			this.entities.add(tracked);
			this.lastActivity = System.currentTimeMillis();
		}

		boolean isExpired(PatchCrumb currentCrumb) {
			if (this.done && currentCrumb != null
					&& currentCrumb.posX == this.x && currentCrumb.posZ == this.z) {
				return false;
			}
			long idle = this.done ? 10_000L : COLUMN_IDLE_MS;
			return System.currentTimeMillis() - this.lastActivity > idle;
		}

		Hit process() {
			if (this.done) {
				return null;
			}
			long lastHash = Long.MIN_VALUE;
			Iterator<TrackedEntity> it = this.entities.iterator();
			while (it.hasNext()) {
				TrackedEntity tracked = it.next();
				Entity entity = tracked.entity;
				BlockPos pos = entity.blockPosition();
				int hash = Objects.hash(pos.getX(), entity.getY(), pos.getZ(), entity.getDeltaMovement().y);
				if (lastHash == hash) {
					continue;
				}
				lastHash = hash;
				if (tracked.shouldDrop()) {
					it.remove();
				}
				Attachment attachment = SandStackTracker.this.attachments.get(entity.getId());
				if (attachment == null) {
					continue;
				}
				boolean stillInCannonChunk = attachment.cannonChunk
						&& attachment.chunkX == pos.getX() >> 4
						&& attachment.chunkZ == pos.getZ() >> 4;
				boolean sandUnchanged = SandStackTracker.this.countSandBelow(entity) <= attachment.sandUnder;
				if (stillInCannonChunk || sandUnchanged) {
					continue;
				}
				int crumbY = (int) Math.round(SandStackTracker.this.entryY(entity));
				this.entities.clear();
				this.done = true;
				this.lastActivity = System.currentTimeMillis();
				return new Hit(this.x, crumbY, this.z, entity);
			}
			return null;
		}
	}
}
