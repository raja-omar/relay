package dev.relay.place;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

import dev.relay.RelayClient;
import dev.relay.chat.ChatMessages;
import dev.relay.litematica.LitematicaIntegration;
import dev.relay.mixin.BlockItemAccessor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Stops a right-click from placing the wrong schematic block, and buys the right one when the
 * inventory is short.
 *
 * <p>Clicks well outside a loaded placement still go through vanilla. Clicks that would put a
 * block where the schematic wants air, or facing the wrong way, are cancelled. A valid schematic
 * click stays on the vanilla {@code useItemOn} path so a remote server can confirm the predicted
 * block; sneak is applied around that click when the support would otherwise open a GUI.
 */
public final class CantMiss {
	private static final long HINT_REPEAT_MS = 450L;
	private static final Property<?>[] STRICT_PROPERTIES = {
			BlockStateProperties.FACING,
			BlockStateProperties.HORIZONTAL_FACING,
			BlockStateProperties.FACING_HOPPER,
			BlockStateProperties.VERTICAL_DIRECTION,
			BlockStateProperties.AXIS,
			BlockStateProperties.HORIZONTAL_AXIS,
			BlockStateProperties.ATTACH_FACE,
			BlockStateProperties.HALF,
			BlockStateProperties.HANGING,
			BlockStateProperties.ROTATION_16,
			BlockStateProperties.DOUBLE_BLOCK_HALF,
			BlockStateProperties.BED_PART,
			BlockStateProperties.DOOR_HINGE,
			BlockStateProperties.WATERLOGGED
	};
	private static final IntegerProperty[] STACK_COUNT_PROPERTIES = {
			BlockStateProperties.PICKLES,
			BlockStateProperties.CANDLES,
			BlockStateProperties.LAYERS,
			BlockStateProperties.EGGS
	};

	private static final ThreadLocal<Boolean> REPLAYING = ThreadLocal.withInitial(() -> Boolean.FALSE);
	private static final ThreadLocal<PendingPlace> PENDING_PLACE = new ThreadLocal<>();
	private static long nextHintAt;
	private static String lastHint = "";

	private CantMiss() {
	}

	public static void tick(Minecraft client) {
		PlacementUse.tick(client);
		ShopPurchases.tick(client);
		HotbarRefill.tick(client);
		RedstoneGateTuner.tick(client);
		WaterPlacement.tick(client);
		EmptyBucketDrop.tick(client);
	}

	public static boolean isPassthrough() {
		return Boolean.TRUE.equals(REPLAYING.get()) || WaterPlacement.isDispatching();
	}

	static <T> T passthrough(Supplier<T> action) {
		REPLAYING.set(Boolean.TRUE);
		try {
			return action.get();
		} finally {
			REPLAYING.remove();
		}
	}

	public static boolean interceptCrouchWaterUse(Minecraft client) {
		return WaterPlacement.interceptCrouchWaterUse(client);
	}

	public static void armEmptyBucketDrop(Minecraft client) {
		EmptyBucketDrop.armCurrentUse(client);
	}

	public static InteractionResult interceptItemUse(LocalPlayer player, InteractionHand hand) {
		return WaterPlacement.interceptItemUse(player, hand);
	}

	/**
	 * Easy Place fails before a vanilla right-click when the item is not in the inventory.
	 * Buy it there so the next click can place, and swallow the "prevented" warning.
	 */
	public static boolean buyMissingEasyPlaceMaterial(Minecraft client) {
		if (client == null
				|| client.player == null
				|| client.level == null
				|| client.player.isCreative()
				|| !RelayClient.get().config().cantMiss()
				|| !RelayClient.get().config().autoPurchase()
				|| !LitematicaIntegration.isAvailable()) {
			return false;
		}

		BlockHitResult hit = LitematicaIntegration.easyPlaceSchematicHit(client).orElse(null);
		if (hit == null) {
			return false;
		}

		BlockPos pos = hit.getBlockPos();
		BlockState wanted = LitematicaIntegration.schematicState(pos).orElse(null);
		if (wanted == null || wanted.isAir()) {
			return false;
		}

		if (client.level.getBlockState(pos).is(wanted.getBlock())) {
			return false;
		}

		ItemStack required = LitematicaIntegration.requiredBuildItem(wanted);
		if (required.isEmpty() || ShopPurchases.findMatchingSlot(client.player, required) >= 0) {
			return false;
		}

		return ShopPurchases.tryAutoPurchase(client.player, wanted, required);
	}

	/** Middle-click / Easy Place pick: buy when Litematica could not put the item in hand. */
	public static void buyMissingPickedItem(ItemStack required, BlockPos pos) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null
				|| required == null
				|| required.isEmpty()
				|| client.player.isCreative()
				|| !RelayClient.get().config().cantMiss()
				|| !RelayClient.get().config().autoPurchase()) {
			return;
		}

		if (ShopPurchases.findMatchingSlot(client.player, required) >= 0) {
			return;
		}

		BlockState wanted = LitematicaIntegration.schematicState(pos).orElse(null);
		ShopPurchases.tryAutoPurchase(client.player, wanted, required);
	}

	/**
	 * {@code null} means the original click should proceed. Any other value replaces it.
	 */
	public static InteractionResult intercept(
			MultiPlayerGameMode manager, LocalPlayer player, InteractionHand hand, BlockHitResult hit) {
		if (isPassthrough()) {
			return null;
		}

		Minecraft client = Minecraft.getInstance();
		if (client.screen != null
				|| player == null
				|| player.isSpectator()
				|| !RelayClient.get().config().cantMiss()
				|| !LitematicaIntegration.isAvailable()) {
			return null;
		}

		ClientLevel level = client.level;
		if (level == null) {
			return null;
		}

		InteractionResult redstone = RedstoneGateTuner.interceptDirect(manager, player, level, hand, hit);
		if (redstone != null) {
			return redstone;
		}

		Decision decision = validate(
				player, level, hand, hit, LitematicaIntegration.accuratePlacementProtocolIsLive(), false);
		if (decision.kind() == Kind.VANILLA || decision.kind() == Kind.ALLOWED) {
			if (decision.kind() == Kind.ALLOWED) {
				PENDING_PLACE.set(new PendingPlace(decision.wanted(), decision.required()));
				if (PlacementUse.needsSneak(player, level, hit)) {
					PlacementUse.armSneakForCurrentUse(player);
				}
			}

			return null;
		}

		if (decision.kind() == Kind.OUTSIDE) {
			hint(player, "No schematic block belongs here");
			return InteractionResult.FAIL;
		}

		hint(player, decision.reason());
		resolveMissingMaterial(player, decision);
		return InteractionResult.FAIL;
	}

	/**
	 * After the vanilla {@code useItemOn} body: restock if the place landed, and drop any sneak we
	 * armed. Must not send restore packets until the next tick — on a remote server those race the
	 * use packet and the predicted block pops.
	 */
	public static void afterUseItemOn(LocalPlayer player, InteractionResult result) {
		PendingPlace pending = PENDING_PLACE.get();
		PENDING_PLACE.remove();
		if (pending != null && result != null && result.consumesAction()) {
			ShopPurchases.schedulePrePurchase(player, Minecraft.getInstance().level, pending.wanted(), pending.required());
		}

		PlacementUse.releaseSneakForCurrentUse(player);
	}

	static Decision evaluateEasyPlace(
			LocalPlayer player, ClientLevel level, InteractionHand hand, BlockHitResult hit, boolean applyProtocol) {
		return validate(player, level, hand, hit, applyProtocol, true);
	}

	static void showHint(LocalPlayer player, String text) {
		hint(player, text);
	}

	private static Decision validate(
			LocalPlayer player,
			ClientLevel level,
			InteractionHand hand,
			BlockHitResult hit,
			boolean applyProtocol,
			boolean requireExactHitPos) {
		ItemStack held = player.getItemInHand(hand);
		BlockPlaceContext base = new BlockPlaceContext(player, hand, held, hit);
		BlockPlaceContext resolved = base;
		BlockState predicted = null;

		if (held.getItem() instanceof BlockItem blockItem) {
			resolved = blockItem.updatePlacementContext(base);
			if (resolved == null) {
				return invalidAt(base.getClickedPos(), "Placement is not valid here");
			}

			if (!LitematicaIntegration.isInsideSchematic(resolved.getClickedPos())
					&& !LitematicaIntegration.isInsideSchematic(hit.getBlockPos())) {
				return Decision.vanilla(resolved.getClickedPos());
			}

			predicted = ((BlockItemAccessor) blockItem).relay$getPlacementState(resolved);
			if (predicted != null && applyProtocol) {
				predicted = LitematicaIntegration.applyPlacementProtocol(predicted, resolved, hand);
			}
		} else if (!LitematicaIntegration.isInsideSchematic(base.getClickedPos())
				&& !LitematicaIntegration.isInsideSchematic(hit.getBlockPos())) {
			return Decision.vanilla(base.getClickedPos());
		}

		BlockPos target = resolved.getClickedPos();
		if (requireExactHitPos && !target.equals(hit.getBlockPos())) {
			return Decision.blocked(target, null, ItemStack.EMPTY, "Placement would target another block");
		}

		if (!LitematicaIntegration.isNearSchematic(target)) {
			return Decision.outside(target);
		}

		BlockState wanted = LitematicaIntegration.schematicState(target).orElse(null);
		if (wanted == null) {
			return Decision.blocked(target, null, ItemStack.EMPTY, "Schematic target could not be read");
		}

		if (wanted.isAir()) {
			return Decision.blocked(target, wanted, ItemStack.EMPTY, "No block belongs here");
		}

		if (wanted.is(Blocks.WATER)) {
			return Decision.blocked(target, wanted, ItemStack.EMPTY, "Water sources are not placed automatically");
		}

		ItemStack required = LitematicaIntegration.requiredBuildItem(wanted);
		if (!(held.getItem() instanceof BlockItem)) {
			return Decision.blocked(target, wanted, required, "Need " + wanted.getBlock().getName().getString());
		}

		if (predicted == null) {
			return Decision.blocked(target, wanted, required, "Placement is not valid here");
		}

		if (!predicted.is(wanted.getBlock())) {
			return Decision.blocked(target, wanted, required, "Need " + wanted.getBlock().getName().getString());
		}

		String progressFailure = validateProgress(predicted, wanted, level.getBlockState(target), target);
		if (progressFailure != null) {
			return Decision.blocked(target, wanted, required, progressFailure);
		}

		boolean anyMismatch = false;
		boolean directionOnly = true;
		boolean facingOnly = true;
		String directionReason = null;
		String otherReason = null;
		for (Property<?> property : STRICT_PROPERTIES) {
			if (!sameProperty(predicted, wanted, property)) {
				anyMismatch = true;
				boolean directionProperty = property == BlockStateProperties.FACING
						|| property == BlockStateProperties.HORIZONTAL_FACING
						|| property == BlockStateProperties.FACING_HOPPER
						|| property == BlockStateProperties.VERTICAL_DIRECTION;
				if (directionProperty) {
					if (directionReason == null) {
						directionReason = propertyHint(wanted, property);
					}
				} else {
					directionOnly = false;
					if (otherReason == null) {
						otherReason = propertyHint(wanted, property);
					}
				}

				if (property != BlockStateProperties.FACING) {
					facingOnly = false;
				}
			}
		}

		if (anyMismatch) {
			return Decision.blocked(
					target,
					wanted,
					required,
					otherReason != null ? otherReason : directionReason,
					directionOnly,
					facingOnly);
		}

		return Decision.allowed(target, wanted, required);
	}

	private static Decision invalidAt(BlockPos target, String reason) {
		if (!LitematicaIntegration.isNearSchematic(target)) {
			return Decision.outside(target);
		}

		BlockState wanted = LitematicaIntegration.schematicState(target).orElse(null);
		ItemStack required = LitematicaIntegration.requiredBuildItem(wanted);
		return Decision.blocked(target, wanted, required, reason);
	}

	private static String validateProgress(BlockState predicted, BlockState wanted, BlockState actual, BlockPos target) {
		if (wanted.hasProperty(BlockStateProperties.SLAB_TYPE)) {
			if (!predicted.hasProperty(BlockStateProperties.SLAB_TYPE)) {
				return "Wrong slab placement";
			}

			SlabType expected = wanted.getValue(BlockStateProperties.SLAB_TYPE);
			SlabType placed = predicted.getValue(BlockStateProperties.SLAB_TYPE);
			if (expected != SlabType.DOUBLE && placed != expected) {
				return "Need " + expected.getSerializedName() + " slab";
			}

			if (expected == SlabType.DOUBLE
					&& actual.is(wanted.getBlock())
					&& actual.hasProperty(BlockStateProperties.SLAB_TYPE)
					&& actual.getValue(BlockStateProperties.SLAB_TYPE) == placed
					&& placed != SlabType.DOUBLE) {
				return "Click the existing slab to make it double";
			}
		}

		for (IntegerProperty property : STACK_COUNT_PROPERTIES) {
			if (wanted.hasProperty(property)) {
				if (!predicted.hasProperty(property)) {
					return "Wrong stacked-block placement";
				}

				int current = actual.is(wanted.getBlock()) && actual.hasProperty(property)
						? actual.getValue(property)
						: 0;
				int next = predicted.getValue(property);
				int expected = wanted.getValue(property);
				if (next <= current || next > expected) {
					return "Schematic needs " + expected + " here";
				}
			}
		}

		if (wanted.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
			if (!predicted.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
					|| predicted.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) != DoubleBlockHalf.LOWER
					|| wanted.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) != DoubleBlockHalf.LOWER) {
				return "Aim at the lower half";
			}

			BlockState upper = LitematicaIntegration.schematicState(target.above()).orElse(null);
			if (upper == null
					|| !upper.is(wanted.getBlock())
					|| !upper.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
					|| upper.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) != DoubleBlockHalf.UPPER
					|| !sameProperty(upper, wanted, BlockStateProperties.HORIZONTAL_FACING)
					|| !sameProperty(upper, wanted, BlockStateProperties.DOOR_HINGE)) {
				return "Schematic upper half does not match";
			}
		}

		if (wanted.hasProperty(BlockStateProperties.BED_PART)) {
			if (!predicted.hasProperty(BlockStateProperties.BED_PART)
					|| predicted.getValue(BlockStateProperties.BED_PART) != BedPart.FOOT
					|| wanted.getValue(BlockStateProperties.BED_PART) != BedPart.FOOT) {
				return "Aim at the bed foot";
			}

			var facing = predicted.getValue(BlockStateProperties.HORIZONTAL_FACING);
			BlockState head = LitematicaIntegration.schematicState(target.relative(facing)).orElse(null);
			if (head == null
					|| !head.is(wanted.getBlock())
					|| !head.hasProperty(BlockStateProperties.BED_PART)
					|| head.getValue(BlockStateProperties.BED_PART) != BedPart.HEAD
					|| !sameProperty(head, wanted, BlockStateProperties.HORIZONTAL_FACING)) {
				return "Bed head does not match the schematic";
			}
		}

		if (wanted.hasProperty(BlockStateProperties.CHEST_TYPE)) {
			return validateChest(predicted, wanted, actual, target);
		}

		return null;
	}

	private static String validateChest(BlockState predicted, BlockState wanted, BlockState actual, BlockPos target) {
		if (!predicted.hasProperty(BlockStateProperties.CHEST_TYPE)) {
			return "Wrong chest placement";
		}

		ChestType expected = wanted.getValue(BlockStateProperties.CHEST_TYPE);
		ChestType placed = predicted.getValue(BlockStateProperties.CHEST_TYPE);
		if (expected == ChestType.SINGLE) {
			return placed == ChestType.SINGLE ? null : "This chest must stay single";
		}

		if (!(wanted.getBlock() instanceof ChestBlock)) {
			return "Wrong double-chest side";
		}

		BlockPos partnerPos = ChestBlock.getConnectedBlockPos(target, wanted);
		BlockState partner = LitematicaIntegration.schematicState(partnerPos).orElse(null);
		if (partner == null
				|| !partner.is(wanted.getBlock())
				|| !partner.hasProperty(BlockStateProperties.CHEST_TYPE)
				|| partner.getValue(BlockStateProperties.CHEST_TYPE) != expected.getOpposite()
				|| !sameProperty(partner, wanted, BlockStateProperties.HORIZONTAL_FACING)) {
			return "Double-chest partner does not match";
		}

		if (placed == expected) {
			return null;
		}

		if (placed != ChestType.SINGLE) {
			return "Wrong double-chest side";
		}

		ClientLevel level = Minecraft.getInstance().level;
		BlockState actualPartner = level == null ? actual : level.getBlockState(partnerPos);
		return actualPartner.is(wanted.getBlock()) ? "This click would connect the chest incorrectly" : null;
	}

	private static boolean sameProperty(BlockState predicted, BlockState wanted, Property<?> property) {
		if (!wanted.hasProperty(property)) {
			return true;
		}

		return predicted.hasProperty(property)
				&& Objects.equals(predicted.getValue(property), wanted.getValue(property));
	}

	private static String propertyHint(BlockState wanted, Property<?> property) {
		Comparable<?> expected = wanted.hasProperty(property) ? wanted.getValue(property) : null;
		String value = expected == null ? "the schematic" : expected.toString().toLowerCase(Locale.ROOT);

		if (property == BlockStateProperties.FACING
				|| property == BlockStateProperties.HORIZONTAL_FACING
				|| property == BlockStateProperties.FACING_HOPPER
				|| property == BlockStateProperties.VERTICAL_DIRECTION) {
			return "Required orientation: " + value;
		}
		if (property == BlockStateProperties.AXIS || property == BlockStateProperties.HORIZONTAL_AXIS) {
			return "Required axis: " + value;
		}
		if (property == BlockStateProperties.HALF) {
			return "Click the " + value + " half";
		}
		if (property == BlockStateProperties.ATTACH_FACE) {
			return "Attach to the " + value;
		}
		if (property == BlockStateProperties.DOOR_HINGE) {
			return "Need " + value + " door hinge";
		}
		if (property == BlockStateProperties.HANGING) {
			return Boolean.TRUE.equals(expected) ? "Attach this block to the ceiling" : "Attach this block from below";
		}
		if (property == BlockStateProperties.ROTATION_16) {
			return "Rotate to match the schematic";
		}
		if (property == BlockStateProperties.DOUBLE_BLOCK_HALF) {
			return "Aim at the lower half";
		}
		if (property == BlockStateProperties.BED_PART) {
			return "Aim at the bed foot";
		}
		if (property == BlockStateProperties.WATERLOGGED) {
			return Boolean.TRUE.equals(expected)
					? "Place the schematic water first while crouching"
					: "This block must remain dry";
		}

		return "Wrong " + property.getName() + ": needs " + value;
	}

	private static void resolveMissingMaterial(LocalPlayer player, Decision decision) {
		ItemStack required = decision.required();
		if (required == null || required.isEmpty() || decision.wanted() == null) {
			return;
		}

		if (player.getMainHandItem().is(required.getItem()) || player.getOffhandItem().is(required.getItem())) {
			return;
		}

		int slot = ShopPurchases.findMatchingSlot(player, required);
		if (slot >= 0) {
			if (slot <= 8) {
				player.getInventory().setSelectedSlot(slot);
				return;
			}

			LitematicaIntegration.pickBuildItem(required, decision.target(), player.level());
			return;
		}

		ShopPurchases.tryAutoPurchase(player, decision.wanted(), required);
	}

	private static void hint(LocalPlayer player, String text) {
		if (text == null || text.isBlank()) {
			return;
		}

		long now = System.currentTimeMillis();
		if (text.equals(lastHint) && now < nextHintAt) {
			return;
		}

		lastHint = text;
		nextHintAt = now + HINT_REPEAT_MS;
		ChatMessages.hint(text);
	}

	enum Kind {
		ALLOWED,
		BLOCKED,
		OUTSIDE,
		VANILLA
	}

	record Decision(
			Kind kind,
			BlockPos target,
			BlockState wanted,
			ItemStack required,
			String reason,
			boolean directionOnly,
			boolean facingOnly) {
		static Decision allowed(BlockPos target, BlockState wanted, ItemStack required) {
			return new Decision(Kind.ALLOWED, target, wanted, required, "", false, false);
		}

		static Decision blocked(BlockPos target, BlockState wanted, ItemStack required, String reason) {
			return blocked(target, wanted, required, reason, false, false);
		}

		static Decision blocked(
				BlockPos target,
				BlockState wanted,
				ItemStack required,
				String reason,
				boolean directionOnly,
				boolean facingOnly) {
			return new Decision(
					Kind.BLOCKED,
					target,
					wanted,
					required == null ? ItemStack.EMPTY : required,
					reason,
					directionOnly,
					facingOnly);
		}

		static Decision outside(BlockPos target) {
			return new Decision(Kind.OUTSIDE, target, null, ItemStack.EMPTY, "", false, false);
		}

		static Decision vanilla(BlockPos target) {
			return new Decision(Kind.VANILLA, target, null, ItemStack.EMPTY, "", false, false);
		}
	}

	private record PendingPlace(BlockState wanted, ItemStack required) {
	}
}
