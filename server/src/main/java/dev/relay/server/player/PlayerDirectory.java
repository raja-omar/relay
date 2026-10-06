package dev.relay.server.player;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import dev.relay.common.AccessTokens;
import dev.relay.common.PlayerNames;

/**
 * Who may sign in, and who they are once they do.
 *
 * <p>Groups stay in memory and vanish on restart. Player ids cannot: an issued id has to keep
 * meaning the same person after the process comes back. The file stores a hash of each id, the
 * name the operator chose, and a stable UUID used as the session key.
 */
public final class PlayerDirectory {
	private final Path file;
	private final Map<String, Entry> byFingerprint = new HashMap<>();
	private final Map<String, String> fingerprintByName = new HashMap<>();

	private PlayerDirectory(Path file) {
		this.file = file;
	}

	/** An empty directory that is never written. Tests use this. */
	public static PlayerDirectory inMemory() {
		return new PlayerDirectory(null);
	}

	public static PlayerDirectory load(Path file) throws IOException {
		PlayerDirectory directory = new PlayerDirectory(file);

		if (Files.isRegularFile(file)) {
			directory.readFromDisk();
		}

		return directory;
	}

	/**
	 * Issues an id for {@code playerName}. A second call for the same name (any case) keeps the
	 * player UUID so groups still recognise them, and kills the previous id.
	 */
	public synchronized Issued issue(String playerName) throws IOException {
		reloadQuietly();

		if (!PlayerNames.isValid(playerName)) {
			throw new IllegalArgumentException("\"" + playerName + "\" is not a usable player name. "
					+ "Use Minecraft's alphabet: letters, digits and underscore, up to "
					+ PlayerNames.MAX_LENGTH + " characters.");
		}

		String nameKey = key(playerName);
		String previousFingerprint = fingerprintByName.get(nameKey);
		Entry previous = previousFingerprint == null ? null : byFingerprint.get(previousFingerprint);
		UUID playerId = previous == null ? UUID.randomUUID() : previous.playerId();

		if (previousFingerprint != null) {
			byFingerprint.remove(previousFingerprint);
		}

		String token = AccessTokens.issue();
		String fingerprint = AccessTokens.fingerprint(token);
		Entry entry = new Entry(playerId, playerName);

		byFingerprint.put(fingerprint, entry);
		fingerprintByName.put(nameKey, fingerprint);
		save();

		return new Issued(token, entry, previous != null);
	}

	public synchronized boolean revoke(String playerName) throws IOException {
		reloadQuietly();
		String fingerprint = fingerprintByName.remove(key(playerName));

		if (fingerprint == null) {
			return false;
		}

		byFingerprint.remove(fingerprint);
		save();
		return true;
	}

	/** The player this id belongs to, or empty when the id is unknown or malformed. */
	public synchronized Optional<Entry> authenticate(String token) {
		reloadQuietly();

		if (!AccessTokens.isWellFormed(token)) {
			return Optional.empty();
		}

		return Optional.ofNullable(byFingerprint.get(AccessTokens.fingerprint(token)));
	}

	public synchronized List<Entry> list() {
		List<Entry> entries = new ArrayList<>(byFingerprint.values());
		entries.sort(Comparator.comparing(entry -> entry.playerName().toLowerCase(Locale.ROOT)));
		return List.copyOf(entries);
	}

	public synchronized int size() {
		return byFingerprint.size();
	}

	/**
	 * Picks up invites written by another process (the {@code invite} command) without a restart.
	 * An unreadable file is left alone so a bad edit does not lock everyone out mid-session.
	 */
	private void reloadQuietly() {
		if (file == null) {
			return;
		}

		try {
			PlayerDirectory latest = load(file);
			byFingerprint.clear();
			byFingerprint.putAll(latest.byFingerprint);
			fingerprintByName.clear();
			fingerprintByName.putAll(latest.fingerprintByName);
		} catch (IOException ignored) {
			// Keep the copy we already have.
		}
	}

	private void readFromDisk() throws IOException {
		List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);

		for (int index = 0; index < lines.size(); index++) {
			String line = lines.get(index).trim();

			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}

			String[] parts = line.split(" ", 3);

			if (parts.length != 3) {
				throw new IOException(file + " line " + (index + 1) + " is not hash, id, name");
			}

			String fingerprint = parts[0].toLowerCase(Locale.ROOT);
			UUID playerId;

			try {
				playerId = UUID.fromString(parts[1]);
			} catch (IllegalArgumentException badId) {
				throw new IOException(file + " line " + (index + 1) + " has a bad player id", badId);
			}

			String playerName = parts[2].trim();

			if (fingerprint.length() != 64 || !PlayerNames.isValid(playerName)) {
				throw new IOException(file + " line " + (index + 1) + " is not a usable player");
			}

			Entry entry = new Entry(playerId, playerName);
			byFingerprint.put(fingerprint, entry);
			fingerprintByName.put(key(playerName), fingerprint);
		}
	}

	private void save() throws IOException {
		if (file == null) {
			return;
		}

		StringBuilder text = new StringBuilder();
		text.append("# Relay player allowlist. The id itself is never stored.\n");
		text.append("# hash player-id name\n");

		for (Map.Entry<String, Entry> row : byFingerprint.entrySet()) {
			Entry entry = row.getValue();
			text.append(row.getKey()).append(' ')
					.append(entry.playerId()).append(' ')
					.append(entry.playerName()).append('\n');
		}

		Path parent = file.getParent();

		if (parent != null) {
			Files.createDirectories(parent);
		}

		Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
		Files.writeString(temporary, text, StandardCharsets.UTF_8);

		try {
			Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException notAtomic) {
			Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static String key(String playerName) {
		return playerName.toLowerCase(Locale.ROOT);
	}

	public record Entry(UUID playerId, String playerName) {
	}

	public record Issued(String token, Entry player, boolean replaced) {
		public UUID playerId() {
			return player.playerId();
		}

		public String playerName() {
			return player.playerName();
		}
	}
}
