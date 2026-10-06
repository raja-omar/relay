package dev.relay.place;

import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.CartographyTableBlock;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.GrindstoneBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.LoomBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.SmithingTableBlock;
import net.minecraft.world.level.block.StonecutterBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Blocks whose default right-click is a GUI or toggle. Easy Place and Cant Miss sneak for these
 * so the click places instead of opening the support.
 */
final class PlacementSupports {
	private PlacementSupports() {
	}

	static boolean needsSneakToPlaceOn(BlockState state) {
		if (state == null || state.isAir() || state.canBeReplaced()) {
			return false;
		}

		Block block = state.getBlock();
		return block instanceof BaseEntityBlock
				|| block instanceof NoteBlock
				|| block instanceof LeverBlock
				|| block instanceof ButtonBlock
				|| block instanceof DoorBlock
				|| block instanceof TrapDoorBlock
				|| block instanceof FenceGateBlock
				|| block instanceof RepeaterBlock
				|| block instanceof ComparatorBlock
				|| block instanceof BedBlock
				|| block instanceof CraftingTableBlock
				|| block instanceof StonecutterBlock
				|| block instanceof LoomBlock
				|| block instanceof CartographyTableBlock
				|| block instanceof SmithingTableBlock
				|| block instanceof GrindstoneBlock
				|| block instanceof AnvilBlock
				|| block instanceof BellBlock
				|| block instanceof ComposterBlock
				|| block instanceof CakeBlock
				|| block instanceof DaylightDetectorBlock;
	}
}
