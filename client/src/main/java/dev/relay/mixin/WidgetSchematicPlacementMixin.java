package dev.relay.mixin;

import dev.relay.RelayClient;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fi.dy.masa.litematica.gui.widgets.WidgetSchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;

/**
 * Adds a Share button on each row of Litematica's Schematic Placements list, to the left of
 * Configure. The button shares that placement as it currently sits in the world.
 */
@Mixin(value = WidgetSchematicPlacement.class, remap = false)
public abstract class WidgetSchematicPlacementMixin extends WidgetListEntryBase<SchematicPlacement> {
	@Shadow
	public int buttonsStartX;

	@Shadow
	public SchematicPlacement placement;

	private WidgetSchematicPlacementMixin(int x, int y, int width, int height) {
		super(x, y, width, height, null, 0);
	}

	@Inject(method = "<init>", at = @At("RETURN"))
	private void relay$addShareButton(CallbackInfo ignored) {
		ButtonGeneric button = new ButtonGeneric(this.buttonsStartX, this.getY() + 1, -1, true, "Share");
		button.setHoverStrings("Share this placement with your Relay group, as it is placed now");
		this.addButton(button, (clicked, mouseButton) -> RelayClient.get().sharePlacement(this.placement));
		this.buttonsStartX = button.getX() - 1;
	}
}
