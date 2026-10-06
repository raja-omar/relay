package dev.relay.gui;

import java.util.List;

import dev.relay.RelayClient;
import dev.relay.RelayClientMod;
import dev.relay.RelayConfig;
import dev.relay.patchcrumbs.PatchCrumb;
import dev.relay.patchcrumbs.PatchCrumbs;
import dev.relay.patchcrumbs.PatchCrumbsPolicy;

import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Sizing;

/**
 * Patchcrumbs tab: ABC sand-stack detection, world overlay, and optional callouts.
 */
final class PatchcrumbsModule implements RelayModule {
	@Override
	public String id() {
		return "patchcrumbs";
	}

	@Override
	public String name() {
		return "Patchcrumbs";
	}

	@Override
	public String description() {
		return "Mark the last cannon shot so you can patch the wall.";
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
		return 0;
	}

	@Override
	public List<HotkeyBinding> hotkeys() {
		return List.of(
				new HotkeyBinding(id(), name(), "Toggle Patchcrumbs", RelayClientMod::patchcrumbsKey),
				new HotkeyBinding(id(), name(), "Callout Shot", RelayClientMod::calloutKey));
	}

	@Override
	public String uiState() {
		RelayConfig config = RelayClient.get().config();
		PatchCrumb crumb = PatchCrumbs.currentCrumb;
		String crumbState = crumb == null ? "none" : crumb.posX + "," + crumb.posY + "," + crumb.posZ;
		return config.patchcrumbs() + "|" + config.patchcrumbsLabel() + "|" + config.patchcrumbsTracer()
				+ "|" + config.patchcrumbsSandCheck() + "|" + config.patchcrumbsAvoidCannons()
				+ "|" + config.patchcrumbsTimeout() + "|" + config.patchcrumbsWidth()
				+ "|" + config.patchcrumbsDirection() + "|" + config.patchcrumbsPalette()
				+ "|" + config.patchcrumbsCallouts() + "|" + config.patchcrumbsConsistentCallouts()
				+ "|" + config.patchcrumbsCalloutTimeout() + "|" + config.patchcrumbsShareUsingFf()
				+ "|" + crumbState;
	}

	@Override
	public void render(FlowLayout content, RelayScreen overlay) {
		RelayConfig config = RelayClient.get().config();
		FlowLayout inner = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
		inner.gap(2);
		inner.child(RelayUi.title(name()));
		inner.child(RelayUi.muted("Ignite TNT and a box plus long patch lines appear at the shot."));
		inner.child(RelayUi.muted(statusLine(config)));
		overlay.toggleRow(inner, "Patchcrumbs",
				"Watch loaded TNT and falling sand, then mark the last shot in the world.",
				RelayClientMod.keyHint(RelayClientMod.patchcrumbsKey()),
				config.patchcrumbs(), true, config::setPatchcrumbs);
		overlay.toggleRow(inner, "Label",
				"Show X Y Z above the shot.",
				"",
				config.patchcrumbsLabel(), true, config::setPatchcrumbsLabel);
		overlay.toggleRow(inner, "Tracer",
				"Draw a line from you to the shot.",
				"",
				config.patchcrumbsTracer(), true, config::setPatchcrumbsTracer);
		overlay.toggleRow(inner, "Sand check",
				"Turn ABC sand-stack detection off and only snap a crumb when TNT is sitting on sand or gravel.",
				"",
				config.patchcrumbsSandCheck(), true, config::setPatchcrumbsSandCheck);
		overlay.toggleRow(inner, "Skip cannons",
				"Hide the overlay while you are inside the cannon area set with /relay cannon pos1 and pos2.",
				"",
				config.patchcrumbsAvoidCannons(), true, config::setPatchcrumbsAvoidCannons);
		overlay.stepperRow(inner, "Timeout", PatchCrumbsPolicy.timeoutHint(),
				PatchCrumbsPolicy.timeoutLabel(config.patchcrumbsTimeout()),
				PatchCrumbsPolicy.canLowerTimeout(config.patchcrumbsTimeout()),
				PatchCrumbsPolicy.canRaiseTimeout(config.patchcrumbsTimeout()),
				() -> config.setPatchcrumbsTimeout(config.patchcrumbsTimeout() - 1),
				() -> config.setPatchcrumbsTimeout(config.patchcrumbsTimeout() + 1));
		overlay.stepperRow(inner, "Width", PatchCrumbsPolicy.widthHint(),
				Integer.toString(config.patchcrumbsWidth()),
				PatchCrumbsPolicy.canLowerWidth(config.patchcrumbsWidth()),
				PatchCrumbsPolicy.canRaiseWidth(config.patchcrumbsWidth()),
				() -> config.setPatchcrumbsWidth(config.patchcrumbsWidth() - 1),
				() -> config.setPatchcrumbsWidth(config.patchcrumbsWidth() + 1));
		overlay.stepperRow(inner, "Direction", PatchCrumbsPolicy.directionHint(),
				config.patchcrumbsDirection().label(),
				true, true,
				() -> config.setPatchcrumbsDirection(config.patchcrumbsDirection().previous()),
				() -> config.setPatchcrumbsDirection(config.patchcrumbsDirection().next()));
		overlay.stepperRow(inner, "Color", "Color of the shot box and the patch lines. RGB cycles through every hue.",
				config.patchcrumbsPalette().label(),
				true, true,
				() -> config.setPatchcrumbsPalette(config.patchcrumbsPalette().previous()),
				() -> config.setPatchcrumbsPalette(config.patchcrumbsPalette().next()));
		overlay.toggleRow(inner, "Callouts",
				"Share the current shot in chat, as [OrbitCallout] x: y: z:.",
				RelayClientMod.keyHint(RelayClientMod.calloutKey()),
				config.patchcrumbsCallouts(), true, config::setPatchcrumbsCallouts);
		overlay.toggleRow(inner, "Consistent callouts",
				"Repeat the callout automatically using the callout timeout.",
				"",
				config.patchcrumbsConsistentCallouts(), true, config::setPatchcrumbsConsistentCallouts);
		overlay.stepperRow(inner, "Callout timeout", "Seconds between automatic callouts.",
				PatchCrumbsPolicy.timeoutLabel(config.patchcrumbsCalloutTimeout()),
				PatchCrumbsPolicy.canLowerTimeout(config.patchcrumbsCalloutTimeout()),
				PatchCrumbsPolicy.canRaiseTimeout(config.patchcrumbsCalloutTimeout()),
				() -> config.setPatchcrumbsCalloutTimeout(config.patchcrumbsCalloutTimeout() - 1),
				() -> config.setPatchcrumbsCalloutTimeout(config.patchcrumbsCalloutTimeout() + 1));
		overlay.toggleRow(inner, "Share with /ff",
				"Send the callout as /ff instead of a normal chat line.",
				"",
				config.patchcrumbsShareUsingFf(), true, config::setPatchcrumbsShareUsingFf);
		content.child(RelayUi.pageScroll(inner));
	}

	private static String statusLine(RelayConfig config) {
		if (!config.patchcrumbs()) {
			return "Turn Patchcrumbs on, then ignite TNT.";
		}
		PatchCrumb crumb = PatchCrumbs.currentCrumb;
		if (crumb == null || System.currentTimeMillis() > crumb.expiresAt) {
			return "Waiting for TNT or falling sand.";
		}
		return "Last shot " + crumb.posX + " " + crumb.posY + " " + crumb.posZ + ".";
	}
}
