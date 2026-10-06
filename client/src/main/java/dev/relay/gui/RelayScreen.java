package dev.relay.gui;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.jetbrains.annotations.NotNull;

import com.mojang.blaze3d.platform.InputConstants;

import dev.relay.ModInfo;
import dev.relay.RelayClient;
import dev.relay.RelayConfig;
import dev.relay.chat.ChatMessages;
import dev.relay.chat.RelayAdventure;
import dev.relay.common.GroupNames;
import dev.relay.common.PlayerNames;
import dev.relay.group.GroupState;
import dev.relay.litematica.LitematicaIntegration;
import dev.relay.schematic.PendingShares;
import dev.relay.schematic.SchematicException;
import dev.relay.schematic.SchematicFile;
import dev.relay.schematic.SchematicLibrary;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * Compact glass overlay. Modules register themselves; this class is the shell and the Java
 * actions the presentation layer can invoke.
 */
public final class RelayScreen extends BaseOwoScreen<FlowLayout> {
	private static boolean openNextTick;

	private boolean settingsOpen;
	private String moduleId = RelayModules.litematica().id();
	private String fingerprint = "";
	private String tokenDraft;
	private String groupDraft = "";
	private String inviteDraft = "";
	private String selectedInvite = "";
	private String selectedShare = "";
	private String selectedSchematic = "";
	private String selectedPlacement = "";
	private KeyMapping listening;
	private FlowLayout sidebar;
	private FlowLayout page;

	public RelayScreen() {
		super(RelayAdventure.nativeComponent(RelayAdventure.overlay(ModInfo.NAME, RelayTheme.TEXT)));
		RelayClient relay = RelayClient.get();
		RelayConfig config = relay.config();
		if (!config.hasToken() || !relay.isSignedIn()) {
			settingsOpen = true;
		} else if (relay.group().isEmpty()) {
			moduleId = RelayModules.groups().id();
		}
	}

	public static void open() {
		Minecraft.getInstance().setScreen(new RelayScreen());
	}

	/**
	 * Opens on the next tick. A chat command that calls {@link #open()} in the same frame is
	 * overwritten when Minecraft closes the chat.
	 */
	public static void openNextTick() {
		openNextTick = true;
	}

	public static void pollOpen() {
		if (openNextTick) {
			openNextTick = false;
			open();
		}
	}

	public static void toggle() {
		Minecraft client = Minecraft.getInstance();

		if (client.screen instanceof RelayScreen) {
			client.setScreen(null);
			return;
		}

		if (client.screen == null || client.screen instanceof TitleScreen || client.screen instanceof PauseScreen) {
			client.setScreen(new RelayScreen());
		}
	}

	@Override
	protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
		return OwoUIAdapter.create(this, UIContainers::verticalFlow);
	}

	@Override
	protected void build(FlowLayout root) {
		root.surface(minecraft.level == null
						? Surface.vanillaPanorama(true).and(Surface.flat(RelayTheme.SCRIM))
						: Surface.BLANK)
				.horizontalAlignment(HorizontalAlignment.CENTER)
				.verticalAlignment(VerticalAlignment.CENTER);

		RelayLayout.Panel panel = RelayLayout.panel(width, height);

		FlowLayout window = UIContainers.verticalFlow(Sizing.fixed(panel.width()), Sizing.fixed(panel.height()));
		window.surface(RelayTheme.glass());

		FlowLayout header = UIContainers.horizontalFlow(Sizing.fill(), Sizing.fixed(26));
		header.padding(Insets.of(4, 4, 10, 8));
		header.gap(8);
		header.verticalAlignment(VerticalAlignment.CENTER);
		header.child(RelayUi.heading(ModInfo.NAME));
		header.child(RelayUi.spacer());
		header.child(RelayUi.status(statusText(), statusDot()));
		header.child(RelayUi.headerLink("Settings", settingsOpen, "Relay ID and sign-in.", this::toggleSettings));
		header.child(RelayUi.headerLink("×", false, "Close", this::onClose));

		FlowLayout body = UIContainers.horizontalFlow(Sizing.fill(), Sizing.expand());
		sidebar = UIContainers.verticalFlow(Sizing.fixed(panel.sidebar()), Sizing.fill());
		sidebar.surface(RelayTheme.sidebar());
		sidebar.padding(Insets.of(8, 8, 6, 8));
		sidebar.gap(2);

		FlowLayout pane = UIContainers.verticalFlow(Sizing.expand(), Sizing.fill());
		pane.padding(Insets.of(8, 10, 12, 10));
		page = UIContainers.verticalFlow(Sizing.fill(), Sizing.expand());
		page.gap(6);
		pane.child(page);

		body.child(sidebar);
		body.child(RelayUi.hairline(true));
		body.child(pane);

		window.child(header);
		window.child(RelayUi.hairline(false));
		window.child(body);
		root.child(window);
		rebuild();
	}

	@Override
	public void tick() {
		super.tick();
		String now = fingerprint();
		if (!now.equals(fingerprint)) {
			rebuild();
		}
		HoverPane.tickHolds();
		RelayUi.tickLive();
	}

	@Override
	public void removed() {
		super.removed();
		HoverPane.clearHolds();
		RelayUi.beginRebuild();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return listening == null;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (listening != null) {
			if (event.isEscape()) {
				bind(InputConstants.UNKNOWN);
			} else {
				bind(InputConstants.getKey(event));
			}
			return true;
		}

		return super.keyPressed(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (listening != null) {
			bind(InputConstants.Type.MOUSE.getOrCreate(event.button()));
			return true;
		}

		return super.mouseClicked(event, doubleClick);
	}

	void rebuild() {
		if (sidebar == null || page == null) {
			return;
		}

		RelayUi.beginRebuild();
		sidebar.clearChildren();
		page.clearChildren();
		fillSidebar();
		if (settingsOpen) {
			SettingsView.render(page, this);
		} else {
			RelayModules.byId(moduleId).render(page, this);
		}
		fingerprint = fingerprint();
	}

	private void fillSidebar() {
		boolean divider = false;
		for (RelayModule module : RelayModules.navigation()) {
			if (module.utility() && !divider) {
				FlowLayout gap = UIContainers.verticalFlow(Sizing.fill(), Sizing.fixed(6));
				gap.verticalAlignment(VerticalAlignment.CENTER);
				gap.child(RelayUi.hairline(false));
				sidebar.child(gap);
				divider = true;
			}
			boolean selected = !settingsOpen && module.id().equals(moduleId);
			sidebar.child(RelayUi.nav(module, selected, () -> show(module)));
		}
	}

	private void show(RelayModule next) {
		if (!next.available()) {
			return;
		}
		moduleId = next.id();
		settingsOpen = false;
		listening = null;
		if (RelayModules.groups().id().equals(next.id())) {
			RelayClient.get().refreshGroup();
		}
		rebuild();
	}

	void openSettings() {
		settingsOpen = true;
		listening = null;
		rebuild();
	}

	private void toggleSettings() {
		settingsOpen = !settingsOpen;
		listening = null;
		rebuild();
	}

	boolean inWorld() {
		return minecraft != null && minecraft.player != null;
	}

	boolean inGroup() {
		return RelayClient.get().group().isPresent() && RelayClient.get().isSignedIn();
	}

	String selectedPlacement() {
		return selectedPlacement;
	}

	void selectPlacement(String id) {
		selectedPlacement = id;
	}

	String selectedSchematic() {
		return selectedSchematic;
	}

	void selectSchematic(String path) {
		selectedSchematic = path;
	}

	String selectedShare() {
		return selectedShare;
	}

	void selectShare(String id) {
		selectedShare = id;
	}

	String selectedInvite() {
		return selectedInvite;
	}

	void selectInvite(String name) {
		selectedInvite = name;
	}

	String tokenDraft() {
		return tokenDraft;
	}

	void tokenDraft(String value) {
		tokenDraft = value;
	}

	String groupDraft() {
		return groupDraft;
	}

	void groupDraft(String value) {
		groupDraft = value;
	}

	String inviteDraft() {
		return inviteDraft;
	}

	void inviteDraft(String value) {
		inviteDraft = value;
	}

	KeyMapping listening() {
		return listening;
	}

	void listen(KeyMapping mapping) {
		listening = listening == mapping ? null : mapping;
		rebuild();
	}

	void ensureTokenDraft() {
		if (tokenDraft != null) {
			return;
		}
		RelayConfig config = RelayClient.get().config();
		tokenDraft = config.hasToken() ? config.token() : "";
	}

	FlowLayout field(Sizing width, String placeholder, String value, int maxLength,
			Predicate<String> allowed, Consumer<String> changed, Runnable submit) {
		return RelayUi.field(width, placeholder, value, maxLength, allowed, changed, submit);
	}

	void toggleRow(FlowLayout content, String title, String help, String hint, boolean on, boolean usable,
			Consumer<Boolean> setter) {
		content.child(RelayUi.toggleRow(title, help, hint, on, usable, value -> {
			setter.accept(value);
			rebuild();
		}));
	}

	void stepperRow(FlowLayout content, String title, String help, String value, boolean minusEnabled,
			boolean plusEnabled, Runnable minus, Runnable plus) {
		stepperRow(content, title, help, value, minusEnabled, plusEnabled, minus, plus, 52);
	}

	void stepperRow(FlowLayout content, String title, String help, String value, boolean minusEnabled,
			boolean plusEnabled, Runnable minus, Runnable plus, int valueWidth) {
		content.child(RelayUi.stepperRow(title, help, value, minusEnabled, plusEnabled, () -> {
			minus.run();
			rebuild();
		}, () -> {
			plus.run();
			rebuild();
		}, valueWidth));
	}

	private void bind(InputConstants.Key key) {
		if (listening == null) {
			return;
		}

		listening.setKey(key);
		KeyMapping.resetMapping();
		if (minecraft != null) {
			minecraft.options.save();
		}

		listening = null;
		rebuild();
	}

	void createGroup() {
		String name = groupDraft.trim();

		if (!GroupNames.isValid(name)) {
			ChatMessages.show(ChatMessages.error("\"" + name + "\" is not a usable group name."));
			ChatMessages.show(ChatMessages.info(GroupNames.RULES));
			return;
		}

		if (!RelayClient.get().createGroup(name)) {
			notConnected();
		}
	}

	void invite() {
		String player = inviteDraft.trim();

		if (!PlayerNames.isValid(player)) {
			ChatMessages.show(ChatMessages.error("\"" + player + "\" is not a player name."));
			ChatMessages.show(ChatMessages.info(
					"Use the player's Minecraft name, as it appears in the tab list."));
			return;
		}

		if (!RelayClient.get().invite(player)) {
			notConnected();
		}
	}

	void answerInvite(boolean accept) {
		if (selectedInvite.isEmpty()) {
			ChatMessages.show(ChatMessages.info("There is no invitation to answer."));
			return;
		}

		boolean sent = accept ? RelayClient.get().accept(selectedInvite) : RelayClient.get().decline(selectedInvite);

		if (!sent) {
			notConnected();
		}
	}

	void leaveGroup() {
		if (!RelayClient.get().leave()) {
			notConnected();
		}
	}

	void downloadShare() {
		if (!LitematicaIntegration.isAvailable()) {
			ChatMessages.show(ChatMessages.error("Litematica is not installed, so schematics cannot be shared"));
			return;
		}

		List<PendingShares.Share> shares = RelayClient.get().pendingShares().list();
		String raw = selectedShare;

		if (raw.isEmpty() && shares.size() == 1) {
			raw = shares.get(0).id().toString();
		}

		if (raw.isEmpty()) {
			ChatMessages.show(ChatMessages.shareUnavailable());
			return;
		}

		try {
			RelayClient.get().downloadShare(UUID.fromString(raw));
			((LitematicaModule) RelayModules.litematica()).showPlacements();
			moduleId = RelayModules.litematica().id();
			settingsOpen = false;
			selectedShare = "";
			rebuild();
		} catch (IllegalArgumentException bad) {
			ChatMessages.show(ChatMessages.shareUnavailable());
		}
	}

	void loadSelectedFile() {
		if (selectedSchematic.isEmpty()) {
			ChatMessages.show(ChatMessages.error("Select a schematic first."));
			return;
		}

		try {
			SchematicLibrary library = new SchematicLibrary(LitematicaIntegration.schematicsDirectory());
			SchematicFile file = library.require(selectedSchematic);
			library.validate(file);
			LitematicaIntegration.loadFileAsPlacement(file.path());
			ChatMessages.show(ChatMessages.success("Loaded " + file.stem() + "."));
			((LitematicaModule) RelayModules.litematica()).showPlacements();
			moduleId = RelayModules.litematica().id();
			settingsOpen = false;
			selectedSchematic = "";
			selectedPlacement = "";
			rebuild();
		} catch (SchematicException failed) {
			ChatMessages.show(ChatMessages.error(failed.getMessage()));
		}
	}

	void toggleSelectedPlacement() {
		LitematicaIntegration.toggleSelected();
	}

	void shareSelectedPlacement() {
		RelayClient.get().shareSelectedPlacement();
	}

	void unloadSelectedPlacement() {
		LitematicaIntegration.unloadSelected();
		selectedPlacement = "";
		rebuild();
	}

	void moveSelectedHere() {
		try {
			LitematicaIntegration.moveSelectedToPlayer();
		} catch (SchematicException failed) {
			ChatMessages.show(ChatMessages.error(failed.getMessage()));
		}
	}

	void movePlacementTo(String id, int x, int y, int z) {
		if (id == null || id.isEmpty()) {
			ChatMessages.show(ChatMessages.error("Select a placement first."));
			return;
		}

		selectPlacement(id);
		LitematicaIntegration.selectPlacement(id);

		try {
			LitematicaIntegration.moveTo(id, x, y, z);
		} catch (SchematicException failed) {
			ChatMessages.show(ChatMessages.error(failed.getMessage()));
		}
	}

	void nudgeSelected(FacingNudge.Move move) {
		FacingNudge.Step step = FacingNudge.step(move, playerYaw());

		try {
			LitematicaIntegration.nudgeSelected(step.x(), step.y(), step.z());
		} catch (SchematicException failed) {
			ChatMessages.show(ChatMessages.error(failed.getMessage()));
		}
	}

	void cycleSelectedRotation() {
		try {
			LitematicaIntegration.cycleSelectedRotation();
			rebuild();
		} catch (SchematicException failed) {
			ChatMessages.show(ChatMessages.error(failed.getMessage()));
		}
	}

	void cycleSelectedMirror() {
		try {
			LitematicaIntegration.cycleSelectedMirror();
			rebuild();
		} catch (SchematicException failed) {
			ChatMessages.show(ChatMessages.error(failed.getMessage()));
		}
	}

	void openSelectedMaterialList() {
		try {
			LitematicaIntegration.openSelectedMaterialList();
		} catch (SchematicException failed) {
			ChatMessages.show(ChatMessages.error(failed.getMessage()));
		}
	}

	void openSelectedVerifier() {
		try {
			LitematicaIntegration.openSelectedVerifier();
		} catch (SchematicException failed) {
			ChatMessages.show(ChatMessages.error(failed.getMessage()));
		}
	}

	float playerYaw() {
		return minecraft != null && minecraft.player != null ? minecraft.player.getYRot() : 0.0F;
	}

	private static void notConnected() {
		ChatMessages.show(ChatMessages.error("Not connected to a schematic server."));
		ChatMessages.show(ChatMessages.info("Connect with /" + ModInfo.ID + " connect <host>."));
	}

	private static String statusText() {
		RelayClient relay = RelayClient.get();

		if (!relay.isSignedIn()) {
			if (!relay.config().hasServer()) {
				return "Offline";
			}

			return switch (relay.status()) {
				case CONNECTED -> "Signing in";
				case CONNECTING -> "Connecting";
				case DISCONNECTED -> "Offline";
			};
		}

		return relay.group()
				.map(GroupState::name)
				.map(name -> shorten(name, 14))
				.orElse("Connected");
	}

	private static int statusDot() {
		RelayClient relay = RelayClient.get();

		if (relay.isSignedIn()) {
			return RelayTheme.GOOD;
		}

		return switch (relay.status()) {
			case CONNECTING -> RelayTheme.TEXT_DIM;
			case CONNECTED, DISCONNECTED -> RelayTheme.BAD;
		};
	}

	private String fingerprint() {
		RelayClient relay = RelayClient.get();
		RelayConfig config = relay.config();
		String group = relay.group()
				.map(state -> state.name() + "/" + state.onlineCount() + "/" + membersKey(state))
				.orElse("-");
		return moduleId + "|" + settingsOpen + "|" + relay.status() + "|" + relay.isSignedIn()
				+ "|" + group + "|" + relay.pendingInvites().size() + "|" + relay.pendingShares().size()
				+ "|" + config.cantMiss() + "|" + config.autoPurchase() + "|" + config.prePurchase()
				+ "|" + config.autoRefill() + "|" + config.fastPlace() + "|" + config.fastPlaceDelay()
				+ "|" + LitematicaIntegration.easyPlaceEnabled()
				+ "|" + LitematicaIntegration.easyPlaceFirst()
				+ "|" + LitematicaIntegration.placementsIdentityKey()
				+ "|" + RelayModules.byId(moduleId).uiState()
				+ "|" + selectedInvite + "|" + selectedShare + "|" + selectedSchematic + "|" + selectedPlacement
				+ "|" + (listening == null ? "-" : listening.getName());
	}

	private static String membersKey(GroupState group) {
		StringBuilder key = new StringBuilder();

		for (GroupState.Member member : group.members()) {
			key.append(member.name()).append(member.online() ? '1' : '0').append(',');
		}

		return key.toString();
	}

	private static String shorten(String name, int max) {
		if (name.length() <= max) {
			return name;
		}
		return name.substring(0, max - 1) + "...";
	}
}
