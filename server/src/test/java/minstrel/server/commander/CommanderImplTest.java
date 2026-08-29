package minstrel.server.commander;

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

import static minstrel.server.TestFixtures.commander;
import static org.junit.jupiter.api.Assertions.*;

class CommanderImplTest {

    @TempDir
    File tmp;

    private File dataRoot() throws IOException {
        File d = new File(tmp, "data");
        d.mkdirs();
        new File(d, "minstrel").mkdirs();
        return d;
    }

    private File webRoot() throws IOException {
        File w = new File(tmp, "web");
        w.mkdirs();
        new File(w, "ebooks").mkdirs();
        return w;
    }

    private String success(File web, File data, Map<String, Object> body) throws IOException {
        return CommanderImpl.handle(web, data, body);
    }

    @Test
    void filesystemInfoReturnsDataRoot() throws IOException {
        File data = dataRoot();
        File web = webRoot();
        String result = success(web, data, commander("filesystemInfo", "{}"));
        Map<?, ?> info = (Map<?, ?>) Json.parse(result);
        assertEquals("data", info.get("root"));
        assertEquals("/", info.get("separator"));
        assertEquals(List.of("data"), info.get("storageRoots"));
    }

    @Test
    void writeToFileCreatesFile() throws IOException {
        File data = dataRoot();
        File web = webRoot();
        String opt = "{\"destination\":\"data/minstrel/notes.txt\",\"string\":\"hello world\"}";
        assertEquals("filewritten", success(web, data, commander("writeToFile", opt)));
        assertEquals("hello world", Files.readString(new File(data, "minstrel/notes.txt").toPath()));
    }

    @Test
    void checkFileExists() throws IOException {
        File data = dataRoot();
        File web = webRoot();
        Files.writeString(new File(data, "minstrel/exists.txt").toPath(), "x");
        assertEquals("fileexists", success(web, data, commander("checkFileExists", "{\"path\":\"data/minstrel/exists.txt\"}")));
        assertEquals("filedoesnotexist", success(web, data, commander("checkFileExists", "{\"path\":\"data/minstrel/missing.txt\"}")));
    }

    @Test
    void copyFileCreatesDestination() throws IOException {
        File data = dataRoot();
        File web = webRoot();
        Files.writeString(new File(data, "minstrel/src.txt").toPath(), "payload");
        String opt = "{\"source\":\"data/minstrel/src.txt\",\"destination\":\"data/minstrel/dst.txt\"}";
        assertEquals("filecopied", success(web, data, commander("copy", opt)));
        assertEquals("payload", Files.readString(new File(data, "minstrel/dst.txt").toPath()));
    }

    @Test
    void copyMissingSourceReports() throws IOException {
        File data = dataRoot();
        File web = webRoot();
        String opt = "{\"source\":\"data/minstrel/nope.txt\",\"destination\":\"data/minstrel/dst.txt\"}";
        assertEquals("filedoesnotexist", success(web, data, commander("copy", opt)));
    }

    @Test
    void copyFromAssetsWwwCopiesFromWebRoot() throws IOException {
        File data = dataRoot();
        File web = webRoot();
        Files.writeString(new File(web, "ebooks/guide.epub").toPath(), "epub-data");
        String opt = "{\"source\":\"ebooks/guide.epub\",\"destination\":\"data/minstrel/guide.epub\"}";
        assertEquals("filecopied", success(web, data, commander("copyFromAssetsWWW", opt)));
        assertEquals("epub-data", Files.readString(new File(data, "minstrel/guide.epub").toPath()));
    }

    @Test
    void deleteAbsoluteRemovesRecursively() throws IOException {
        File data = dataRoot();
        File web = webRoot();
        File dir = new File(data, "minstrel/tmp");
        dir.mkdirs();
        Files.writeString(new File(dir, "a.txt").toPath(), "1");
        assertEquals("", success(web, data, commander("deleteAbsolute", "{\"path\":\"data/minstrel/tmp\"}")));
        assertFalse(dir.exists());
    }

    @Test
    void deleteRelativeRemoves() throws IOException {
        File data = dataRoot();
        File web = webRoot();
        File dir = new File(data, "minstrel/tmp");
        dir.mkdirs();
        Files.writeString(new File(dir, "a.txt").toPath(), "1");
        assertEquals("", success(web, data, commander("deleteRelative", "{\"path\":\"minstrel/tmp\"}")));
        assertFalse(dir.exists());
    }

    @Test
    void listSubdirectoriesReturnsTree() throws IOException {
        File data = dataRoot();
        File web = webRoot();
        new File(data, "minstrel/sub/a").mkdirs();
        new File(data, "minstrel/sub/b").mkdirs();
        String result = success(web, data, commander("listSubdirectories", "{\"path\":\"data/minstrel\",\"recursive\":true,\"ignoreHidden\":true}"));
        Map<?, ?> obj = (Map<?, ?>) Json.parse(result);
        assertTrue(obj.containsKey("data/minstrel"));
        Map<?, ?> sp = (Map<?, ?>) obj.get("data/minstrel");
        assertTrue(((List<?>) sp.get("subdirectories")).size() >= 2);
    }

    @Test
    void platformCommandsAreNoOps() throws IOException {
        File data = dataRoot();
        File web = webRoot();
        for (String cmd : new String[]{"toast", "dim", "setbrightness", "getbrightness", "orient", "openExternalURL"}) {
            String opt = "{\"message\":\"x\",\"dim\":true,\"value\":\"1.0\",\"url\":\"http://example.com\",\"value\":\"portrait\"}";
            assertEquals("", success(web, data, commander(cmd, opt)), "command: " + cmd);
        }
    }
}