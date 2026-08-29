package minstrel.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class HttpUtilTest {

    @TempDir
    File tmp;

    private File dataRoot() {
        File d = new File(tmp, "data");
        d.mkdirs();
        return d;
    }

    @Test
    void resolveDataMapsDataPrefix() throws IOException {
        File root = dataRoot();
        File expected = new File(root, "minstrel/x.epub");
        assertEquals(expected, HttpUtil.resolveData(root, "data/minstrel/x.epub"));
        assertEquals(expected, HttpUtil.resolveData(root, "minstrel/x.epub"));
        assertEquals(expected, HttpUtil.resolveData(root, "file://data/minstrel/x.epub"));
        assertEquals(expected, HttpUtil.resolveData(root, "/data/minstrel/x.epub"));
        assertEquals(expected, HttpUtil.resolveData(root, "data\\minstrel\\x.epub"));
    }

    @Test
    void resolveDataNormalizesWindowsSeparators() {
        File root = dataRoot();
        File expected = new File(root, "minstrel/tmp/item/audio.mp3");
        assertEquals(expected, HttpUtil.resolveData(root, "data/minstrel/tmp/item/audio.mp3"));
    }

    @Test
    void resolveDataRejectsTraversal() throws IOException {
        File root = dataRoot();
        // ../../ escapes the data root -> must return null
        assertNull(HttpUtil.resolveData(root, "data/../../evil.txt"));
        assertNull(HttpUtil.resolveData(root, "../evil.txt"));
        assertNull(HttpUtil.resolveData(root, "data/minstrel/../../../etc/passwd"));
    }

    @Test
    void resolveDataRejectsAbsoluteWindowsDrive() {
        File root = dataRoot();
        assertNull(HttpUtil.resolveData(root, "C:/windows/system32/x"));
    }

    @Test
    void resolveStaticRoutesToWebOrData() throws IOException {
        File web = new File(tmp, "web");
        File data = dataRoot();
        web.mkdirs();
        File w = new File(web, "index.html");
        File d = new File(data, "minstrel/tmp/x.png");
        Files.writeString(w.toPath(), "hi");
        d.getParentFile().mkdirs();
        Files.write(d.toPath(), new byte[]{1});

        assertEquals(w, HttpUtil.resolveStatic(web, data, "/index.html"));
        assertEquals(new File(web, "js/app.js"), HttpUtil.resolveStatic(web, data, "/js/app.js"));
        assertEquals(d, HttpUtil.resolveStatic(web, data, "/data/minstrel/tmp/x.png"));
        assertNotNull(HttpUtil.resolveStatic(web, data, "/minstrel/tmp/x.png"));
        assertNull(HttpUtil.resolveStatic(web, data, "/data/../../outside.png"));
    }

    @Test
    void toUrlStripsFilePrefixAndSlashes() {
        assertEquals("data/minstrel/tmp/x.mp3", HttpUtil.toUrl("file://data/minstrel/tmp/x.mp3"));
        assertEquals("data/minstrel/tmp/x.mp3", HttpUtil.toUrl("/data/minstrel/tmp/x.mp3"));
        assertEquals("data/minstrel/tmp/x.mp3", HttpUtil.toUrl("data\\minstrel\\tmp\\x.mp3"));
    }
}