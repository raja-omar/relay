package dev.relay.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;

@Mixin(Gui.class)
public interface GuiAccessor {
	@Accessor("overlayMessageString")
	void relay$setOverlayMessageString(Component message);

	@Accessor("overlayMessageTime")
	void relay$setOverlayMessageTime(int ticks);

	@Accessor("animateOverlayMessageColor")
	void relay$setAnimateOverlayMessageColor(boolean animate);
}
