package dev.relay;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import dev.relay.chat.ChatMessages;
import dev.relay.combat.Autopot;
import dev.relay.commands.RelayCommand;
import dev.relay.gui.RelayScreen;
import dev.relay.litematica.LitematicaIntegration;
import dev.relay.fluids.LavaPacks;
import dev.relay.fluids.LavaVisibility;
import dev.relay.patchcrumbs.Callouts;
import dev.relay.patchcrumbs.CrumbRenderer;
import dev.relay.patchcrumbs.PatchCrumbs;
import dev.relay.ping.BlockPings;
import dev.relay.ping.PingRenderer;
import dev.relay.place.CantMiss;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Entry point. Registers commands and reports what we found at startup; nothing here may block,
 * because Minecraft has to keep working whether or not the rest of the system is reachable.
 */
public final class RelayClientMod implements ClientModInitializer {
	private static final KeyMapping.Category KEYS = KeyMapping.Category.register(
			Identifier.fromNamespaceAndPath(ModInfo.ID, "main"));

	private static KeyMapping openGuiKey;
	private static KeyMapping cantMissKey;
	private static KeyMapping easyPlaceKey;
	private static KeyMapping shopKey;
	private static KeyMapping prePurchaseKey;
	private static KeyMapping refillKey;
	private static KeyMapping fastPlaceKey;
	private static KeyMapping patchcrumbsKey;
	private static KeyMapping calloutKey;
	private static KeyMapping lavaKey;
	private static KeyMapping pingBlockKey;
	private static KeyMapping autopotKey;

	@Override
	public void onInitializeClient() {
		ModInfo.LOG.info("Starting {} {}", ModInfo.NAME, ModInfo.version());
		logLitematicaStatus();

		RelayClient relay = RelayClient.create(RelayConfig.load(FabricLoader.getInstance().getConfigDir()));

		ClientCommandRegistrationCallback.EVENT.register(
				(dispatcher, registryAccess) -> RelayCommand.register(dispatcher));

		openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.relay.open",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_RIGHT_SHIFT,
				KEYS));
		cantMissKey = unbound("key.relay.cantmiss");
		easyPlaceKey = unbound("key.relay.easyplace");
		shopKey = unbound("key.relay.shop");
		prePurchaseKey = unbound("key.relay.prepurchase");
		refillKey = unbound("key.relay.refill");
		fastPlaceKey = unbound("key.relay.fastplace");
		patchcrumbsKey = unbound("key.relay.patchcrumbs");
		calloutKey = unbound("key.relay.callout");
		lavaKey = unbound("key.relay.lava");
		pingBlockKey = unbound("key.relay.ping");
		autopotKey = unbound("key.relay.autpot");

		CrumbRenderer.register();
		PingRenderer.register();
		LavaPacks.register();

		// Waits for the client to be up, so the player's name and the chat are both ready.
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			connectIfConfigured(relay);
			LavaPacks.apply(LavaVisibility.current());
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> relay.shutdown());
		ClientTickEvents.START_CLIENT_TICK.register(client -> {
			PatchCrumbs.onClientTickStart(client);
			Callouts.onClientTick(client);
			BlockPings.onClientTick(client);
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			CantMiss.tick(client);
			RelayScreen.pollOpen();
			consumeHotkeys(relay);
			Autopot.tick(client);
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			PatchCrumbs.clearSession();
			BlockPings.clear();
			Autopot.clear();
		});

		ModInfo.LOG.info("{} ready", ModInfo.NAME);
	}

	public static KeyMapping openGuiKey() {
		return openGuiKey;
	}

	public static KeyMapping cantMissKey() {
		return cantMissKey;
	}

	public static KeyMapping easyPlaceKey() {
		return easyPlaceKey;
	}

	public static KeyMapping shopKey() {
		return shopKey;
	}

	public static KeyMapping prePurchaseKey() {
		return prePurchaseKey;
	}

	public static KeyMapping refillKey() {
		return refillKey;
	}

	public static KeyMapping fastPlaceKey() {
		return fastPlaceKey;
	}

	public static KeyMapping patchcrumbsKey() {
		return patchcrumbsKey;
	}

	public static KeyMapping calloutKey() {
		return calloutKey;
	}

	public static KeyMapping lavaKey() {
		return lavaKey;
	}

	public static KeyMapping pingBlockKey() {
		return pingBlockKey;
	}

	public static KeyMapping autopotKey() {
		return autopotKey;
	}

	/** The bound key's label, or empty when the hotkey is unbound. */
	public static String keyHint(KeyMapping mapping) {
		if (mapping == null || mapping.isUnbound()) {
			return "";
		}

		return mapping.getTranslatedKeyMessage().getString();
	}

	private static void consumeHotkeys(RelayClient relay) {
		while (openGuiKey.consumeClick()) {
			RelayScreen.toggle();
		}

		RelayConfig config = relay.config();

		while (cantMissKey.consumeClick()) {
			boolean next = !config.cantMiss();
			config.setCantMiss(next);
			ChatMessages.show(ChatMessages.success("cantMiss " + onOff(next) + "."));
		}

		while (easyPlaceKey.consumeClick()) {
			if (!LitematicaIntegration.isAvailable()) {
				ChatMessages.show(ChatMessages.error("Litematica is not installed."));
				continue;
			}

			boolean next = !LitematicaIntegration.easyPlaceEnabled();
			LitematicaIntegration.setEasyPlaceEnabled(next);
			ChatMessages.show(ChatMessages.success("Easy Place " + onOff(next) + "."));
		}

		while (shopKey.consumeClick()) {
			boolean next = !config.autoPurchase();
			config.setAutoPurchase(next);
			ChatMessages.show(ChatMessages.success("autoPurchase " + onOff(next) + "."));
		}

		while (prePurchaseKey.consumeClick()) {
			boolean next = !config.prePurchase();
			config.setPrePurchase(next);
			ChatMessages.show(ChatMessages.success("prePurchase " + onOff(next) + "."));
		}

		while (refillKey.consumeClick()) {
			boolean next = !config.autoRefill();
			config.setAutoRefill(next);
			ChatMessages.show(ChatMessages.success("autoRefill " + onOff(next) + "."));
		}

		while (fastPlaceKey.consumeClick()) {
			boolean next = !config.fastPlace();
			config.setFastPlace(next);
			ChatMessages.show(ChatMessages.success("Fast Place " + onOff(next) + "."));
		}

		while (patchcrumbsKey.consumeClick()) {
			boolean next = !config.patchcrumbs();
			config.setPatchcrumbs(next);
			ChatMessages.show(ChatMessages.success("Patchcrumbs " + onOff(next) + "."));
		}

		while (calloutKey.consumeClick()) {
			if (config.patchcrumbsCallouts()) {
				Callouts.calloutShot(Minecraft.getInstance());
			}
		}

		while (lavaKey.consumeClick()) {
			LavaVisibility next = config.lavaVisibility().next();
			LavaVisibility.set(next);
			ChatMessages.show(ChatMessages.success("Lava " + next.id() + "."));
		}

		while (pingBlockKey.consumeClick()) {
			BlockPings.ping(Minecraft.getInstance());
		}

		while (autopotKey.consumeClick()) {
			Autopot.press(Minecraft.getInstance());
		}
	}

	private static KeyMapping unbound(String id) {
		return KeyBindingHelper.registerKeyBinding(new KeyMapping(
				id,
				InputConstants.Type.KEYSYM,
				InputConstants.UNKNOWN.getValue(),
				KEYS));
	}

	private static String onOff(boolean enabled) {
		return enabled ? "on" : "off";
	}

	private static void connectIfConfigured(RelayClient relay) {
		if (relay.config().hasServer() && relay.config().hasToken()) {
			relay.connect();
			return;
		}

		if (relay.config().hasServer()) {
			ModInfo.LOG.info("Schematic server is set but there is no player id; use /{} login <id>",
					ModInfo.ID);
			return;
		}

		ModInfo.LOG.info("No schematic server configured; use /{} connect <host>", ModInfo.ID);
	}

	private static void logLitematicaStatus() {
		if (LitematicaIntegration.isAvailable()) {
			ModInfo.LOG.info("Found Litematica {}", LitematicaIntegration.version().orElse("(unknown version)"));
		} else {
			// Not fatal for the mod itself, but sharing schematics cannot work without it.
			ModInfo.LOG.warn("Litematica is not installed; schematic sharing will be unavailable");
		}
	}
}
