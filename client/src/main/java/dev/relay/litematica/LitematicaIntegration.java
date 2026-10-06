package dev.relay.litematica;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import dev.relay.ModInfo;
import dev.relay.common.PlacementNames;
import dev.relay.common.SchematicLimits;
import dev.relay.schematic.SchematicException;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.data.SchematicHolder;
import fi.dy.masa.litematica.gui.GuiMaterialList;
import fi.dy.masa.litematica.gui.GuiSchematicVerifier;
import fi.dy.masa.litematica.materials.MaterialCache;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.util.EasyPlaceProtocol;
import fi.dy.masa.litematica.util.EntityUtils;
import fi.dy.masa.litematica.util.FileType;
import fi.dy.masa.litematica.util.InventoryUtils;
import fi.dy.masa.litematica.util.PlacementHandler;
import fi.dy.masa.litematica.util.RayTraceUtils;
import fi.dy.masa.litematica.util.WorldUtils;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.gui.interfaces.IMessageConsumer;
import fi.dy.masa.malilib.interfaces.IStringConsumer;

/**
 * The one place in this mod that knows about Litematica.
 *
 * <p>Everything Litematica-specific belongs here, so a Litematica update can only ever break a
 * single file. Sharing a placement reads Litematica's in-memory schematic and writes it back into
 * the recipient's loaded list -- no file is created on either side.
 */
public final class LitematicaIntegration {
	public static final String LITEMATICA_MOD_ID = "litematica";

	private static final IMessageConsumer SILENT = new IMessageConsumer() {
		@Override
		public void addMessage(MessageType type, String messageKey, Object... args) {
		}

		@Override
		public void addMessage(MessageType type, int displayTimeMs, String messageKey, Object... args) {
		}
	};

	private static final IStringConsumer SILENT_STRING = message -> {
	};

	/** A loaded Litematica placement, without exposing the Litematica type to the rest of the mod. */
	public record PlacementView(
			String id,
			String name,
			BlockPos origin,
			boolean enabled,
			boolean selected,
			int rotation,
			int mirror,
			boolean locked) {
	}

	public static boolean isAvailable() {
		return FabricLoader.getInstance().isModLoaded(LITEMATICA_MOD_ID);
	}

	/** The installed Litematica version, or empty when Litematica is not installed. */
	public static Optional<String> version() {
		return FabricLoader.getInstance()
				.getModContainer(LITEMATICA_MOD_ID)
				.map(ModContainer::getMetadata)
				.map(ModMetadata::getVersion)
				.map(Object::toString);
	}

	/**
	 * Where Litematica keeps schematics, or {@code <game dir>/schematics} when Litematica cannot be
	 * asked. The folder does not have to exist yet.
	 */
	public static Path schematicsDirectory() {
		if (isAvailable()) {
			try {
				return DataManager.getSchematicsBaseDirectory();
			} catch (RuntimeException failed) {
				ModInfo.LOG.warn("Could not ask Litematica for its schematics folder, using the default", failed);
			}
		}

		return FabricLoader.getInstance().getGameDir().resolve("schematics");
	}

	/** The name shown on the placement in Litematica, used in chat when it is shared. */
	public static String placementName(SchematicPlacement placement) {
		String name = PlacementNames.sanitise(placement.getName());
		return name.isEmpty() ? "schematic" : name;
	}

	/**
	 * The schematic plus its origin, rotation and mirror, as compressed NBT. This is what goes on
	 * the wire; the recipient loads it without writing a file.
	 */
	public static byte[] serializePlacement(SchematicPlacement placement) throws SchematicException {
		try {
			BlockPos origin = placement.getOrigin();
			CompoundTag envelope = new CompoundTag();
			envelope.putString("Name", placementName(placement));
			envelope.putInt("X", origin.getX());
			envelope.putInt("Y", origin.getY());
			envelope.putInt("Z", origin.getZ());
			envelope.putInt("Rotation", placement.getRotation().ordinal());
			envelope.putInt("Mirror", placement.getMirror().ordinal());
			envelope.put("Schematic", placement.getSchematic().writeToNBT());

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			NbtIo.writeCompressed(envelope, bytes);
			byte[] data = bytes.toByteArray();

			if (data.length == 0) {
				throw new SchematicException("That schematic is empty.");
			}

			if (data.length > SchematicLimits.MAX_BYTES) {
				throw new SchematicException("Schematic is too large to share.");
			}

			return data;
		} catch (IOException failed) {
			throw new SchematicException("Could not read that schematic.", failed);
		}
	}

	/**
	 * Loads a shared placement into Litematica the same way a locally created one is loaded: it
	 * appears under Loaded Schematics and as a visible placement, and is not written to disk.
	 */
	public static void loadSharedPlacement(byte[] data) throws SchematicException {
		if (!SchematicLimits.looksLikeCompressedNbt(data)) {
			throw new SchematicException("That file is not a usable schematic.");
		}

		if (data.length > SchematicLimits.MAX_BYTES) {
			throw new SchematicException("Schematic is too large to share.");
		}

		try {
			CompoundTag envelope = NbtIo.readCompressed(new ByteArrayInputStream(data), NbtAccounter.unlimitedHeap());
			CompoundTag schematicNbt = envelope.getCompoundOrEmpty("Schematic");

			if (schematicNbt.isEmpty()) {
				throw new SchematicException("That file is not a usable schematic.");
			}

			String name = envelope.getStringOr("Name", "schematic");
			BlockPos origin = new BlockPos(
					envelope.getIntOr("X", 0),
					envelope.getIntOr("Y", 0),
					envelope.getIntOr("Z", 0));
			Rotation rotation = rotationAt(envelope.getIntOr("Rotation", 0));
			Mirror mirror = mirrorAt(envelope.getIntOr("Mirror", 0));

			LitematicaSchematic schematic = new LitematicaSchematic(null, schematicNbt, FileType.LITEMATICA_SCHEMATIC);
			SchematicHolder.getInstance().addSchematic(schematic, true);

			SchematicPlacement placement = SchematicPlacement.createFor(schematic, origin, name, true, true);
			placement.setRotation(rotation, SILENT);
			placement.setMirror(mirror, SILENT);
			placement.setShouldBeSaved(false);

			SchematicPlacementManager manager = DataManager.getSchematicPlacementManager();
			manager.addSchematicPlacement(placement, true);
			manager.setSelectedSchematicPlacement(placement);
		} catch (SchematicException failed) {
			throw failed;
		} catch (IOException failed) {
			throw new SchematicException("Could not load that schematic.", failed);
		} catch (RuntimeException failed) {
			ModInfo.LOG.warn("Litematica refused a shared placement", failed);
			throw new SchematicException("Could not load that schematic.", failed);
		}
	}

	/** Every placement Litematica currently has in the world. */
	public static List<PlacementView> placements() {
		if (!isAvailable()) {
			return List.of();
		}

		try {
			SchematicPlacementManager manager = DataManager.getSchematicPlacementManager();
			SchematicPlacement selected = manager.getSelectedSchematicPlacement();
			List<PlacementView> views = new ArrayList<>();

			for (SchematicPlacement placement : manager.getAllSchematicsPlacements()) {
				views.add(new PlacementView(
						placementId(placement),
						placementName(placement),
						placement.getOrigin(),
						placement.isEnabled(),
						placement == selected,
						placement.getRotation().ordinal(),
						placement.getMirror().ordinal(),
						placement.isLocked()));
			}

			return views;
		} catch (RuntimeException failed) {
			ModInfo.LOG.debug("Could not list Litematica placements", failed);
			return List.of();
		}
	}

	/** Compact fingerprint of loaded placements, so the Relay window can refresh when they change. */
	public static String placementsKey() {
		return fingerprint(placements(), true);
	}

	/** Identity only, so nudging a placement does not rebuild the overlay. */
	public static String placementsIdentityKey() {
		return fingerprint(placements(), false);
	}

	static String fingerprint(List<PlacementView> views, boolean includeOrigin) {
		StringBuilder key = new StringBuilder();

		for (PlacementView view : views) {
			key.append(view.id())
					.append(view.enabled() ? '1' : '0')
					.append(view.selected() ? '*' : '-')
					.append(view.locked() ? 'L' : 'u')
					.append('r').append(view.rotation())
					.append('m').append(view.mirror());
			if (includeOrigin) {
				BlockPos origin = view.origin();
				key.append(origin.getX()).append(',')
						.append(origin.getY()).append(',')
						.append(origin.getZ());
			}
			key.append(';');
		}

		return key.toString();
	}

	public static Optional<SchematicPlacement> selectedPlacement() {
		if (!isAvailable()) {
			return Optional.empty();
		}

		try {
			return Optional.ofNullable(DataManager.getSchematicPlacementManager().getSelectedSchematicPlacement());
		} catch (RuntimeException failed) {
			ModInfo.LOG.debug("Could not ask Litematica for the selected placement", failed);
			return Optional.empty();
		}
	}

	public static void selectPlacement(String id) {
		findPlacement(id).ifPresent(placement -> {
			try {
				DataManager.getSchematicPlacementManager().setSelectedSchematicPlacement(placement);
			} catch (RuntimeException failed) {
				ModInfo.LOG.debug("Could not select a Litematica placement", failed);
			}
		});
	}

	public static void toggleSelected() {
		selectedPlacement().ifPresent(placement -> {
			try {
				placement.toggleEnabled();
			} catch (RuntimeException failed) {
				ModInfo.LOG.debug("Could not toggle a Litematica placement", failed);
			}
		});
	}

	public static void unloadSelected() {
		selectedPlacement().ifPresent(placement -> {
			try {
				DataManager.getSchematicPlacementManager().removeSchematicPlacement(placement);
			} catch (RuntimeException failed) {
				ModInfo.LOG.debug("Could not unload a Litematica placement", failed);
			}
		});
	}

	public static void cycleSelectedRotation() throws SchematicException {
		SchematicPlacement placement = requireSelected();
		if (placement.isLocked()) {
			throw new SchematicException("That placement is locked.");
		}

		try {
			Rotation[] values = Rotation.values();
			Rotation next = values[(placement.getRotation().ordinal() + 1) % values.length];
			placement.setRotation(next, SILENT);
		} catch (RuntimeException failed) {
			ModInfo.LOG.debug("Could not rotate a Litematica placement", failed);
			throw new SchematicException("Could not rotate that placement.", failed);
		}
	}

	public static void cycleSelectedMirror() throws SchematicException {
		SchematicPlacement placement = requireSelected();
		if (placement.isLocked()) {
			throw new SchematicException("That placement is locked.");
		}

		try {
			Mirror[] values = Mirror.values();
			Mirror next = values[(placement.getMirror().ordinal() + 1) % values.length];
			placement.setMirror(next, SILENT);
		} catch (RuntimeException failed) {
			ModInfo.LOG.debug("Could not mirror a Litematica placement", failed);
			throw new SchematicException("Could not mirror that placement.", failed);
		}
	}

	public static void openSelectedMaterialList() throws SchematicException {
		SchematicPlacement placement = requireSelected();

		try {
			MaterialListBase materialList = placement.getMaterialList();
			DataManager.setMaterialList(materialList);
			GuiBase.openGui(new GuiMaterialList(materialList));
		} catch (RuntimeException failed) {
			ModInfo.LOG.debug("Could not open the Litematica material list", failed);
			throw new SchematicException("Could not open the material list.", failed);
		}
	}

	public static void openSelectedVerifier() throws SchematicException {
		SchematicPlacement placement = requireSelected();

		try {
			GuiBase.openGui(new GuiSchematicVerifier(placement));
		} catch (RuntimeException failed) {
			ModInfo.LOG.debug("Could not open the Litematica schematic verifier", failed);
			throw new SchematicException("Could not open the schematic verifier.", failed);
		}
	}

	public static void nudgeSelected(Direction direction, int amount) throws SchematicException {
		SchematicPlacement placement = requireSelected();
		movePlacement(placement, placement.getOrigin().relative(direction, amount));
	}

	public static void nudgeSelected(int dx, int dy, int dz) throws SchematicException {
		if (dx == 0 && dy == 0 && dz == 0) {
			return;
		}

		SchematicPlacement placement = requireSelected();
		movePlacement(placement, placement.getOrigin().offset(dx, dy, dz));
	}

	public static void moveSelectedToPlayer() throws SchematicException {
		Minecraft client = Minecraft.getInstance();

		if (client.player == null) {
			throw new SchematicException("Join a world before moving a placement.");
		}

		movePlacement(requireSelected(), client.player.blockPosition());
	}

	public static void moveTo(String id, int x, int y, int z) throws SchematicException {
		SchematicPlacement placement = findPlacement(id)
				.orElseThrow(() -> new SchematicException("Select a placement first."));
		movePlacement(placement, new BlockPos(x, y, z));
	}

	/**
	 * Loads a schematic from disk the same way Litematica's load screen does: into Loaded
	 * Schematics, and as a placement at the player's feet.
	 */
	public static void loadFileAsPlacement(Path file) throws SchematicException {
		if (!isAvailable()) {
			throw new SchematicException("Litematica is not installed, so schematics cannot be loaded.");
		}

		Minecraft client = Minecraft.getInstance();

		if (client.player == null) {
			throw new SchematicException("Join a world before loading a schematic.");
		}

		if (!Files.isRegularFile(file) || !Files.isReadable(file)) {
			throw new SchematicException("Schematic could not be found.");
		}

		try {
			LitematicaSchematic schematic = readSchematicFile(file);

			if (schematic == null) {
				throw new SchematicException("Could not load that schematic.");
			}

			SchematicHolder.getInstance().addSchematic(schematic, true);
			Path fileName = file.getFileName();
			String name = placementNameFrom(schematic, fileName == null ? "" : fileName.toString());
			BlockPos origin = client.player.blockPosition();
			SchematicPlacement placement = SchematicPlacement.createFor(schematic, origin, name, true, true);
			SchematicPlacementManager manager = DataManager.getSchematicPlacementManager();
			manager.addSchematicPlacement(placement, true);
			manager.setSelectedSchematicPlacement(placement);
		} catch (SchematicException failed) {
			throw failed;
		} catch (RuntimeException failed) {
			ModInfo.LOG.warn("Litematica refused to load {}", file, failed);
			throw new SchematicException("Could not load that schematic.", failed);
		}
	}

	public static boolean easyPlaceEnabled() {
		if (!isAvailable()) {
			return false;
		}

		try {
			return Configs.Generic.EASY_PLACE_MODE.getBooleanValue();
		} catch (RuntimeException failed) {
			ModInfo.LOG.debug("Could not read Litematica Easy Place", failed);
			return false;
		}
	}

	public static void setEasyPlaceEnabled(boolean on) {
		if (!isAvailable()) {
			return;
		}

		try {
			Configs.Generic.EASY_PLACE_MODE.setBooleanValue(on);
			if (on) {
				Configs.Generic.EASY_PLACE_HOLD_ENABLED.setBooleanValue(true);
			}
			Configs.saveToFile();
		} catch (RuntimeException failed) {
			ModInfo.LOG.debug("Could not change Litematica Easy Place", failed);
		}
	}

	public static boolean easyPlaceFirst() {
		if (!isAvailable()) {
			return false;
		}

		try {
			return Configs.Generic.EASY_PLACE_FIRST.getBooleanValue();
		} catch (RuntimeException failed) {
			ModInfo.LOG.debug("Could not read Litematica Easy Place First", failed);
			return false;
		}
	}

	public static void setEasyPlaceFirst(boolean on) {
		if (!isAvailable()) {
			return;
		}

		try {
			Configs.Generic.EASY_PLACE_FIRST.setBooleanValue(on);
			Configs.saveToFile();
		} catch (RuntimeException failed) {
			ModInfo.LOG.debug("Could not change Litematica Easy Place First", failed);
		}
	}

	private static SchematicPlacement requireSelected() throws SchematicException {
		return selectedPlacement().orElseThrow(() -> new SchematicException("Select a placement first."));
	}

	private static void movePlacement(SchematicPlacement placement, BlockPos origin) throws SchematicException {
		if (placement.isLocked()) {
			throw new SchematicException("That placement is locked.");
		}

		try {
			placement.setOrigin(origin, SILENT_STRING);
		} catch (RuntimeException failed) {
			ModInfo.LOG.debug("Could not move a Litematica placement", failed);
			throw new SchematicException("Could not move that placement.", failed);
		}
	}

	private static Optional<SchematicPlacement> findPlacement(String id) {
		if (!isAvailable() || id == null || id.isEmpty()) {
			return Optional.empty();
		}

		try {
			for (SchematicPlacement placement : DataManager.getSchematicPlacementManager().getAllSchematicsPlacements()) {
				if (placementId(placement).equals(id)) {
					return Optional.of(placement);
				}
			}
		} catch (RuntimeException failed) {
			ModInfo.LOG.debug("Could not find a Litematica placement", failed);
		}

		return Optional.empty();
	}

	private static String placementId(SchematicPlacement placement) {
		return Integer.toHexString(System.identityHashCode(placement));
	}

	private static LitematicaSchematic readSchematicFile(Path file) {
		Path dir = file.getParent();
		Path fileName = file.getFileName();

		if (dir == null || fileName == null) {
			return null;
		}

		String name = fileName.toString();

		return switch (FileType.fromFile(file)) {
			case LITEMATICA_SCHEMATIC -> LitematicaSchematic.createFromFile(dir, name);
			case SCHEMATICA_SCHEMATIC -> WorldUtils.convertSchematicaSchematicToLitematicaSchematic(
					dir, name, false, SILENT_STRING);
			case VANILLA_STRUCTURE -> WorldUtils.convertStructureToLitematicaSchematic(dir, name);
			case SPONGE_SCHEMATIC -> WorldUtils.convertSpongeSchematicToLitematicaSchematic(dir, name);
			default -> null;
		};
	}

	private static String placementNameFrom(LitematicaSchematic schematic, String fileName) {
		String name = PlacementNames.sanitise(schematic.getMetadata().getName());

		if (!name.isEmpty() && !name.equals("?")) {
			return name;
		}

		int dot = fileName.lastIndexOf('.');
		String stem = dot > 0 ? fileName.substring(0, dot) : fileName;
		name = PlacementNames.sanitise(stem);
		return name.isEmpty() ? "schematic" : name;
	}

	private static Rotation rotationAt(int ordinal) {
		Rotation[] values = Rotation.values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : Rotation.NONE;
	}

	private static Mirror mirrorAt(int ordinal) {
		Mirror[] values = Mirror.values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : Mirror.NONE;
	}

	/** True when this position sits inside a loaded placement, with a one-block fringe. */
	public static boolean isNearSchematic(BlockPos pos) {
		return isWithinSchematic(pos, 1);
	}

	/** True when this position is inside a loaded placement, with no fringe. */
	public static boolean isInsideSchematic(BlockPos pos) {
		return isWithinSchematic(pos, 0);
	}

	public static Optional<WorldSchematic> schematicWorld() {
		if (!isAvailable()) {
			return Optional.empty();
		}

		try {
			return Optional.ofNullable(SchematicWorldHandler.getSchematicWorld());
		} catch (RuntimeException failed) {
			ModInfo.LOG.debug("Could not ask Litematica for the schematic world", failed);
			return Optional.empty();
		}
	}

	public static Optional<BlockState> schematicState(BlockPos pos) {
		if (!isAvailable() || pos == null) {
			return Optional.empty();
		}

		try {
			WorldSchematic world = SchematicWorldHandler.getSchematicWorld();
			return world == null ? Optional.empty() : Optional.of(world.getBlockState(pos));
		} catch (RuntimeException failed) {
			ModInfo.LOG.debug("Could not read the schematic at {}", pos, failed);
			return Optional.empty();
		}
	}

	/** The item Litematica wants in hand to build this state, or empty when none is needed. */
	public static ItemStack requiredBuildItem(BlockState state) {
		if (!isAvailable() || state == null || state.isAir()) {
			return ItemStack.EMPTY;
		}

		try {
			ItemStack stack = MaterialCache.getInstance().getRequiredBuildItemForState(state);
			return stack == null ? ItemStack.EMPTY : stack;
		} catch (RuntimeException failed) {
			ModInfo.LOG.debug("Could not ask Litematica for the build item", failed);
			return ItemStack.EMPTY;
		}
	}

	/**
	 * Lets Litematica rewrite facing and similar properties the same way Easy Place does, so
	 * cant-miss checks the state that will actually appear.
	 */
	public static BlockState applyPlacementProtocol(BlockState predicted, BlockPlaceContext context, InteractionHand hand) {
		if (!isAvailable() || predicted == null || context == null) {
			return predicted;
		}

		try {
			return PlacementHandler.applyPlacementProtocolToPlacementState(
					predicted, PlacementHandler.UseContext.from(context, hand));
		} catch (RuntimeException failed) {
			ModInfo.LOG.debug("Could not apply Litematica's placement protocol", failed);
			return predicted;
		}
	}

	/** The schematic block Easy Place would click now, if any. */
	public static Optional<BlockHitResult> easyPlaceSchematicHit(Minecraft client) {
		if (!isAvailable() || client == null || client.player == null || client.level == null) {
			return Optional.empty();
		}

		try {
			double reach = Configs.Generic.EASY_PLACE_VANILLA_REACH.getBooleanValue() ? 4.5 : 6.0;
			RayTraceUtils.RayTraceWrapper wrapper = Configs.Generic.EASY_PLACE_FIRST.getBooleanValue()
					? RayTraceUtils.getGenericTrace(
							client.level,
							client.player,
							reach,
							true,
							Configs.InfoOverlays.INFO_OVERLAYS_TARGET_FLUIDS.getBooleanValue(),
							false)
					: RayTraceUtils.getFurthestSchematicWorldTraceBeforeVanilla(client.level, client.player, reach);
			if (wrapper == null || wrapper.getHitType() != RayTraceUtils.RayTraceWrapper.HitType.SCHEMATIC_BLOCK) {
				return Optional.empty();
			}

			return Optional.ofNullable(wrapper.getBlockHitResult());
		} catch (RuntimeException failed) {
			ModInfo.LOG.debug("Could not ask Litematica for the Easy Place target", failed);
			return Optional.empty();
		}
	}

	/**
	 * True when Litematica can actually rewrite facing in the use packet. On a normal multiplayer
	 * server this is false, so Easy Place should pick a vanilla face instead of encoding V2/V3.
	 */
	public static boolean accuratePlacementProtocolIsLive() {
		if (!isAvailable()) {
			return false;
		}

		try {
			EasyPlaceProtocol protocol = PlacementHandler.getEffectiveProtocolVersion();
			Minecraft client = Minecraft.getInstance();
			boolean integrated = client != null
					&& client.isLocalServer()
					&& Configs.Generic.EASY_PLACE_SP_HANDLING.getBooleanValue()
					&& Configs.Generic.ITEM_USE_PACKET_CHECK_BYPASS.getBooleanValue();
			return switch (protocol) {
				case V3 -> integrated;
				case V2 -> integrated || DataManager.isCarpetServer();
				default -> false;
			};
		} catch (RuntimeException failed) {
			ModInfo.LOG.debug("Could not ask Litematica for the Easy Place protocol", failed);
			return false;
		}
	}

	/** Temporarily mark the local player as sneaking the same way Litematica's Easy Place does. */
	public static void setFakedSneaking(boolean sneaking) {
		if (!isAvailable()) {
			return;
		}

		try {
			EntityUtils.setFakedSneakingState(sneaking);
		} catch (RuntimeException failed) {
			ModInfo.LOG.debug("Could not fake a sneak for schematic placement", failed);
		}
	}

	/**
	 * Drop a pos from Litematica's Easy Place cache so a failed click can retry on the next tick
	 * instead of going silent for two seconds.
	 */
	public static void forgetEasyPlaceTarget(BlockPos pos) {
		if (!isAvailable() || pos == null) {
			return;
		}

		forgetCachedTarget("fi.dy.masa.litematica.util.EasyPlaceUtils", pos);
		forgetCachedTarget("fi.dy.masa.litematica.util.WorldUtils", pos);
	}

	private static void forgetCachedTarget(String owner, BlockPos target) {
		try {
			Class<?> type = Class.forName(owner);
			Field field = type.getDeclaredField("EASY_PLACE_POSITIONS");
			field.setAccessible(true);
			if (!(field.get(null) instanceof List<?> positions)) {
				return;
			}

			Method positionGetter = null;
			Iterator<?> iterator = positions.iterator();
			while (iterator.hasNext()) {
				Object entry = iterator.next();
				if (entry == null) {
					continue;
				}

				if (positionGetter == null || positionGetter.getDeclaringClass() != entry.getClass()) {
					positionGetter = entry.getClass().getMethod("getPos");
					positionGetter.setAccessible(true);
				}

				if (target.equals(positionGetter.invoke(entry))) {
					iterator.remove();
				}
			}
		} catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
		}
	}

	/** Moves the required stack into a hotbar slot the same way Litematica's pick-block does. */
	public static void pickBuildItem(ItemStack stack, BlockPos pos, Level world) {
		if (!isAvailable() || stack == null || stack.isEmpty()) {
			return;
		}

		try {
			InventoryUtils.schematicWorldPickBlock(stack, pos, world, Minecraft.getInstance());
		} catch (RuntimeException failed) {
			ModInfo.LOG.debug("Could not pick the required build item", failed);
		}
	}

	private static boolean isWithinSchematic(BlockPos pos, int range) {
		if (!isAvailable() || pos == null) {
			return false;
		}

		try {
			return WorldUtils.isPositionWithinRangeOfSchematicRegions(pos, range);
		} catch (RuntimeException failed) {
			ModInfo.LOG.debug("Could not ask Litematica whether {} is in a placement", pos, failed);
			return false;
		}
	}

	private LitematicaIntegration() {
	}
}
