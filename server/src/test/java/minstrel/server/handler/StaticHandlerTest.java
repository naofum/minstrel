package minstrel.server.handler;

import com.sun.net.httpserver.HttpServer;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class StaticHandlerTest {

    @TempDir
    File tmp;

    private HttpServer server;
    private File webRoot;
    private File dataRoot;
    private String base;
    private HttpClient client;

    @BeforeEach
    void setUp() throws Exception {
        webRoot = new File(tmp, "web");
        dataRoot = new File(tmp, "data");
        webRoot.mkdirs();
        dataRoot.mkdirs();

        Files.writeString(new File(webRoot, "index.html").toPath(), "<html>app</html>");
        File img = new File(webRoot, "img");
        img.mkdirs();
        Files.write(new File(img, "icon.png").toPath(), new byte[]{1, 2, 3});
        File dataFile = new File(dataRoot, "minstrel/tmp/x.mp3");
        dataFile.getParentFile().mkdirs();
        Files.write(dataFile.toPath(), new byte[1000]);

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", new StaticHandler(webRoot, dataRoot));
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        client = HttpClient.newHttpClient();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private HttpResponse<byte[]> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base + path)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }

    @Test
    void servesIndexFromWebRoot() throws Exception {
        HttpResponse<byte[]> r = get("/");
        assertEquals(200, r.statusCode());
        assertEquals("<html>app</html>", new String(r.body(), StandardCharsets.UTF_8));
    }

    @Test
    void servesDataFiles() throws Exception {
        HttpResponse<byte[]> r = get("/data/minstrel/tmp/x.mp3");
        assertEquals(200, r.statusCode());
        assertEquals(1000, r.body().length);
        assertEquals("audio/mpeg", r.headers().firstValue("Content-Type").orElse(""));
    }

    @Test
    void servesImagesWithMime() throws Exception {
        HttpResponse<byte[]> r = get("/img/icon.png");
        assertEquals(200, r.statusCode());
        assertEquals("image/png", r.headers().firstValue("Content-Type").orElse(""));
    }

    @Test
    void rangeRequestReturnsPartialContent() throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/data/minstrel/tmp/x.mp3"))
                .header("Range", "bytes=0-99")
                .GET().build();
        HttpResponse<byte[]> r = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(206, r.statusCode());
        assertEquals(100, r.body().length);
        assertEquals("bytes 0-99/1000", r.headers().firstValue("Content-Range").orElse(""));
        assertEquals("bytes", r.headers().firstValue("Accept-Ranges").orElse(""));
    }

    @Test
    void missingEncryptionXmlReturnsEmpty200() throws Exception {
        HttpResponse<byte[]> r = get("/data/minstrel/tmp/book/META-INF/encryption.xml");
        assertEquals(200, r.statusCode());
        assertEquals(0, r.body().length);
    }

    @Test
    void missingFileReturns404() throws Exception {
        HttpResponse<byte[]> r = get("/img/nothere.png");
        assertEquals(404, r.statusCode());
    }

    @Test
    void traversalPathReturns404() throws Exception {
        HttpResponse<byte[]> r = get("/data/../pom.xml");
        assertEquals(404, r.statusCode());
    }
}