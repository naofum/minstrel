package minstrel.server.handler;

import com.sun.net.httpserver.HttpServer;
import minstrel.server.TestFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test: exercises the full /api/* routing exactly like the browser
 * shim (web/js/windows.js) does.
 */
class ApiHandlerTest {

    @TempDir
    File tmp;

    private HttpServer server;
    private String base;
    private HttpClient client;

    @BeforeEach
    void setUp() throws Exception {
        File webRoot = new File(tmp, "web");
        File dataRoot = new File(tmp, "data");
        webRoot.mkdirs();
        dataRoot.mkdirs();
        new File(dataRoot, "minstrel").mkdirs();
        TestFixtures.createEpub(new File(dataRoot, "minstrel"));
        TestFixtures.createCbz(new File(dataRoot, "minstrel"));
        TestFixtures.createAbz(new File(dataRoot, "minstrel"));

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api", new ApiHandler(webRoot, dataRoot));
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        client = HttpClient.newHttpClient();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private String post(String endpoint, String jsonBody) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(base + endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        HttpResponse<String> r = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, r.statusCode());
        return r.body();
    }

    @Test
    void commanderFilesystemInfo() throws Exception {
        String body = post("/api/commander", "{\"command\":\"filesystemInfo\",\"option\":\"{}\"}");
        assertTrue(body.contains("\"success\""));
        assertTrue(body.contains("root"));
        assertTrue(body.contains("storageRoots"));
        assertTrue(body.contains("\\\"root\\\":\\\"data"));
    }

    @Test
    void librarianFullScan() throws Exception {
        String args = "{"
                + "\"paths\":[\"data/minstrel\"],"
                + "\"recursive\":true,\"ignoreHidden\":true,"
                + "\"thumbnailDirectory\":\"data/minstrel/.thumbnails\","
                + "\"thumbnailWidth\":300,\"thumbnailHeight\":400,"
                + "\"formats\":{\"epub\":[\".epub\"],\"cbz\":[\".cbz\"],\"abz\":[\".abz\"]}"
                + "}";
        String body = post("/api/librarian", "{\"mode\":\"full\",\"path\":\"\",\"args\":" + "\"" + args.replace("\"", "\\\"") + "\"}");
        assertTrue(body.contains("\"success\""));
        assertTrue(body.contains("publications"));
        assertTrue(body.contains("Test Book"));
        assertTrue(body.contains("Sample Comic"));
        assertTrue(body.contains("Sample Audiobook"));
    }

    @Test
    void unzipperAll() throws Exception {
        String body = post("/api/unzipper",
                "{\"mode\":\"all\",\"srcPath\":\"data/minstrel/sample.abz\",\"dstPath\":\"data/minstrel/tmp/x\",\"args\":\"{}\"}");
        assertTrue(body.contains("\"success\""));
        assertTrue(body.contains("track01.wav"));
    }

    @Test
    void unknownEndpointReturns404() throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/api/bogus"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();
        HttpResponse<String> r = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, r.statusCode());
    }
}