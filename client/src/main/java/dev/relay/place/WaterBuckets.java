package dev.relay.place;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class WaterBuckets {
	private WaterBuckets() {
	}

	static boolean isVanilla(ItemStack stack) {
		return stack != null && !stack.isEmpty() && stack.is(Items.WATER_BUCKET) && stack.get(DataComponents.CUSTOM_NAME) == null;
	}

	static boolean isNamedInfinite(ItemStack stack) {
		if (stack == null || stack.isEmpty() || stack.get(DataComponents.CUSTOM_NAME) == null) {
			return false;
		}

		return BucketNameMatcher.matchesInfiniteWaterGenerator(stack.getHoverName().getString());
	}

	static boolean isUsable(ItemStack stack) {
		return isNamedInfinite(stack) || isVanilla(stack);
	}
}
