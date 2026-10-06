package dev.relay.schematic;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import dev.relay.common.SchematicLimits;

/**
 * The schematics in one folder, which is Litematica's folder when this is used from the game.
 *
 * <p>Listing and reading are plain filesystem work: Litematica is only asked where the folder is.
 * Nothing here talks to the schematic server, and nothing is written.
 */
public final class SchematicLibrary {
	/** @see SchematicLimits#MAX_BYTES */
	public static final int MAX_BYTES = SchematicLimits.MAX_BYTES;

	private static final int MAX_WALK_DEPTH = 8;
	private static final String[] EXTENSIONS = { ".litematic", ".schematic", ".schem", ".nbt" };

	private final Path directory;
	private final int maxBytes;

	public SchematicLibrary(Path directory) {
		this(directory, MAX_BYTES);
	}

	/** Tests use a smaller ceiling so they do not have to write a 16 MiB file. */
	SchematicLibrary(Path directory, int maxBytes) {
		this.directory = directory.toAbsolutePath().normalize();
		this.maxBytes = maxBytes;
	}

	public Path directory() {
		return directory;
	}

	/**
	 * Every schematic under the folder, sorted by relative path. Missing folders are empty, not an
	 * error: Litematica creates this directory the first time it needs it.
	 */
	public List<SchematicFile> list() throws SchematicException {
		if (!Files.isDirectory(directory)) {
			return List.of();
		}

		List<SchematicFile> files = new ArrayList<>();

		try {
			Files.walkFileTree(directory, Set.of(), MAX_WALK_DEPTH, new SimpleFileVisitor<>() {
				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) {
					if (dir.equals(directory)) {
						return FileVisitResult.CONTINUE;
					}

					String name = dir.getFileName().toString();

					if (name.startsWith(".") || name.equals("transmit")) {
						return FileVisitResult.SKIP_SUBTREE;
					}

					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
					if (attributes.isRegularFile() && isSchematicName(file.getFileName().toString())) {
						files.add(new SchematicFile(directory, file.toAbsolutePath().normalize(), attributes.size()));
					}

					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException unreadable) {
			throw new SchematicException("Could not read the Litematica schematics folder.", unreadable);
		}

		files.sort(Comparator.comparing(SchematicFile::relativePath, String.CASE_INSENSITIVE_ORDER));
		return List.copyOf(files);
	}

	/**
	 * The one schematic that matches {@code query}, or empty when nothing does.
	 *
	 * <p>More specific names win: a relative path beats a filename, which beats a name without its
	 * extension. That is how {@code Wall.litematic} still works when {@code builds/Wall.litematic}
	 * is also there.
	 *
	 * @throws SchematicException when the name matches more than one file
	 */
	public Optional<SchematicFile> find(String query) throws SchematicException {
		String wanted = query == null ? "" : query.trim().replace('\\', '/');

		if (wanted.isEmpty()) {
			throw new SchematicException("Give the name of a schematic.");
		}

		List<SchematicFile> files = list();
		String quoted = query.trim();

		SchematicFile match = pick(files, quoted, file -> file.relativePath().equals(wanted));

		if (match == null) {
			match = pick(files, quoted, file -> file.fileName().equals(wanted));
		}

		if (match == null) {
			match = pick(files, quoted, file -> file.stem().equals(wanted)
					|| stripExtension(file.fileName()).equals(wanted));
		}

		if (match == null) {
			match = pick(files, quoted, file -> file.relativePath().equalsIgnoreCase(wanted));
		}

		if (match == null) {
			match = pick(files, quoted, file -> file.fileName().equalsIgnoreCase(wanted));
		}

		if (match == null) {
			match = pick(files, quoted, file -> file.stem().equalsIgnoreCase(wanted)
					|| stripExtension(file.fileName()).equalsIgnoreCase(wanted));
		}

		return Optional.ofNullable(match);
	}

	/** {@link #find(String)} that fails with the wording the player sees for a missing file. */
	public SchematicFile require(String query) throws SchematicException {
		return find(query).orElseThrow(() -> new SchematicException("Schematic could not be found."));
	}

	/**
	 * Checks that the file is still there, is not empty, fits in memory, and looks like a schematic.
	 * Does not load the whole file.
	 */
	public void validate(SchematicFile file) throws SchematicException {
		if (!isSchematicName(file.fileName())) {
			throw new SchematicException("That file is not a usable schematic.");
		}

		if (!Files.isRegularFile(file.path())) {
			throw new SchematicException("Schematic could not be found.");
		}

		long size;

		try {
			size = Files.size(file.path());
		} catch (IOException unreadable) {
			throw new SchematicException("Could not read that schematic.", unreadable);
		}

		if (size == 0) {
			throw new SchematicException("That schematic is empty.");
		}

		if (size > maxBytes) {
			throw new SchematicException("Schematic is too large to share.");
		}

		try (InputStream in = Files.newInputStream(file.path())) {
			byte[] header = in.readNBytes(2);

			if (!looksLikeSchematic(header)) {
				throw new SchematicException("That file is not a usable schematic.");
			}
		} catch (IOException unreadable) {
			throw new SchematicException("Could not read that schematic.", unreadable);
		}
	}

	public byte[] read(String query) throws SchematicException {
		return read(require(query));
	}

	public byte[] read(SchematicFile file) throws SchematicException {
		validate(file);

		try {
			byte[] bytes = Files.readAllBytes(file.path());

			if (bytes.length > maxBytes) {
				throw new SchematicException("Schematic is too large to share.");
			}

			return bytes;
		} catch (IOException unreadable) {
			throw new SchematicException("Could not read that schematic.", unreadable);
		}
	}

	static boolean isSchematicName(String fileName) {
		String lower = fileName.toLowerCase(Locale.ROOT);

		for (String extension : EXTENSIONS) {
			if (lower.endsWith(extension)) {
				return true;
			}
		}

		return false;
	}

	static String stripExtension(String name) {
		String lower = name.toLowerCase(Locale.ROOT);

		for (String extension : EXTENSIONS) {
			if (lower.endsWith(extension)) {
				return name.substring(0, name.length() - extension.length());
			}
		}

		return name;
	}

	private static SchematicFile pick(List<SchematicFile> files, String query, Predicate<SchematicFile> test)
			throws SchematicException {
		SchematicFile found = null;

		for (SchematicFile file : files) {
			if (!test.test(file)) {
				continue;
			}

			if (found != null) {
				throw new SchematicException("\"" + query + "\" matches more than one schematic. Be more specific.");
			}

			found = file;
		}

		return found;
	}

	/**
	 * Litematica's own formats are gzip-compressed NBT ({@code 1f 8b}) or a raw NBT compound
	 * ({@code 0a}). Anything else is not a schematic we should hand to someone else.
	 */
	static boolean looksLikeSchematic(byte[] header) {
		if (header.length < 2) {
			return header.length >= 1 && header[0] == 0x0A;
		}

		return (header[0] == 0x1F && header[1] == (byte) 0x8B) || header[0] == 0x0A;
	}
}
