package minstrel.server.librarian;

import minstrel.server.Json;
import minstrel.server.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static minstrel.server.TestFixtures.librarian;
import static org.junit.jupiter.api.Assertions.*;

class LibrarianImplTest {

    @TempDir
    File tmp;

    private File dataRoot;

    @BeforeEach
    void setUp() throws IOException {
        dataRoot = new File(tmp, "data");
        dataRoot.mkdirs();
        File lib = new File(dataRoot, "minstrel");
        lib.mkdirs();
        TestFixtures.createEpub(lib);
        TestFixtures.createCbz(lib);
        TestFixtures.createAbz(lib);
    }

    private Map<String, Object> scanArgs() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("paths", List.of("data/minstrel"));
        args.put("recursive", true);
        args.put("ignoreHidden", true);
        args.put("thumbnailDirectory", "data/minstrel/.thumbnails");
        args.put("thumbnailWidth", 300);
        args.put("thumbnailHeight", 400);
        Map<String, Object> formats = new LinkedHashMap<>();
        formats.put("epub", List.of(".epub", ".epub.zip"));
        formats.put("cbz", List.of(".cbz", ".cbz.zip"));
        formats.put("abz", List.of(".abz", ".abz.zip"));
        args.put("formats", formats);
        return args;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> publications(String json) {
        Map<String, Object> obj = (Map<String, Object>) Json.parse(json);
        Map<String, Object> pubs = (Map<String, Object>) obj.get("publications");
        return (List<Map<String, Object>>) pubs.get("items");
    }

    @Test
    void fullScanFindsAllFormats() throws IOException {
        String json = LibrarianImpl.handle(dataRoot, librarian("full", "", scanArgs()));
        List<Map<String, Object>> items = publications(json);
        assertEquals(3, items.size());

        Map<String, Object> epub = find(items, "epub");
        assertEquals("Test Book", epub.get("title"));
        Map<String, Object> epubMeta = (Map<String, Object>) meta(epub, "epub");
        assertEquals("Jane Doe", epubMeta.get("author"));
        assertEquals("en", epubMeta.get("language"));
        assertEquals("Narrator One", epubMeta.get("narrator"));
        assertEquals("0:10:00", epubMeta.get("duration"));
        assertEquals("Series X", epubMeta.get("series"));
        assertEquals("2", epubMeta.get("seriesIndex"));
        assertTrue(((String) epub.get("absolutePath")).startsWith("data/minstrel/"));

        Map<String, Object> cbz = find(items, "cbz");
        assertEquals("Sample Comic", cbz.get("title"));
        assertEquals("3", meta(cbz, "cbz").get("numberOfAssets"));
        assertFalse(((String) cbz.get("absolutePathThumbnail")).isEmpty());

        Map<String, Object> abz = find(items, "abz");
        assertEquals("Sample Audiobook", abz.get("title"));
        assertEquals("playlist.m3u", meta(abz, "abz").get("internalPathPlaylist"));
        assertFalse(((String) abz.get("absolutePathThumbnail")).isEmpty());
    }

    @Test
    void scanGeneratesThumbnailsOnDisk() throws IOException {
        LibrarianImpl.handle(dataRoot, librarian("full", "", scanArgs()));
        File thumbDir = new File(dataRoot, "minstrel/.thumbnails");
        assertTrue(thumbDir.isDirectory());
        File[] thumbs = thumbDir.listFiles((d, n) -> n.endsWith(".png"));
        assertNotNull(thumbs);
        assertTrue(thumbs.length >= 2, "expected thumbnails for cbz/abz");
        assertTrue(new File(thumbDir, ".nomedia").exists());
    }

    @Test
    void singleModeParsesOneEpub() throws IOException {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("format", "epub");
        args.put("thumbnailDirectory", "data/minstrel/.thumbnails");
        args.put("thumbnailWidth", 300);
        args.put("thumbnailHeight", 400);
        String json = LibrarianImpl.handle(dataRoot, librarian("single", "data/minstrel/sample.epub", args));
        List<Map<String, Object>> items = publications(json);
        assertEquals(1, items.size());
        assertEquals("Test Book", items.get(0).get("title"));
    }

    @Test
    void customAbzReturnsPlaylistTracks() throws IOException {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("format", "abz");
        args.put("internalPathPlaylist", "playlist.m3u");
        args.put("command", "getSortedListOfAssets");
        String json = LibrarianImpl.handle(dataRoot, librarian("custom", "data/minstrel/sample.abz", args));
        Map<String, Object> obj = (Map<String, Object>) Json.parse(json);
        Map<String, Object> assets = (Map<String, Object>) obj.get("assets");
        List<Map<String, Object>> items = (List<Map<String, Object>>) assets.get("items");
        assertEquals(2, items.size());
        Map<String, Object> first = items.get(0);
        assertEquals("track01.wav", first.get("path"));
        assertEquals("2", ((Map<?, ?>) first.get("metadata")).get("duration"));
        assertEquals("Track One", ((Map<?, ?>) first.get("metadata")).get("title"));
    }

    @Test
    void customCbzReturnsImages() throws IOException {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("format", "cbz");
        args.put("command", "getSortedListOfAssets");
        String json = LibrarianImpl.handle(dataRoot, librarian("custom", "data/minstrel/sample.cbz", args));
        Map<String, Object> obj = (Map<String, Object>) Json.parse(json);
        Map<String, Object> assets = (Map<String, Object>) obj.get("assets");
        List<Map<String, Object>> items = (List<Map<String, Object>>) assets.get("items");
        assertEquals(3, items.size());
        assertEquals("cover.png", items.get(0).get("path"));
    }

    @Test
    void createThumbnailDirectoryCreatesDir() throws IOException {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("thumbnailDirectory", "data/minstrel/.thumbnails");
        LibrarianImpl.handle(dataRoot, librarian("createThumbnailDirectory", "", args));
        assertTrue(new File(dataRoot, "minstrel/.thumbnails").isDirectory());
        assertTrue(new File(dataRoot, "minstrel/.thumbnails/.nomedia").exists());
    }

    @Test
    void emptyThumbnailDirectoryClearsContents() throws IOException {
        File thumbDir = new File(dataRoot, "minstrel/.thumbnails");
        thumbDir.mkdirs();
        assertTrue(new File(thumbDir, "old.png").createNewFile());
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("thumbnailDirectory", "data/minstrel/.thumbnails");
        LibrarianImpl.handle(dataRoot, librarian("emptyThumbnailDirectory", "", args));
        File[] files = thumbDir.listFiles();
        assertNotNull(files);
        for (File f : files) {
            assertNotEquals("old.png", f.getName());
        }
    }

    private Map<String, Object> find(List<Map<String, Object>> items, String mainFormat) {
        for (Map<String, Object> item : items) {
            if (mainFormat.equals(item.get("mainFormat"))) {
                return item;
            }
        }
        throw new AssertionError("no item with format " + mainFormat);
    }

    private Map<String, Object> meta(Map<String, Object> item, String format) {
        Map<String, Object> formats = (Map<String, Object>) item.get("formats");
        Map<String, Object> f = (Map<String, Object>) formats.get(format);
        return (Map<String, Object>) f.get("metadata");
    }
}