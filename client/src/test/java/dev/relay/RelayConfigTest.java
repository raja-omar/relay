package dev.relay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import dev.relay.common.AccessTokens;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The config file is the only place a player can put their issued id, so it has to be forgiving. */
class RelayConfigTest {
	@TempDir
	Path configDirectory;

	@Test
	void usesBakedDefaultsWhenThereIsNoFile() {
		Properties defaults = defaults("box.example.com", "25600");

		RelayConfig config = RelayConfig.load(configDirectory, defaults);

		assertTrue(config.hasServer());
		assertEquals("box.example.com", config.host());
		assertEquals(25600, config.port());
		assertFalse(config.hasToken());
		assertFalse(Files.isRegularFile(configDirectory.resolve("relay.properties")),
				"baked defaults should not write a file");
	}

	@Test
	void hasNoServerWhenNothingIsPackedOrSaved() {
		RelayConfig config = RelayConfig.load(configDirectory, new Properties());

		assertFalse(config.hasServer());
		assertEquals(RelayConfig.DEFAULT_PORT, config.port());
		assertEquals("", config.token());
	}

	@Test
	void readsWhatWasWritten() {
		RelayConfig saved = RelayConfig.load(configDirectory, new Properties());
		saved.setServer("schem.example.com", 25600);

		RelayConfig reloaded = RelayConfig.load(configDirectory, new Properties());

		assertTrue(reloaded.hasServer());
		assertEquals("schem.example.com", reloaded.host());
		assertEquals(25600, reloaded.port());
		assertEquals("schem.example.com:25600", reloaded.address());
	}

	@Test
	void keepsTheTlsPinItWasGiven() {
		RelayConfig saved = RelayConfig.load(configDirectory, new Properties());
		saved.setServer("schem.example.com", 25600,
				"0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF");

		RelayConfig reloaded = RelayConfig.load(configDirectory, new Properties());

		assertTrue(reloaded.hasTlsPin());
		assertEquals("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", reloaded.tlsPin());
	}

	@Test
	void packsATlsPinAlongsideTheHost() {
		Properties defaults = defaults("box.example.com", "25599");
		defaults.setProperty("tlsPin", "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");

		RelayConfig config = RelayConfig.load(configDirectory, defaults);

		assertTrue(config.hasTlsPin());
		assertEquals("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", config.tlsPin());
		assertFalse(config.hasToken(), "a sold jar must not contain a player id");
	}

	@Test
	void aConfigFileOverridesBakedDefaults() throws IOException {
		write("host=other.example.com", "port=25601");

		RelayConfig config = RelayConfig.load(configDirectory, defaults("box.example.com", "25599"));

		assertEquals("other.example.com", config.host());
		assertEquals(25601, config.port());
	}

	@Test
	void keepsTheTokenItFinds() throws IOException {
		String token = AccessTokens.issue();
		write("host=box.example.com", "port=25599", "token=" + token);

		RelayConfig config = RelayConfig.load(configDirectory, new Properties());

		assertEquals(token, config.token());
		assertTrue(config.hasToken());
	}

	@Test
	void ignoresAnOldSecretAndDoesNotTreatItAsAnId() throws IOException {
		write("host=box.example.com", "port=25599", "secret=hunter2");

		RelayConfig config = RelayConfig.load(configDirectory, new Properties());

		assertEquals("", config.token());
		assertFalse(config.hasToken());
	}

	@Test
	void setTokenKeepsTheServerAddress() {
		String token = AccessTokens.issue();
		RelayConfig config = RelayConfig.load(configDirectory, new Properties());
		config.setServer("box.example.com", 25599);
		config.setToken(token);

		RelayConfig reloaded = RelayConfig.load(configDirectory, new Properties());

		assertEquals("box.example.com", reloaded.host());
		assertEquals(token, reloaded.token());
	}

	@Test
	void ignoresSurroundingSpace() throws IOException {
		String token = AccessTokens.issue();
		write("host=  box.example.com  ", "port= 25601 ", "token= " + token + " ");

		RelayConfig config = RelayConfig.load(configDirectory, new Properties());

		assertEquals("box.example.com", config.host());
		assertEquals(25601, config.port());
		assertEquals(token, config.token());
	}

	@Test
	void fallsBackToTheDefaultPortWhenItMakesNoSense() throws IOException {
		write("host=box.example.com", "port=not-a-number");

		assertEquals(RelayConfig.DEFAULT_PORT, RelayConfig.load(configDirectory, new Properties()).port());

		write("host=box.example.com", "port=70000");

		assertEquals(RelayConfig.DEFAULT_PORT, RelayConfig.load(configDirectory, new Properties()).port());
	}

	@Test
	void treatsAMissingHostAsNoServer() throws IOException {
		write("port=25599", "token=" + AccessTokens.issue());

		assertFalse(RelayConfig.load(configDirectory, new Properties()).hasServer());
	}

	@Test
	void placementFlagsDefaultOn() {
		RelayConfig config = RelayConfig.load(configDirectory, new Properties());

		assertTrue(config.cantMiss());
		assertTrue(config.autoPurchase());
		assertTrue(config.prePurchase());
		assertTrue(config.autoRefill());
		assertFalse(config.fastPlace());
		assertTrue(config.autopot());
		assertFalse(config.autopotRefill());
		assertEquals(0, config.fastPlaceDelay());
		assertTrue(config.patchcrumbs());
		assertTrue(config.patchcrumbsLabel());
		assertFalse(config.patchcrumbsTracer());
		assertFalse(config.patchcrumbsSandCheck());
		assertFalse(config.patchcrumbsAvoidCannons());
		assertEquals(12, config.patchcrumbsTimeout());
		assertEquals(2, config.patchcrumbsWidth());
		assertEquals(dev.relay.patchcrumbs.PatchCrumbsPolicy.DirectionMode.BOTH, config.patchcrumbsDirection());
		assertEquals(dev.relay.patchcrumbs.PatchCrumbsPolicy.Palette.RED, config.patchcrumbsPalette());
		assertFalse(config.patchcrumbsCallouts());
		assertTrue(config.patchcrumbsConsistentCallouts());
		assertEquals(12, config.patchcrumbsCalloutTimeout());
		assertTrue(config.patchcrumbsShareUsingFf());
		assertEquals(dev.relay.fluids.LavaVisibility.NORMAL, config.lavaVisibility());
		assertEquals(dev.relay.fluids.WaterVisibility.NORMAL, config.waterVisibility());
		assertFalse(config.clearWater());
	}

	@Test
	void placementFlagsCanBeTurnedOffAndRemembered() {
		RelayConfig saved = RelayConfig.load(configDirectory, new Properties());
		saved.setCantMiss(false);
		saved.setAutoPurchase(false);
		saved.setPrePurchase(false);
		saved.setAutoRefill(false);
		saved.setFastPlace(true);
		saved.setAutopot(false);
		saved.setAutopotRefill(true);
		saved.setFastPlaceDelay(2);
		saved.setPatchcrumbs(true);
		saved.setPatchcrumbsLabel(false);
		saved.setPatchcrumbsTracer(true);
		saved.setPatchcrumbsSandCheck(false);
		saved.setPatchcrumbsAvoidCannons(false);
		saved.setPatchcrumbsTimeout(30);
		saved.setPatchcrumbsWidth(5);
		saved.setPatchcrumbsDirection(dev.relay.patchcrumbs.PatchCrumbsPolicy.DirectionMode.AUTO);
		saved.setPatchcrumbsPalette(dev.relay.patchcrumbs.PatchCrumbsPolicy.Palette.CYAN);
		saved.setPatchcrumbsCallouts(true);
		saved.setPatchcrumbsConsistentCallouts(false);
		saved.setPatchcrumbsCalloutTimeout(20);
		saved.setPatchcrumbsShareUsingFf(false);
		saved.setLavaVisibility(dev.relay.fluids.LavaVisibility.TRANSLUCENT);
		saved.setWaterVisibility(dev.relay.fluids.WaterVisibility.CLEAR);

		RelayConfig reloaded = RelayConfig.load(configDirectory, new Properties());

		assertFalse(reloaded.cantMiss());
		assertFalse(reloaded.autoPurchase());
		assertFalse(reloaded.prePurchase());
		assertFalse(reloaded.autoRefill());
		assertTrue(reloaded.fastPlace());
		assertFalse(reloaded.autopot());
		assertTrue(reloaded.autopotRefill());
		assertEquals(2, reloaded.fastPlaceDelay());
		assertTrue(reloaded.patchcrumbs());
		assertFalse(reloaded.patchcrumbsLabel());
		assertTrue(reloaded.patchcrumbsTracer());
		assertFalse(reloaded.patchcrumbsSandCheck());
		assertFalse(reloaded.patchcrumbsAvoidCannons());
		assertEquals(30, reloaded.patchcrumbsTimeout());
		assertEquals(5, reloaded.patchcrumbsWidth());
		assertEquals(dev.relay.patchcrumbs.PatchCrumbsPolicy.DirectionMode.AUTO, reloaded.patchcrumbsDirection());
		assertEquals(dev.relay.patchcrumbs.PatchCrumbsPolicy.Palette.CYAN, reloaded.patchcrumbsPalette());
		assertTrue(reloaded.patchcrumbsCallouts());
		assertFalse(reloaded.patchcrumbsConsistentCallouts());
		assertEquals(20, reloaded.patchcrumbsCalloutTimeout());
		assertFalse(reloaded.patchcrumbsShareUsingFf());
		assertEquals(dev.relay.fluids.LavaVisibility.TRANSLUCENT, reloaded.lavaVisibility());
		assertEquals(dev.relay.fluids.WaterVisibility.CLEAR, reloaded.waterVisibility());
		assertTrue(reloaded.clearWater());
	}

	@Test
	void readsOnOffWordingForPlacementFlags() throws IOException {
		write("cantMiss=off", "autoPurchase=no", "prePurchase=on", "autoRefill=off",
				"fastPlace=yes", "fastPlaceDelay=3",
				"patchcrumbs=on", "patchcrumbsLabel=off", "patchcrumbsTracer=yes",
				"patchcrumbsSandCheck=no", "patchcrumbsAvoidCannons=off",
				"patchcrumbsTimeout=8", "patchcrumbsWidth=4",
				"patchcrumbsDirection=north/south", "patchcrumbsColor=blue",
				"patchcrumbsCallouts=yes", "patchcrumbsConsistentCallouts=off",
				"patchcrumbsCalloutTimeout=9", "patchcrumbsShareUsingFf=no",
				"lavaVisibility=transparent", "waterVisibility=clear");

		RelayConfig config = RelayConfig.load(configDirectory, new Properties());

		assertFalse(config.cantMiss());
		assertFalse(config.autoPurchase());
		assertTrue(config.prePurchase());
		assertFalse(config.autoRefill());
		assertTrue(config.fastPlace());
		assertEquals(3, config.fastPlaceDelay());
		assertTrue(config.patchcrumbs());
		assertFalse(config.patchcrumbsLabel());
		assertTrue(config.patchcrumbsTracer());
		assertFalse(config.patchcrumbsSandCheck());
		assertFalse(config.patchcrumbsAvoidCannons());
		assertEquals(8, config.patchcrumbsTimeout());
		assertEquals(4, config.patchcrumbsWidth());
		assertEquals(dev.relay.patchcrumbs.PatchCrumbsPolicy.DirectionMode.NORTH_SOUTH, config.patchcrumbsDirection());
		assertEquals(dev.relay.patchcrumbs.PatchCrumbsPolicy.Palette.BLUE, config.patchcrumbsPalette());
		assertTrue(config.patchcrumbsCallouts());
		assertFalse(config.patchcrumbsConsistentCallouts());
		assertEquals(9, config.patchcrumbsCalloutTimeout());
		assertFalse(config.patchcrumbsShareUsingFf());
		assertEquals(dev.relay.fluids.LavaVisibility.TRANSPARENT, config.lavaVisibility());
		assertEquals(dev.relay.fluids.WaterVisibility.CLEAR, config.waterVisibility());
		assertTrue(config.clearWater());
	}

	@Test
	void fastPlaceDelayStaysInsideVanillaRange() throws IOException {
		write("fastPlaceDelay=-4");
		assertEquals(0, RelayConfig.load(configDirectory, new Properties()).fastPlaceDelay());

		write("fastPlaceDelay=99");
		assertEquals(4, RelayConfig.load(configDirectory, new Properties()).fastPlaceDelay());

		write("fastPlaceDelay=nope");
		assertEquals(0, RelayConfig.load(configDirectory, new Properties()).fastPlaceDelay());
	}

	@Test
	void oldHiddenPatchcrumbsDefaultsBecomeVisible() throws IOException {
		write("patchcrumbs=false", "patchcrumbsLabel=true", "patchcrumbsTracer=false",
				"patchcrumbsSandCheck=true", "patchcrumbsAvoidCannons=true",
				"patchcrumbsTimeout=12", "patchcrumbsWidth=2");

		RelayConfig config = RelayConfig.load(configDirectory, new Properties());

		assertTrue(config.patchcrumbs());
		assertFalse(config.patchcrumbsSandCheck());
		assertFalse(config.patchcrumbsAvoidCannons());
	}

	@Test
	void turningPatchcrumbsOffOnPurposeIsRemembered() throws IOException {
		write("patchcrumbs=false", "patchcrumbsSandCheck=false", "patchcrumbsAvoidCannons=false");

		assertFalse(RelayConfig.load(configDirectory, new Properties()).patchcrumbs());
	}

	@Test
	void clearWaterIsRememberedAndFallsBack() throws IOException {
		write("clearWater=on");
		RelayConfig upgraded = RelayConfig.load(configDirectory, new Properties());
		assertTrue(upgraded.clearWater());
		assertEquals(dev.relay.fluids.WaterVisibility.CLEAR, upgraded.waterVisibility());

		write("clearWater=off");
		assertFalse(RelayConfig.load(configDirectory, new Properties()).clearWater());

		write("waterVisibility=off", "clearWater=yes");
		RelayConfig hidden = RelayConfig.load(configDirectory, new Properties());
		assertEquals(dev.relay.fluids.WaterVisibility.OFF, hidden.waterVisibility());
		assertFalse(hidden.clearWater());
	}

	@Test
	void waterVisibilityIsRememberedAndFallsBack() throws IOException {
		write("waterVisibility=off");
		assertEquals(dev.relay.fluids.WaterVisibility.OFF,
				RelayConfig.load(configDirectory, new Properties()).waterVisibility());

		write("waterVisibility=hidden");
		assertEquals(dev.relay.fluids.WaterVisibility.OFF,
				RelayConfig.load(configDirectory, new Properties()).waterVisibility());

		write("waterVisibility=clear");
		assertEquals(dev.relay.fluids.WaterVisibility.CLEAR,
				RelayConfig.load(configDirectory, new Properties()).waterVisibility());

		write("waterVisibility=nope");
		assertEquals(dev.relay.fluids.WaterVisibility.NORMAL,
				RelayConfig.load(configDirectory, new Properties()).waterVisibility());
	}

	@Test
	void lavaVisibilityIsRememberedAndFallsBack() throws IOException {
		write("lavaVisibility=translucent");
		assertEquals(dev.relay.fluids.LavaVisibility.TRANSLUCENT,
				RelayConfig.load(configDirectory, new Properties()).lavaVisibility());

		write("lavaVisibility=see-thru");
		assertEquals(dev.relay.fluids.LavaVisibility.TRANSLUCENT,
				RelayConfig.load(configDirectory, new Properties()).lavaVisibility());

		write("lavaVisibility=nope");
		assertEquals(dev.relay.fluids.LavaVisibility.NORMAL,
				RelayConfig.load(configDirectory, new Properties()).lavaVisibility());
	}

	@Test
	void pingSettingsAreRemembered() throws IOException {
		write("pingMultiple=true", "pingTimeout=0", "pingColor=rgb", "pingShare=false");
		RelayConfig low = RelayConfig.load(configDirectory, new Properties());
		assertTrue(low.pingMultiple());
		assertFalse(low.pingShare());
		assertEquals(1, low.pingTimeout());
		assertEquals(dev.relay.patchcrumbs.PatchCrumbsPolicy.Palette.RGB, low.pingPalette());

		write("pingTimeout=90", "pingColor=magenta");
		RelayConfig high = RelayConfig.load(configDirectory, new Properties());
		assertFalse(high.pingMultiple());
		assertTrue(high.pingShare());
		assertEquals(60, high.pingTimeout());
		assertEquals(dev.relay.patchcrumbs.PatchCrumbsPolicy.Palette.MAGENTA, high.pingPalette());
	}

	@Test
	void patchcrumbsNumbersStayInsideConfiguredRange() throws IOException {
		write("patchcrumbsTimeout=0", "patchcrumbsWidth=0");
		RelayConfig low = RelayConfig.load(configDirectory, new Properties());
		assertEquals(1, low.patchcrumbsTimeout());
		assertEquals(1, low.patchcrumbsWidth());

		write("patchcrumbsTimeout=99", "patchcrumbsWidth=40");
		RelayConfig high = RelayConfig.load(configDirectory, new Properties());
		assertEquals(60, high.patchcrumbsTimeout());
		assertEquals(10, high.patchcrumbsWidth());
	}

	private static Properties defaults(String host, String port) {
		Properties properties = new Properties();
		properties.setProperty("host", host);
		properties.setProperty("port", port);
		return properties;
	}

	private void write(String... lines) throws IOException {
		Files.write(configDirectory.resolve("relay.properties"), java.util.List.of(lines));
	}
}
