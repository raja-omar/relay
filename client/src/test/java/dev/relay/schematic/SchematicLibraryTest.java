package dev.relay.schematic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Listing, identifying and reading local schematics, without Minecraft: the folder is just a
 * folder, and Litematica is only how the game finds it.
 */
class SchematicLibraryTest {
	@TempDir
	Path folder;

	private SchematicLibrary library;

	@BeforeEach
	void openLibrary() {
		library = new SchematicLibrary(folder, 64);
	}

	@Test
	void missingFolderIsAnEmptyLibrary() throws SchematicException {
		assertTrue(new SchematicLibrary(folder.resolve("no-such-dir")).list().isEmpty());
	}

	@Test
	void listsOnlySchematicFiles() throws Exception {
		writeGzip("Wall.litematic", 8);
		writeGzip("keep.schem", 8);
		Files.write(folder.resolve("notes.txt"), gzip(8));
		Files.write(folder.resolve("readme.md"), "not a schematic".getBytes());

		List<SchematicFile> files = library.list();

		assertEquals(2, files.size());
		assertEquals("keep.schem", files.get(0).relativePath());
		assertEquals("Wall.litematic", files.get(1).relativePath());
	}

	@Test
	void listsSchematicsInSubfoldersAndSkipsTransmit() throws Exception {
		writeGzip("houses/Starter.litematic", 8);
		writeGzip("transmit/cached.litematic", 8);
		Files.createDirectories(folder.resolve(".hidden"));
		writeGzip(".hidden/secret.litematic", 8);

		List<SchematicFile> files = library.list();

		assertEquals(List.of("houses/Starter.litematic"), files.stream().map(SchematicFile::relativePath).toList());
	}

	@Test
	void findsByNameWithOrWithoutExtension() throws Exception {
		writeGzip("Wall.litematic", 8);

		assertEquals("Wall.litematic", library.require("Wall").relativePath());
		assertEquals("Wall.litematic", library.require("Wall.litematic").relativePath());
		assertEquals("Wall.litematic", library.require("wall").relativePath());
	}

	@Test
	void findsASchematicInASubfolder() throws Exception {
		writeGzip("houses/Starter.litematic", 8);

		assertEquals("houses/Starter.litematic", library.require("houses/Starter").relativePath());
		assertEquals("houses/Starter.litematic", library.require("houses/Starter.litematic").relativePath());
		assertEquals("houses/Starter.litematic", library.require("Starter").relativePath());
	}

	@Test
	void prefersTheExactNameWhenTwoFilesShareAStem() throws Exception {
		writeGzip("Wall.litematic", 8);
		writeGzip("builds/Wall.litematic", 8);

		assertEquals("Wall.litematic", library.require("Wall.litematic").relativePath());
		assertEquals("builds/Wall.litematic", library.require("builds/Wall").relativePath());
	}

	@Test
	void refusesANameThatMatchesMoreThanOneFile() throws Exception {
		writeGzip("Wall.litematic", 8);
		writeGzip("builds/Wall.litematic", 8);

		SchematicException error = assertThrows(SchematicException.class, () -> library.require("Wall"));

		assertTrue(error.getMessage().contains("more than one"));
	}

	@Test
	void pathTraversalDoesNotEscapeTheFolder() throws Exception {
		Path outside = folder.resolveSibling("secret.litematic");
		Files.write(outside, gzip(8));

		assertTrue(library.find("../secret").isEmpty());
		assertTrue(library.find("../secret.litematic").isEmpty());
	}

	@Test
	void missingNameIsEmptyThenRequireExplains() throws SchematicException {
		assertTrue(library.find("Wall").isEmpty());

		SchematicException error = assertThrows(SchematicException.class, () -> library.require("Wall"));

		assertEquals("Schematic could not be found.", error.getMessage());
	}

	@Test
	void blankNameIsRejected() {
		SchematicException error = assertThrows(SchematicException.class, () -> library.find("   "));

		assertEquals("Give the name of a schematic.", error.getMessage());
	}

	@Test
	void readsTheBytesThatWereWritten() throws Exception {
		byte[] contents = gzip(12);
		Files.write(folder.resolve("Wall.litematic"), contents);

		assertArrayEquals(contents, library.read("Wall"));
	}

	@Test
	void rejectsAnEmptyFile() throws Exception {
		Files.write(folder.resolve("Empty.litematic"), new byte[0]);

		SchematicException error = assertThrows(SchematicException.class, () -> library.read("Empty"));

		assertEquals("That schematic is empty.", error.getMessage());
	}

	@Test
	void rejectsAFileThatIsNotCompressedNbt() throws Exception {
		Files.write(folder.resolve("Fake.litematic"), "hello".getBytes());

		SchematicException error = assertThrows(SchematicException.class, () -> library.read("Fake"));

		assertEquals("That file is not a usable schematic.", error.getMessage());
	}

	@Test
	void rejectsAFileOverTheSizeLimit() throws Exception {
		// Zeros gzip to almost nothing, so the file on disk has to be large, not the uncompressed
		// payload. A gzip header plus padding is enough to go over the 64-byte test ceiling.
		byte[] contents = new byte[80];
		contents[0] = 0x1F;
		contents[1] = (byte) 0x8B;
		Files.write(folder.resolve("Huge.litematic"), contents);

		SchematicException error = assertThrows(SchematicException.class, () -> library.read("Huge"));

		assertEquals("Schematic is too large to share.", error.getMessage());
	}

	@Test
	void acceptsUncompressedNbtAsAVanillaStructure() throws Exception {
		byte[] nbt = { 0x0A, 0x00, 0x00 };
		Files.write(folder.resolve("Village.nbt"), nbt);

		assertArrayEquals(nbt, library.read("Village"));
	}

	@Test
	void sizeLabelUsesTheSameUnitsTheChatWillShow() throws Exception {
		assertEquals("8 B", new SchematicFile(folder, folder.resolve("tiny.litematic"), 8).sizeLabel());
		assertEquals("1.5 KB", new SchematicFile(folder, folder.resolve("small.litematic"), 1536).sizeLabel());
		assertEquals("2.4 MB", new SchematicFile(folder, folder.resolve("big.litematic"), 2_516_582).sizeLabel());
	}

	@Test
	void gzipMagicIsTheCheckForALItematic() {
		assertTrue(SchematicLibrary.looksLikeSchematic(new byte[] { 0x1F, (byte) 0x8B }));
		assertTrue(SchematicLibrary.looksLikeSchematic(new byte[] { 0x0A, 0x00 }));
		assertFalse(SchematicLibrary.looksLikeSchematic(new byte[] { 'P', 'K' }));
		assertFalse(SchematicLibrary.looksLikeSchematic(new byte[0]));
	}

	private void writeGzip(String relativePath, int uncompressedBytes) throws IOException {
		Path file = folder.resolve(relativePath);
		Files.createDirectories(file.getParent());
		Files.write(file, gzip(uncompressedBytes));
	}

	private static byte[] gzip(int uncompressedBytes) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();

		try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
			gzip.write(new byte[uncompressedBytes]);
		}

		return bytes.toByteArray();
	}
}
