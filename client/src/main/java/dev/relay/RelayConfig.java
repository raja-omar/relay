package dev.relay;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

import dev.relay.common.AccessTokens;
import dev.relay.common.Tls;
import dev.relay.fluids.LavaVisibility;
import dev.relay.fluids.WaterVisibility;
import dev.relay.patchcrumbs.PatchCrumbsPolicy;
import dev.relay.patchcrumbs.PatchCrumbsPolicy.DirectionMode;
import dev.relay.patchcrumbs.PatchCrumbsPolicy.Palette;
import dev.relay.ping.PingPolicy;
import dev.relay.place.FastPlacePolicy;

/**
 * Where the schematic server is, and the player id this client signs in with.
 *
 * <p>A release jar may pack a host and port so a player can drop the mod in. The player id is
 * never packed: it lives only in {@code relay.properties}, set by {@code /relay login}.
 */
public final class RelayConfig {
	public static final int DEFAULT_PORT = 25599;

	private static final String BAKED_DEFAULTS = "/relay-defaults.properties";

	private final Path file;

	private String host = "";
	private int port = DEFAULT_PORT;
	private String token = "";
	private String tlsPin = "";
	private boolean cantMiss = true;
	private boolean autoPurchase = true;
	private boolean prePurchase = true;
	private boolean autoRefill = true;
	private boolean fastPlace = false;
	private boolean autopot = true;
	private boolean autopotRefill = false;
	private int fastPlaceDelay = FastPlacePolicy.DEFAULT_DELAY_TICKS;
	private boolean patchcrumbs = true;
	private boolean patchcrumbsLabel = true;
	private boolean patchcrumbsTracer = false;
	private boolean patchcrumbsSandCheck = false;
	private boolean patchcrumbsAvoidCannons = false;
	private int patchcrumbsTimeout = PatchCrumbsPolicy.DEFAULT_TIMEOUT_SECONDS;
	private int patchcrumbsWidth = PatchCrumbsPolicy.DEFAULT_LINE_WIDTH;
	private DirectionMode patchcrumbsDirection = DirectionMode.BOTH;
	private Palette patchcrumbsPalette = Palette.RED;
	private boolean patchcrumbsCallouts = false;
	private boolean patchcrumbsConsistentCallouts = true;
	private int patchcrumbsCalloutTimeout = PatchCrumbsPolicy.DEFAULT_TIMEOUT_SECONDS;
	private boolean patchcrumbsShareUsingFf = true;
	private LavaVisibility lavaVisibility = LavaVisibility.NORMAL;
	private WaterVisibility waterVisibility = WaterVisibility.NORMAL;
	private boolean clearWater = false;
	private boolean pingMultiple = false;
	private boolean pingShare = true;
	private int pingTimeout = PingPolicy.DEFAULT_TIMEOUT_SECONDS;
	private Palette pingPalette = Palette.CYAN;

	private RelayConfig(Path file) {
		this.file = file;
	}

	/** Reads the config, starting from the values packed in the jar. */
	public static RelayConfig load(Path configDirectory) {
		return load(configDirectory, bakedDefaults());
	}

	/** Tests pass their own defaults so they are not coupled to the packed server address. */
	static RelayConfig load(Path configDirectory, Properties defaults) {
		RelayConfig config = new RelayConfig(configDirectory.resolve(ModInfo.ID + ".properties"));
		apply(config, defaults);

		if (!Files.isRegularFile(config.file)) {
			return config;
		}

		Properties properties = new Properties();

		try (InputStream in = Files.newInputStream(config.file)) {
			properties.load(in);
		} catch (IOException unreadable) {
			ModInfo.LOG.warn("Could not read {}, using defaults", config.file, unreadable);
			return config;
		}

		apply(config, properties);
		return config;
	}

	public void save() {
		Properties properties = new Properties();
		properties.setProperty("host", host);
		properties.setProperty("port", Integer.toString(port));
		properties.setProperty("token", token);
		properties.setProperty("tlsPin", tlsPin);
		properties.setProperty("cantMiss", Boolean.toString(cantMiss));
		properties.setProperty("autoPurchase", Boolean.toString(autoPurchase));
		properties.setProperty("prePurchase", Boolean.toString(prePurchase));
		properties.setProperty("autoRefill", Boolean.toString(autoRefill));
		properties.setProperty("fastPlace", Boolean.toString(fastPlace));
		properties.setProperty("autopot", Boolean.toString(autopot));
		properties.setProperty("autopotRefill", Boolean.toString(autopotRefill));
		properties.setProperty("fastPlaceDelay", Integer.toString(fastPlaceDelay));
		properties.setProperty("patchcrumbs", Boolean.toString(patchcrumbs));
		properties.setProperty("patchcrumbsLabel", Boolean.toString(patchcrumbsLabel));
		properties.setProperty("patchcrumbsTracer", Boolean.toString(patchcrumbsTracer));
		properties.setProperty("patchcrumbsSandCheck", Boolean.toString(patchcrumbsSandCheck));
		properties.setProperty("patchcrumbsAvoidCannons", Boolean.toString(patchcrumbsAvoidCannons));
		properties.setProperty("patchcrumbsTimeout", Integer.toString(patchcrumbsTimeout));
		properties.setProperty("patchcrumbsWidth", Integer.toString(patchcrumbsWidth));
		properties.setProperty("patchcrumbsDirection", patchcrumbsDirection.name().toLowerCase(Locale.ROOT));
		properties.setProperty("patchcrumbsColor", patchcrumbsPalette.name().toLowerCase(Locale.ROOT));
		properties.setProperty("patchcrumbsCallouts", Boolean.toString(patchcrumbsCallouts));
		properties.setProperty("patchcrumbsConsistentCallouts", Boolean.toString(patchcrumbsConsistentCallouts));
		properties.setProperty("patchcrumbsCalloutTimeout", Integer.toString(patchcrumbsCalloutTimeout));
		properties.setProperty("patchcrumbsShareUsingFf", Boolean.toString(patchcrumbsShareUsingFf));
		properties.setProperty("lavaVisibility", lavaVisibility.id());
		properties.setProperty("waterVisibility", waterVisibility.id());
		properties.setProperty("clearWater", Boolean.toString(clearWater));
		properties.setProperty("pingMultiple", Boolean.toString(pingMultiple));
		properties.setProperty("pingShare", Boolean.toString(pingShare));
		properties.setProperty("pingTimeout", Integer.toString(pingTimeout));
		properties.setProperty("pingColor", pingPalette.name().toLowerCase(Locale.ROOT));

		try {
			Files.createDirectories(file.getParent());

			try (OutputStream out = Files.newOutputStream(file)) {
				properties.store(out, " " + ModInfo.NAME + " -- schematic server to connect to."
						+ System.lineSeparator() + "# token is the player id the server issued."
						+ " Use /relay login; do not type it in public chat."
						+ System.lineSeparator() + "# tlsPin is the SHA-256 of the server certificate."
						+ " Packed in a sold jar; never a player id."
						+ System.lineSeparator() + "# cantMiss / autoPurchase / prePurchase / autoRefill default on."
						+ System.lineSeparator() + "# fastPlace defaults off. fastPlaceDelay is 0-4 ticks; 0 is every tick."
						+ System.lineSeparator() + "# autopot defaults on. The hotkey throws only while it is on."
						+ System.lineSeparator() + "# autopotRefill defaults off. It restocks empty hotbar slots."
						+ System.lineSeparator() + "# patchcrumbs settings are stored for the Relay tab only."
						+ System.lineSeparator() + "# lavaVisibility is hidden, vanilla, or see-through."
						+ System.lineSeparator() + "# waterVisibility is hidden, vanilla, or clear.");
			}
		} catch (IOException couldNotWrite) {
			ModInfo.LOG.warn("Could not save {}", file, couldNotWrite);
		}
	}

	public boolean hasServer() {
		return !host.isEmpty();
	}

	public boolean hasToken() {
		return AccessTokens.isWellFormed(token);
	}

	public boolean hasTlsPin() {
		return Tls.isWellFormedPin(tlsPin);
	}

	public String host() {
		return host;
	}

	public int port() {
		return port;
	}

	public String token() {
		return token;
	}

	public String tlsPin() {
		return tlsPin;
	}

	public String address() {
		return host + ":" + port;
	}

	/** When true, a right-click only places if it matches the loaded schematic. */
	public boolean cantMiss() {
		return cantMiss;
	}

	/** When true, a missed schematic block is bought with {@code /shop}. */
	public boolean autoPurchase() {
		return autoPurchase;
	}

	/**
	 * When true, a successful place tops the stack back up. Only runs if
	 * {@link #autoPurchase()} is also on.
	 */
	public boolean prePurchase() {
		return prePurchase;
	}

	/** When true, an emptied build-hotbar slot is restocked from the backpack. */
	public boolean autoRefill() {
		return autoRefill;
	}

	/** When true, held right-click places blocks faster than vanilla. */
	public boolean fastPlace() {
		return fastPlace;
	}

	/** Ticks to wait between held block places. Zero is every tick; four is vanilla. */
	public int fastPlaceDelay() {
		return fastPlaceDelay;
	}

	public void setCantMiss(boolean cantMiss) {
		this.cantMiss = cantMiss;
		save();
	}

	public void setAutoPurchase(boolean autoPurchase) {
		this.autoPurchase = autoPurchase;
		save();
	}

	public void setPrePurchase(boolean prePurchase) {
		this.prePurchase = prePurchase;
		save();
	}

	public void setAutoRefill(boolean autoRefill) {
		this.autoRefill = autoRefill;
		save();
	}

	public void setFastPlace(boolean fastPlace) {
		this.fastPlace = fastPlace;
		save();
	}

	/** When true, the Autpot hotkey throws an Instant Health II splash potion. */
	public boolean autopot() {
		return autopot;
	}

	public void setAutopot(boolean autopot) {
		this.autopot = autopot;
		save();
	}

	/** When true, every second Autpot throw restocks empty hotbar slots from the backpack. */
	public boolean autopotRefill() {
		return autopotRefill;
	}

	public void setAutopotRefill(boolean autopotRefill) {
		this.autopotRefill = autopotRefill;
		save();
	}

	public void setFastPlaceDelay(int delayTicks) {
		this.fastPlaceDelay = FastPlacePolicy.clampDelay(delayTicks);
		save();
	}

	/** When true, the last TNT or falling-sand shot is marked in the world. */
	public boolean patchcrumbs() {
		return patchcrumbs;
	}

	public boolean patchcrumbsLabel() {
		return patchcrumbsLabel;
	}

	public boolean patchcrumbsTracer() {
		return patchcrumbsTracer;
	}

	/** When true, a crumb is only taken when the shot is sitting on sand, gravel, or concrete powder. */
	public boolean patchcrumbsSandCheck() {
		return patchcrumbsSandCheck;
	}

	/** When true, TNT still in a dispenser cluster is ignored. */
	public boolean patchcrumbsAvoidCannons() {
		return patchcrumbsAvoidCannons;
	}

	public int patchcrumbsTimeout() {
		return patchcrumbsTimeout;
	}

	public int patchcrumbsWidth() {
		return patchcrumbsWidth;
	}

	public DirectionMode patchcrumbsDirection() {
		return patchcrumbsDirection;
	}

	public Palette patchcrumbsPalette() {
		return patchcrumbsPalette;
	}

	public boolean patchcrumbsCallouts() {
		return patchcrumbsCallouts;
	}

	public boolean patchcrumbsConsistentCallouts() {
		return patchcrumbsConsistentCallouts;
	}

	public int patchcrumbsCalloutTimeout() {
		return patchcrumbsCalloutTimeout;
	}

	public boolean patchcrumbsShareUsingFf() {
		return patchcrumbsShareUsingFf;
	}

	public void setPatchcrumbs(boolean patchcrumbs) {
		this.patchcrumbs = patchcrumbs;
		save();
	}

	public void setPatchcrumbsLabel(boolean patchcrumbsLabel) {
		this.patchcrumbsLabel = patchcrumbsLabel;
		save();
	}

	public void setPatchcrumbsTracer(boolean patchcrumbsTracer) {
		this.patchcrumbsTracer = patchcrumbsTracer;
		save();
	}

	public void setPatchcrumbsSandCheck(boolean patchcrumbsSandCheck) {
		this.patchcrumbsSandCheck = patchcrumbsSandCheck;
		save();
	}

	public void setPatchcrumbsAvoidCannons(boolean patchcrumbsAvoidCannons) {
		this.patchcrumbsAvoidCannons = patchcrumbsAvoidCannons;
		save();
	}

	public void setPatchcrumbsTimeout(int seconds) {
		this.patchcrumbsTimeout = PatchCrumbsPolicy.clampTimeout(seconds);
		save();
	}

	public void setPatchcrumbsWidth(int width) {
		this.patchcrumbsWidth = PatchCrumbsPolicy.clampWidth(width);
		save();
	}

	public void setPatchcrumbsDirection(DirectionMode direction) {
		this.patchcrumbsDirection = direction == null ? DirectionMode.BOTH : direction;
		save();
	}

	public void setPatchcrumbsPalette(Palette palette) {
		this.patchcrumbsPalette = palette == null ? Palette.RED : palette;
		save();
	}

	public void setPatchcrumbsCallouts(boolean patchcrumbsCallouts) {
		this.patchcrumbsCallouts = patchcrumbsCallouts;
		save();
	}

	public void setPatchcrumbsConsistentCallouts(boolean patchcrumbsConsistentCallouts) {
		this.patchcrumbsConsistentCallouts = patchcrumbsConsistentCallouts;
		save();
	}

	public void setPatchcrumbsCalloutTimeout(int seconds) {
		this.patchcrumbsCalloutTimeout = PatchCrumbsPolicy.clampTimeout(seconds);
		save();
	}

	public void setPatchcrumbsShareUsingFf(boolean patchcrumbsShareUsingFf) {
		this.patchcrumbsShareUsingFf = patchcrumbsShareUsingFf;
		save();
	}

	/** How lava is drawn. Does not change water. */
	public LavaVisibility lavaVisibility() {
		return lavaVisibility;
	}

	public void setLavaVisibility(LavaVisibility lavaVisibility) {
		this.lavaVisibility = lavaVisibility == null ? LavaVisibility.NORMAL : lavaVisibility;
		save();
	}

	/** Whether water is drawn. Off skips the mesh without a resource pack. */
	public WaterVisibility waterVisibility() {
		return waterVisibility;
	}

	public void setWaterVisibility(WaterVisibility waterVisibility) {
		this.waterVisibility = waterVisibility == null ? WaterVisibility.NORMAL : waterVisibility;
		this.clearWater = this.waterVisibility == WaterVisibility.CLEAR;
		save();
	}

	/** When true, water is visible and swimming does not change fog, FOV, or overlay. */
	public boolean clearWater() {
		return waterVisibility == WaterVisibility.CLEAR;
	}

	public void setClearWater(boolean clearWater) {
		setWaterVisibility(clearWater ? WaterVisibility.CLEAR : WaterVisibility.NORMAL);
	}

	/** When true, each ping is kept until it fades. Off replaces the previous beam. */
	public boolean pingMultiple() {
		return pingMultiple;
	}

	public void setPingMultiple(boolean pingMultiple) {
		this.pingMultiple = pingMultiple;
		save();
	}

	/** When false, pings stay on this client and are not sent to the group. */
	public boolean pingShare() {
		return pingShare;
	}

	public void setPingShare(boolean pingShare) {
		this.pingShare = pingShare;
		save();
	}

	public int pingTimeout() {
		return pingTimeout;
	}

	public void setPingTimeout(int seconds) {
		this.pingTimeout = PingPolicy.clampTimeout(seconds);
		save();
	}

	public Palette pingPalette() {
		return pingPalette;
	}

	public void setPingPalette(Palette palette) {
		this.pingPalette = palette == null ? Palette.CYAN : palette;
		save();
	}

	/** Points at a different server and remembers it for next time. The player id is left alone. */
	public void setServer(String host, int port) {
		this.host = host.trim();
		this.port = port;
		save();
	}

	public void setServer(String host, int port, String tlsPin) {
		this.host = host.trim();
		this.port = port;
		this.tlsPin = Tls.normalizePin(tlsPin);
		save();
	}

	/** Stores the issued id in the grouped form, so the file is readable. */
	public void setToken(String token) {
		this.token = AccessTokens.display(token);
		save();
	}

	private static void apply(RelayConfig config, Properties properties) {
		if (properties.containsKey("host")) {
			config.host = properties.getProperty("host", "").trim();
		}

		if (properties.containsKey("token")) {
			String token = properties.getProperty("token", "").trim();
			config.token = AccessTokens.isWellFormed(token) ? AccessTokens.display(token) : token;
		}

		if (properties.containsKey("port")) {
			config.port = readPort(properties.getProperty("port"));
		}

		if (properties.containsKey("tlsPin")) {
			config.tlsPin = Tls.normalizePin(properties.getProperty("tlsPin", ""));
		}

		if (properties.containsKey("cantMiss")) {
			config.cantMiss = readFlag(properties.getProperty("cantMiss"), true);
		}

		if (properties.containsKey("autoPurchase")) {
			config.autoPurchase = readFlag(properties.getProperty("autoPurchase"), true);
		}

		if (properties.containsKey("prePurchase")) {
			config.prePurchase = readFlag(properties.getProperty("prePurchase"), true);
		}

		if (properties.containsKey("autoRefill")) {
			config.autoRefill = readFlag(properties.getProperty("autoRefill"), true);
		}

		if (properties.containsKey("fastPlace")) {
			config.fastPlace = readFlag(properties.getProperty("fastPlace"), false);
		}

		if (properties.containsKey("autopot")) {
			config.autopot = readFlag(properties.getProperty("autopot"), true);
		}

		if (properties.containsKey("autopotRefill")) {
			config.autopotRefill = readFlag(properties.getProperty("autopotRefill"), false);
		}

		if (properties.containsKey("fastPlaceDelay")) {
			config.fastPlaceDelay = FastPlacePolicy.clampDelay(
					readInt(properties.getProperty("fastPlaceDelay"), FastPlacePolicy.DEFAULT_DELAY_TICKS));
		}

		if (properties.containsKey("patchcrumbs")) {
			config.patchcrumbs = readFlag(properties.getProperty("patchcrumbs"), true);
		}

		if (properties.containsKey("patchcrumbsLabel")) {
			config.patchcrumbsLabel = readFlag(properties.getProperty("patchcrumbsLabel"), true);
		}

		if (properties.containsKey("patchcrumbsTracer")) {
			config.patchcrumbsTracer = readFlag(properties.getProperty("patchcrumbsTracer"), false);
		}

		if (properties.containsKey("patchcrumbsSandCheck")) {
			config.patchcrumbsSandCheck = readFlag(properties.getProperty("patchcrumbsSandCheck"), false);
		}

		if (properties.containsKey("patchcrumbsAvoidCannons")) {
			config.patchcrumbsAvoidCannons = readFlag(properties.getProperty("patchcrumbsAvoidCannons"), false);
		}

		if (properties.containsKey("patchcrumbsTimeout")) {
			config.patchcrumbsTimeout = PatchCrumbsPolicy.clampTimeout(
					readInt(properties.getProperty("patchcrumbsTimeout"), PatchCrumbsPolicy.DEFAULT_TIMEOUT_SECONDS));
		}

		if (properties.containsKey("patchcrumbsWidth")) {
			config.patchcrumbsWidth = PatchCrumbsPolicy.clampWidth(
					readInt(properties.getProperty("patchcrumbsWidth"), PatchCrumbsPolicy.DEFAULT_LINE_WIDTH));
		}

		if (properties.containsKey("patchcrumbsDirection")) {
			config.patchcrumbsDirection = DirectionMode.fromName(
					properties.getProperty("patchcrumbsDirection"), DirectionMode.BOTH);
		}

		if (properties.containsKey("patchcrumbsColor")) {
			config.patchcrumbsPalette = Palette.fromName(properties.getProperty("patchcrumbsColor"), Palette.RED);
		}

		if (properties.containsKey("patchcrumbsCallouts")) {
			config.patchcrumbsCallouts = readFlag(properties.getProperty("patchcrumbsCallouts"), false);
		}

		if (properties.containsKey("patchcrumbsConsistentCallouts")) {
			config.patchcrumbsConsistentCallouts = readFlag(
					properties.getProperty("patchcrumbsConsistentCallouts"), true);
		}

		if (properties.containsKey("patchcrumbsCalloutTimeout")) {
			config.patchcrumbsCalloutTimeout = PatchCrumbsPolicy.clampTimeout(
					readInt(properties.getProperty("patchcrumbsCalloutTimeout"),
							PatchCrumbsPolicy.DEFAULT_TIMEOUT_SECONDS));
		}

		if (properties.containsKey("patchcrumbsShareUsingFf")) {
			config.patchcrumbsShareUsingFf = readFlag(properties.getProperty("patchcrumbsShareUsingFf"), true);
		}

		if (properties.containsKey("lavaVisibility")) {
			config.lavaVisibility = LavaVisibility.fromName(
					properties.getProperty("lavaVisibility"), LavaVisibility.NORMAL);
		}

		if (properties.containsKey("waterVisibility")) {
			config.waterVisibility = WaterVisibility.fromName(
					properties.getProperty("waterVisibility"), WaterVisibility.NORMAL);
		}

		// Older jars stored Clear as waterVisibility=normal plus clearWater=true.
		if (properties.containsKey("clearWater")
				&& readFlag(properties.getProperty("clearWater"), false)
				&& config.waterVisibility == WaterVisibility.NORMAL) {
			config.waterVisibility = WaterVisibility.CLEAR;
		}
		config.clearWater = config.waterVisibility == WaterVisibility.CLEAR;

		if (properties.containsKey("pingMultiple")) {
			config.pingMultiple = readFlag(properties.getProperty("pingMultiple"), false);
		}

		if (properties.containsKey("pingShare")) {
			config.pingShare = readFlag(properties.getProperty("pingShare"), true);
		}

		if (properties.containsKey("pingTimeout")) {
			config.pingTimeout = PingPolicy.clampTimeout(
					readInt(properties.getProperty("pingTimeout"), PingPolicy.DEFAULT_TIMEOUT_SECONDS));
		}

		if (properties.containsKey("pingColor")) {
			config.pingPalette = Palette.fromName(properties.getProperty("pingColor"), Palette.CYAN);
		}

		// First jar wrote patchcrumbs off with sand-check and skip-cannons on. That hid every
		// overlay. Treat that stock trio as "never configured" and turn the marker on.
		if (properties.containsKey("patchcrumbs")
				&& !config.patchcrumbs
				&& config.patchcrumbsSandCheck
				&& config.patchcrumbsAvoidCannons
				&& !config.patchcrumbsTracer
				&& config.patchcrumbsLabel
				&& config.patchcrumbsTimeout == PatchCrumbsPolicy.DEFAULT_TIMEOUT_SECONDS
				&& config.patchcrumbsWidth == PatchCrumbsPolicy.DEFAULT_LINE_WIDTH) {
			config.patchcrumbs = true;
			config.patchcrumbsSandCheck = false;
			config.patchcrumbsAvoidCannons = false;
		}
	}

	private static Properties bakedDefaults() {
		Properties properties = new Properties();

		try (InputStream in = RelayConfig.class.getResourceAsStream(BAKED_DEFAULTS)) {
			if (in != null) {
				properties.load(in);
			}
		} catch (IOException ignored) {
			// Packed defaults are a convenience. Missing them just means "no server yet".
		}

		return properties;
	}

	private static boolean readFlag(String value, boolean fallback) {
		if (value == null || value.isBlank()) {
			return fallback;
		}

		return switch (value.trim().toLowerCase(Locale.ROOT)) {
			case "true", "yes", "on" -> true;
			case "false", "no", "off" -> false;
			default -> fallback;
		};
	}

	private static int readInt(String value, int fallback) {
		if (value == null || value.isBlank()) {
			return fallback;
		}

		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException notANumber) {
			return fallback;
		}
	}

	private static int readPort(String value) {
		if (value == null || value.isBlank()) {
			return DEFAULT_PORT;
		}

		try {
			int port = Integer.parseInt(value.trim());
			return port >= 1 && port <= 65535 ? port : DEFAULT_PORT;
		} catch (NumberFormatException notANumber) {
			ModInfo.LOG.warn("Ignoring unusable port \"{}\"", value);
			return DEFAULT_PORT;
		}
	}
}
