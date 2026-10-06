package dev.relay.schematic;

import java.nio.file.Path;
import java.util.Locale;

/**
 * One schematic on disk, as this mod sees it: a path under Litematica's schematics folder and how
 * big it is.
 *
 * <p>The name a player types is {@link #relativePath()} or the filename without the extension.
 * Either is enough when it only matches one file.
 */
public record SchematicFile(Path directory, Path path, long sizeBytes) {
	public String fileName() {
		return path.getFileName().toString();
	}

	/**
	 * Path relative to the schematics folder, always with {@code /}, so the same name works on every
	 * operating system.
	 */
	public String relativePath() {
		return directory.relativize(path).toString().replace('\\', '/');
	}

	/** {@link #relativePath()} without a known schematic extension. */
	public String stem() {
		return SchematicLibrary.stripExtension(relativePath());
	}

	public String sizeLabel() {
		if (sizeBytes < 1024) {
			return sizeBytes + " B";
		}

		if (sizeBytes < 1024 * 1024) {
			return String.format(Locale.US, "%.1f KB", sizeBytes / 1024.0);
		}

		return String.format(Locale.US, "%.1f MB", sizeBytes / (1024.0 * 1024.0));
	}
}
