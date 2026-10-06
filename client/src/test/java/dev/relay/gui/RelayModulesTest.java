package dev.relay.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class RelayModulesTest {
	@Test
	void navigationIsFeatureModulesThenUtility() {
		List<String> ids = RelayModules.navigation().stream().map(RelayModule::id).toList();
		assertEquals(List.of(
				"litematica", "fastplace", "patchcrumbs", "fluids", "miscellaneous", "combat", "groups", "hotkeys"), ids);
	}

	@Test
	void groupsAndHotkeysSitInTheUtilitySection() {
		assertTrue(RelayModules.groups().utility());
		assertTrue(RelayModules.hotkeys().utility());
		assertFalse(RelayModules.litematica().utility());
		assertFalse(RelayModules.fastPlace().utility());
		assertFalse(RelayModules.patchcrumbs().utility());
		assertFalse(RelayModules.fluids().utility());
		assertFalse(RelayModules.miscellaneous().utility());
		assertFalse(RelayModules.combat().utility());
	}

	@Test
	void patchcrumbsIsAFeatureTab() {
		RelayModule patchcrumbs = RelayModules.patchcrumbs();
		assertEquals("Patchcrumbs", patchcrumbs.name());
		assertTrue(patchcrumbs.available());
		assertEquals("patchcrumbs", patchcrumbs.id());
		assertEquals("Mark the last cannon shot so you can patch the wall.", patchcrumbs.description());
		assertEquals(List.of("Toggle Patchcrumbs", "Callout Shot"),
				patchcrumbs.hotkeys().stream().map(HotkeyBinding::action).toList());
	}

	@Test
	void fastPlaceIsAWorkingFeatureModule() {
		RelayModule fastPlace = RelayModules.fastPlace();
		assertEquals("Fast Place", fastPlace.name());
		assertTrue(fastPlace.available());
		assertEquals("fastplace", fastPlace.id());
	}

	@Test
	void combatIsAFeatureTab() {
		RelayModule combat = RelayModules.combat();
		assertEquals("Combat", combat.name());
		assertTrue(combat.available());
		assertEquals("combat", combat.id());
		assertEquals("", combat.description());
		assertEquals(List.of("Autpot"), combat.hotkeys().stream().map(HotkeyBinding::action).toList());
	}

	@Test
	void fluidsIsAFeatureTab() {
		RelayModule fluids = RelayModules.fluids();
		assertEquals("Fluids", fluids.name());
		assertTrue(fluids.available());
		assertEquals("fluids", fluids.id());
		assertEquals("Hide lava or water, make lava see-through, or swim without underwater fog.", fluids.description());
		assertEquals(List.of("Cycle Lava"),
				fluids.hotkeys().stream().map(HotkeyBinding::action).toList());
	}

	@Test
	void hotkeysAreCollectedFromModulesRatherThanTheShell() {
		List<String> modules = RelayModules.allHotkeys().stream().map(HotkeyBinding::moduleName).distinct().toList();
		assertEquals(List.of(
				"Relay", "Litematica", "Fast Place", "Patchcrumbs", "Fluids", "Miscellaneous", "Combat"), modules);

		List<String> litematica = RelayModules.litematica().hotkeys().stream()
				.map(HotkeyBinding::action)
				.toList();
		assertEquals(List.of(
				"Toggle Easy Place",
				"Toggle Cant Miss",
				"Toggle Auto Purchase",
				"Toggle Pre-purchase",
				"Toggle Auto Refill"), litematica);

		List<String> fastPlace = RelayModules.fastPlace().hotkeys().stream()
				.map(HotkeyBinding::action)
				.toList();
		assertEquals(List.of("Toggle Fast Place"), fastPlace);

		List<String> patchcrumbs = RelayModules.patchcrumbs().hotkeys().stream()
				.map(HotkeyBinding::action)
				.toList();
		assertEquals(List.of("Toggle Patchcrumbs", "Callout Shot"), patchcrumbs);

		List<String> fluids = RelayModules.fluids().hotkeys().stream()
				.map(HotkeyBinding::action)
				.toList();
		assertEquals(List.of("Cycle Lava"), fluids);

		List<String> miscellaneous = RelayModules.miscellaneous().hotkeys().stream()
				.map(HotkeyBinding::action)
				.toList();
		assertEquals(List.of("Ping Block"), miscellaneous);

		List<String> combat = RelayModules.combat().hotkeys().stream()
				.map(HotkeyBinding::action)
				.toList();
		assertEquals(List.of("Autpot"), combat);
	}

	@Test
	void unknownIdsFallBackToLitematica() {
		assertEquals("litematica", RelayModules.byId("nope").id());
	}
}
