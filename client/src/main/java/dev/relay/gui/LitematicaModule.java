package dev.relay.gui;

import java.util.List;

import dev.relay.RelayClient;
import dev.relay.RelayClientMod;
import dev.relay.RelayConfig;
import dev.relay.litematica.LitematicaIntegration;
import dev.relay.litematica.LitematicaIntegration.PlacementView;
import dev.relay.schematic.PendingShares;
import dev.relay.schematic.SchematicException;
import dev.relay.schematic.SchematicFile;
import dev.relay.schematic.SchematicLibrary;

import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Sizing;

import net.minecraft.core.BlockPos;

/**
 * Litematica as a Relay module: library, sharing, movement, rotation, and placement options.
 */
final class LitematicaModule implements RelayModule {
	enum Page {
		PLACEMENTS("Placements"),
		LIBRARY("Library"),
		SHARES("Shared Schematics"),
		OPTIONS("Options");

		private final String title;

		Page(String title) {
			this.title = title;
		}
	}

	private Page page = Page.PLACEMENTS;

	@Override
	public String id() {
		return "litematica";
	}

	@Override
	public String name() {
		return "Litematica";
	}

	@Override
	public String description() {
		return "Schematics, sharing, and placement options.";
	}

	@Override
	public boolean available() {
		return true;
	}

	@Override
	public boolean utility() {
		return false;
	}

	@Override
	public int badge() {
		return RelayClient.get().pendingShares().size();
	}

	@Override
	public List<HotkeyBinding> hotkeys() {
		return List.of(
				new HotkeyBinding(id(), name(), "Toggle Easy Place", RelayClientMod::easyPlaceKey),
				new HotkeyBinding(id(), name(), "Toggle Cant Miss", RelayClientMod::cantMissKey),
				new HotkeyBinding(id(), name(), "Toggle Auto Purchase", RelayClientMod::shopKey),
				new HotkeyBinding(id(), name(), "Toggle Pre-purchase", RelayClientMod::prePurchaseKey),
				new HotkeyBinding(id(), name(), "Toggle Auto Refill", RelayClientMod::refillKey));
	}

	@Override
	public String uiState() {
		return page.name();
	}

	@Override
	public void render(FlowLayout content, RelayScreen overlay) {
		content.child(RelayUi.title(name()));

		FlowLayout tabs = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
		tabs.gap(4);
		for (Page next : Page.values()) {
			int badge = next == Page.SHARES ? RelayClient.get().pendingShares().size() : 0;
			tabs.child(RelayUi.tab(next.title, page == next, badge, () -> {
				page = next;
				overlay.rebuild();
			}));
		}
		content.child(tabs);

		if (!LitematicaIntegration.isAvailable() && page != Page.OPTIONS) {
			content.child(RelayUi.label("Litematica is not installed, so schematics cannot be loaded.",
					RelayTheme.BAD));
			return;
		}

		switch (page) {
			case PLACEMENTS -> placements(content, overlay);
			case LIBRARY -> library(content, overlay);
			case SHARES -> shares(content, overlay);
			case OPTIONS -> options(content, overlay);
		}
	}

	void showShares() {
		page = Page.SHARES;
	}

	void showPlacements() {
		page = Page.PLACEMENTS;
	}

	private void placements(FlowLayout content, RelayScreen overlay) {
		boolean inWorld = overlay.inWorld();
		boolean inGroup = overlay.inGroup();
		List<PlacementView> placements = LitematicaIntegration.placements();

		if (overlay.selectedPlacement().isEmpty()) {
			placements.stream()
					.filter(PlacementView::selected)
					.findFirst()
					.ifPresent(view -> overlay.selectPlacement(view.id()));
		}

		FlowLayout body = UIContainers.horizontalFlow(Sizing.fill(), Sizing.expand());
		body.gap(12);

		FlowLayout listCol = UIContainers.verticalFlow(Sizing.expand(), Sizing.expand());
		listCol.gap(6);
		if (placements.isEmpty()) {
			listCol.child(RelayUi.muted("No placements loaded."));
		} else {
			FlowLayout rows = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
			for (PlacementView view : placements) {
				boolean chosen = view.id().equals(overlay.selectedPlacement());
				int color = view.enabled() ? RelayTheme.TEXT : RelayTheme.MUTED;
				rows.child(RelayUi.placementRow(view.name(),
						() -> originOf(view.id(), view.origin()).getX(),
						() -> originOf(view.id(), view.origin()).getY(),
						() -> originOf(view.id(), view.origin()).getZ(),
						color, chosen, () -> {
					if (!view.id().equals(overlay.selectedPlacement())) {
						overlay.selectPlacement(view.id());
						LitematicaIntegration.selectPlacement(view.id());
						overlay.rebuild();
					}
				}, (x, y, z) -> overlay.movePlacementTo(view.id(), x, y, z)));
			}
			listCol.child(RelayUi.list(rows, Sizing.expand()));
		}

		PlacementView selected = placements.stream()
				.filter(view -> view.id().equals(overlay.selectedPlacement()))
				.findFirst()
				.orElse(null);
		if (selected != null) {
			boolean enabled = selected.enabled();
			FlowLayout actions = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
			actions.gap(4);
			actions.child(RelayUi.button(enabled ? "On" : "Off",
					enabled ? RelayUi.Kind.SUCCESS : RelayUi.Kind.GHOST,
					true, "Turn this placement on or off.", overlay::toggleSelectedPlacement));
			actions.child(RelayUi.button("Share", RelayUi.Kind.GHOST, inGroup,
					"Share this placement with your Relay group.", overlay::shareSelectedPlacement));
			actions.child(RelayUi.button("Unload", RelayUi.Kind.GHOST, true,
					"Remove this placement from the world. The file on disk is left alone.",
					overlay::unloadSelectedPlacement));
			listCol.child(actions);
		}
		body.child(listCol);

		if (selected != null) {
			body.child(movePad(overlay, inWorld, selected));
		}
		content.child(body);
	}

	private FlowLayout movePad(RelayScreen overlay, boolean inWorld, PlacementView view) {
		FlowLayout pad = UIContainers.verticalFlow(Sizing.fixed(124), Sizing.content());
		pad.gap(8);
		pad.horizontalAlignment(HorizontalAlignment.CENTER);
		if (inWorld) {
			pad.child(caption("Move"));
			pad.child(dpad(overlay, 20));

			FlowLayout vert = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
			vert.gap(6);
			vert.child(nudge(overlay, "+", FacingNudge.Move.UP, 20));
			vert.child(nudge(overlay, "−", FacingNudge.Move.DOWN, 20));
			pad.child(vert);
		}

		boolean canTurn = !view.locked();
		String lockHint = PlacementFacing.lockedHint();
		FlowLayout facing = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
		facing.gap(5);
		facing.child(RelayUi.actionRow("Rotate", PlacementFacing.rotationLabel(view.rotation()), canTurn,
				canTurn ? PlacementFacing.rotateHint() : lockHint, overlay::cycleSelectedRotation));
		facing.child(RelayUi.actionRow("Mirror", PlacementFacing.mirrorLabel(view.mirror()), canTurn,
				canTurn ? PlacementFacing.mirrorHint() : lockHint, overlay::cycleSelectedMirror));
		facing.child(RelayUi.actionRow("Materials", "Open", true, PlacementFacing.materialsHint(),
				overlay::openSelectedMaterialList));
		facing.child(RelayUi.actionRow("Verifier", "Open", true, PlacementFacing.verifierHint(),
				overlay::openSelectedVerifier));
		pad.child(facing);
		return pad;
	}

	private FlowLayout dpad(RelayScreen overlay, int size) {
		FlowLayout pad = UIContainers.verticalFlow(Sizing.content(), Sizing.content());
		pad.gap(5);
		pad.horizontalAlignment(HorizontalAlignment.CENTER);
		pad.child(nudge(overlay, "↑", FacingNudge.Move.FORWARD, size));

		FlowLayout mid = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
		mid.gap(5);
		mid.child(nudge(overlay, "←", FacingNudge.Move.LEFT, size));
		mid.child(RelayUi.padIcon(size, true, "Move this placement to your feet.", overlay::moveSelectedHere,
				RelayTheme.centeredDisc(RelayTheme.TEXT, 2.4)));
		mid.child(nudge(overlay, "→", FacingNudge.Move.RIGHT, size));
		pad.child(mid);

		pad.child(nudge(overlay, "↓", FacingNudge.Move.BACK, size));
		return pad;
	}

	private static LabelComponent caption(String text) {
		LabelComponent label = RelayUi.muted(text);
		label.horizontalTextAlignment(HorizontalAlignment.CENTER);
		return label;
	}

	private static BlockPos originOf(String id, BlockPos fallback) {
		return LitematicaIntegration.placements().stream()
				.filter(view -> view.id().equals(id))
				.findFirst()
				.map(PlacementView::origin)
				.orElse(fallback);
	}

	private FlowLayout nudge(RelayScreen overlay, String label, FacingNudge.Move move, int size) {
		return RelayUi.button(label, RelayUi.Kind.PAD, true, FacingNudge.hint(move),
				() -> overlay.nudgeSelected(move), Sizing.fixed(size), Sizing.fixed(size));
	}

	private void library(FlowLayout content, RelayScreen overlay) {
		SchematicLibrary library = new SchematicLibrary(LitematicaIntegration.schematicsDirectory());
		List<SchematicFile> files;
		String empty = "Save a schematic with Litematica first.";
		int emptyColor = RelayTheme.MUTED;

		try {
			files = library.list();
		} catch (SchematicException failed) {
			files = List.of();
			empty = failed.getMessage();
			emptyColor = RelayTheme.BAD;
		}

		FlowLayout rows = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
		if (files.isEmpty()) {
			content.child(RelayUi.label(empty, emptyColor));
		} else {
			for (SchematicFile file : files) {
				boolean chosen = file.relativePath().equals(overlay.selectedSchematic());
				rows.child(RelayUi.listRow(file.relativePath(), file.sizeLabel(), RelayTheme.TEXT, chosen, () -> {
					overlay.selectSchematic(file.relativePath());
					overlay.rebuild();
				}, overlay::loadSelectedFile));
			}
			content.child(RelayUi.list(rows, Sizing.expand()));
		}

		if (!overlay.selectedSchematic().isEmpty()) {
			content.child(RelayUi.button("Load", RelayUi.Kind.PRIMARY, overlay.inWorld(),
					"Load this schematic at your feet.", overlay::loadSelectedFile));
		}
	}

	private void shares(FlowLayout content, RelayScreen overlay) {
		List<PendingShares.Share> shares = RelayClient.get().pendingShares().list();
		FlowLayout rows = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
		if (shares.isEmpty()) {
			content.child(RelayUi.muted("No shared schematics waiting from your group."));
		} else {
			for (PendingShares.Share share : shares) {
				boolean chosen = share.id().toString().equals(overlay.selectedShare());
				rows.child(RelayUi.listRow(share.name(), "from " + share.senderName(), RelayTheme.TEXT, chosen, () -> {
					overlay.selectShare(share.id().toString());
					overlay.rebuild();
				}, overlay::downloadShare));
			}
			content.child(RelayUi.list(rows, Sizing.expand()));
		}

		boolean canDownload = !overlay.selectedShare().isEmpty() || shares.size() == 1;
		if (canDownload) {
			content.child(RelayUi.button("Download", RelayUi.Kind.PRIMARY, true,
					"Load this shared placement in Litematica.", overlay::downloadShare));
		}
	}

	private void options(FlowLayout content, RelayScreen overlay) {
		RelayConfig config = RelayClient.get().config();
		FlowLayout inner = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
		inner.gap(2);
		inner.child(RelayUi.heading("Placement"));
		overlay.toggleRow(inner, "Easy Place", "Litematica Easy Place. Also turns on hold-to-place.",
				RelayClientMod.keyHint(RelayClientMod.easyPlaceKey()),
				LitematicaIntegration.easyPlaceEnabled(),
				LitematicaIntegration.isAvailable(),
				LitematicaIntegration::setEasyPlaceEnabled);
		overlay.toggleRow(inner, "Cant Miss", "Right-clicks only place when they match the schematic.",
				RelayClientMod.keyHint(RelayClientMod.cantMissKey()),
				config.cantMiss(), true, config::setCantMiss);
		overlay.toggleRow(inner, "Easy Place First", "Place the closest schematic block instead of the furthest one.",
				"",
				LitematicaIntegration.easyPlaceFirst(),
				LitematicaIntegration.isAvailable(),
				LitematicaIntegration::setEasyPlaceFirst);
		inner.child(RelayUi.section("Shop"));
		overlay.toggleRow(inner, "Shop", "Buy a missing schematic block with /shop.",
				RelayClientMod.keyHint(RelayClientMod.shopKey()),
				config.autoPurchase(), true, config::setAutoPurchase);
		overlay.toggleRow(inner, "Pre-purchase", "Top the stack up after a successful place.",
				RelayClientMod.keyHint(RelayClientMod.prePurchaseKey()),
				config.prePurchase(), true, config::setPrePurchase);
		inner.child(RelayUi.section("Hotbar"));
		overlay.toggleRow(inner, "Refill", "Restock emptied build-hotbar slots from the backpack.",
				RelayClientMod.keyHint(RelayClientMod.refillKey()),
				config.autoRefill(), true, config::setAutoRefill);
		content.child(RelayUi.pageScroll(inner));
	}
}
