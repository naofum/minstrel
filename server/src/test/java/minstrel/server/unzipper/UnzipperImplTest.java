package minstrel.server.unzipper;

import minstrel.server.Json;
import minstrel.server.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static minstrel.server.TestFixtures.unzipper;
import static org.junit.jupiter.api.Assertions.*;

class UnzipperImplTest {

    @TempDir
    File tmp;

    private File dataRoot() throws IOException {
        File d = new File(tmp, "data");
        d.mkdirs();
        new File(d, "minstrel").mkdirs();
        return d;
    }

    private Map<String, Object> body(String mode, String src, String dst, Map<String, Object> args) throws IOException {
        File data = dataRoot();
        return unzipper(mode, src, dst, args);
    }

    private File src(File data, String name) throws IOException {
        File f = TestFixtures.createEpub(data);
        File named = new File(data, name);
        Files.move(f.toPath(), named.toPath());
        return named;
    }

    @SuppressWarnings("unchecked")
    private List<String> items(String json) {
        Map<String, Object> obj = (Map<String, Object>) Json.parse(json);
        Map<String, Object> files = (Map<String, Object>) obj.get("files");
        return (List<String>) files.get("items");
    }

    @Test
    void listReturnsSortedEntries() throws IOException {
        File data = dataRoot();
        File epub = src(data, "book.epub");
        String json = UnzipperImpl.handle(data, body("list", "data/" + epub.getName(), "", new LinkedHashMap<>()));
        List<String> items = items(json);
        assertTrue(items.contains("META-INF/container.xml"));
        assertTrue(items.contains("OEBPS/content.opf"));
        assertTrue(items.contains("OEBPS/cover.png"));
    }

    @Test
    void unzipAllExtractsEverything() throws IOException {
        File data = dataRoot();
        File epub = src(data, "book.epub");
        String json = UnzipperImpl.handle(data, body("all", "data/" + epub.getName(), "data/minstrel/tmp/book", new LinkedHashMap<>()));
        List<String> items = items(json);
        assertTrue(items.contains("OEBPS/content.opf"));
        assertTrue(items.contains("OEBPS/cover.png"));
        assertTrue(Files.exists(new File(data, "minstrel/tmp/book/OEBPS/Text/ch1.xhtml").toPath()));
    }

    @Test
    void unzipAllStructureExtractsOnlyStructure() throws IOException {
        File data = dataRoot();
        File epub = src(data, "book.epub");
        String json = UnzipperImpl.handle(data, body("allStructure", "data/" + epub.getName(), "data/minstrel/tmp/book", new LinkedHashMap<>()));
        List<String> items = items(json);
        assertTrue(items.contains("META-INF/container.xml"));
        assertTrue(items.contains("OEBPS/content.opf"));
        assertFalse(items.contains("OEBPS/cover.png"));
        assertFalse(Files.exists(new File(data, "minstrel/tmp/book/OEBPS/cover.png").toPath()));
    }

    @Test
    void unzipAllNonMediaExcludesAudio() throws IOException {
        File data = dataRoot();
        File abz = TestFixtures.createAbz(data);
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("excludeExtensions", List.of(".aac", ".flac", ".m4a", ".mp3", ".oga", ".ogg", ".wav", ".webm"));
        String json = UnzipperImpl.handle(data, body("allNonMedia", "data/sample.abz", "data/minstrel/tmp/abz", args));
        List<String> items = items(json);
        assertTrue(items.contains("playlist.m3u"));
        assertFalse(items.contains("track01.wav"));
        assertFalse(Files.exists(new File(data, "minstrel/tmp/abz/track01.wav").toPath()));
    }

    @Test
    void unzipSelectedExtractsOnlyRequested() throws IOException {
        File data = dataRoot();
        File epub = src(data, "book.epub");
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("entries", List.of("OEBPS/content.opf"));
        String json = UnzipperImpl.handle(data, body("selected", "data/" + epub.getName(), "data/minstrel/tmp/book", args));
        List<String> items = items(json);
        assertTrue(items.contains("OEBPS/content.opf"));
        assertFalse(items.contains("META-INF/container.xml"));
    }

    @Test
    void traversalEntriesAreRejected() throws IOException {
        File data = dataRoot();
        File evil = TestFixtures.createZipWithTraversal(data);
        Map<String, Object> args = new LinkedHashMap<>();
        String json = UnzipperImpl.handle(data, body("all", "data/evil.zip", "data/minstrel/tmp/evil", args));
        // the entry name is "../escape.txt"; extraction must not write outside dst
        assertFalse(Files.exists(new File(data, "escape.txt").toPath()));
        assertFalse(Files.exists(new File(tmp, "escape.txt").toPath()));
    }

    @Test
    void missingSourceReturnsEmpty() throws IOException {
        File data = dataRoot();
        String json = UnzipperImpl.handle(data, body("all", "data/nothere.zip", "data/minstrel/tmp/x", new LinkedHashMap<>()));
        assertTrue(items(json).isEmpty());
    }
}