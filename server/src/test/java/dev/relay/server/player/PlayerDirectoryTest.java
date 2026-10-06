package dev.relay.server.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlayerDirectoryTest {
	@Test
	void issuesAnIdThatSignsThatPlayerIn() throws IOException {
		PlayerDirectory directory = PlayerDirectory.inMemory();
		PlayerDirectory.Issued issued = directory.issue("Alice");

		assertEquals("Alice", issued.playerName());
		assertFalse(issued.replaced());
		assertEquals("Alice", directory.authenticate(issued.token()).orElseThrow().playerName());
		assertTrue(directory.authenticate("rly_0000-0000-0000-0000").isEmpty());
		assertTrue(directory.authenticate("not-an-id").isEmpty());
	}

	@Test
	void reissueKeepsThePlayerAndKillsTheOldId() throws IOException {
		PlayerDirectory directory = PlayerDirectory.inMemory();
		PlayerDirectory.Issued first = directory.issue("Alice");
		PlayerDirectory.Issued second = directory.issue("alice");

		assertTrue(second.replaced());
		assertEquals(first.playerId(), second.playerId());
		assertNotEquals(first.token(), second.token());
		assertTrue(directory.authenticate(first.token()).isEmpty());
		assertEquals("alice", directory.authenticate(second.token()).orElseThrow().playerName());
		assertEquals(1, directory.size());
	}

	@Test
	void refusesANameItWouldNotWantToPrint() {
		assertThrows(IllegalArgumentException.class, () -> PlayerDirectory.inMemory().issue("§cAdmin"));
	}

	@Test
	void revokeForgetsTheId() throws IOException {
		PlayerDirectory directory = PlayerDirectory.inMemory();
		PlayerDirectory.Issued issued = directory.issue("Alice");

		assertTrue(directory.revoke("alice"));
		assertFalse(directory.revoke("Alice"));
		assertTrue(directory.authenticate(issued.token()).isEmpty());
		assertEquals(0, directory.size());
	}

	@Test
	void survivesARestartFromTheFile(@TempDir Path folder) throws IOException {
		Path file = folder.resolve("players");
		PlayerDirectory written = PlayerDirectory.load(file);
		PlayerDirectory.Issued alice = written.issue("Alice");
		PlayerDirectory.Issued beta = written.issue("Beta");

		PlayerDirectory reloaded = PlayerDirectory.load(file);

		assertEquals(alice.playerId(), reloaded.authenticate(alice.token()).orElseThrow().playerId());
		assertEquals("Beta", reloaded.authenticate(beta.token()).orElseThrow().playerName());
		assertEquals(List.of("Alice", "Beta"), reloaded.list().stream()
				.map(PlayerDirectory.Entry::playerName).toList());
	}

	@Test
	void aMissingFileIsAnEmptyDirectory(@TempDir Path folder) throws IOException {
		PlayerDirectory directory = PlayerDirectory.load(folder.resolve("nobody"));

		assertEquals(0, directory.size());
		assertFalse(Files.exists(folder.resolve("nobody")));
	}

	@Test
	void complainsAboutACorruptFile(@TempDir Path folder) throws IOException {
		Path file = folder.resolve("players");
		Files.writeString(file, "not-a-player-line\n");

		assertThrows(IOException.class, () -> PlayerDirectory.load(file));
	}

	@Test
	void aRunningCopySeesInvitesWrittenByAnotherProcess(@TempDir Path folder) throws IOException {
		Path file = folder.resolve("players");
		PlayerDirectory running = PlayerDirectory.load(file);
		PlayerDirectory admin = PlayerDirectory.load(file);
		PlayerDirectory.Issued issued = admin.issue("Alice");

		assertEquals("Alice", running.authenticate(issued.token()).orElseThrow().playerName());
	}

	@Test
	void twoNamesAreTwoPlayers() throws IOException {
		PlayerDirectory directory = PlayerDirectory.inMemory();
		UUID alice = directory.issue("Alice").playerId();
		UUID beta = directory.issue("Beta").playerId();

		assertNotEquals(alice, beta);
		assertEquals(2, directory.size());
	}
}
