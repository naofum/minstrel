package minstrel.server;

import com.sun.net.httpserver.HttpExchange;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Shared HTTP/IO helpers and path resolution used across handlers.
 */
public final class HttpUtil {

    public static final String JSON = "application/json; charset=utf-8";

    private HttpUtil() {
    }

    // ------------------------------------------------------------------
    // HTTP responses
    // ------------------------------------------------------------------

    public static void sendJson(HttpExchange ex, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", JSON);
        ex.sendResponseHeaders(status, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }

    public static void sendJsonOk(HttpExchange ex, String json) throws IOException {
        sendJson(ex, 200, json);
    }

    public static void sendJsonError(HttpExchange ex, String message) throws IOException {
        sendJson(ex, 200, Json.stringify(Map.of("error", message)));
    }

    public static void sendText(HttpExchange ex, int status, String text) throws IOException {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        ex.sendResponseHeaders(status, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }

    public static String readBody(HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------
    // Path resolution
    // ------------------------------------------------------------------

    /**
     * Maps a path coming from the JS app to a real file inside {@code dataRoot}.
     * <p>
     * The app's "absolute" paths use the virtual {@code data} prefix, e.g.
     * {@code data/minstrel/tmp/ITEM/audio.mp3}. The {@code file://} prefix and
     * a leading slash are tolerated. The result is guaranteed to stay inside
     * {@code dataRoot} (path traversal is rejected).
     */
    public static File resolveData(File dataRoot, String path) {
        return resolveRooted(dataRoot, stripVirtualPrefix(path), "data");
    }

    /**
     * Maps a URL path to a file inside {@code webRoot} or {@code dataRoot}.
     * <p>
     * URLs starting with {@code /data/} or {@code /minstrel/} are served from
     * the data root; everything else is served from the web root.
     */
    public static File resolveStatic(File webRoot, File dataRoot, String urlPath) {
        if (urlPath == null) {
            urlPath = "/";
        }
        if (urlPath.startsWith("/data/") || urlPath.startsWith("/minstrel/")) {
            String rel = urlPath.substring("/data/".length());
            return resolveRooted(dataRoot, rel, "data");
        }
        return resolveRooted(webRoot, urlPath, "web");
    }

    private static File resolveRooted(File root, String rel, String guard) {
        if (rel == null) {
            rel = "";
        }
        rel = rel.replace('\\', '/');
        while (rel.startsWith("/")) {
            rel = rel.substring(1);
        }
        File target = new File(root, rel).getAbsoluteFile();
        try {
            String targetPath = target.getCanonicalPath();
            String rootPath = root.getCanonicalPath();
            if (!targetPath.equals(rootPath) && !targetPath.startsWith(rootPath + File.separator)) {
                return null;
            }
            return target;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Strips {@code file://}, leading slashes and the virtual {@code data/}
     * prefix from a path used by the app.
     */
    private static String stripVirtualPrefix(String path) {
        if (path == null) {
            return "";
        }
        String p = path;
        if (p.startsWith("file://")) {
            p = p.substring("file://".length());
        }
        p = p.replace('\\', '/');
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        if (p.startsWith("data/")) {
            p = p.substring("data/".length());
        } else if (p.equals("data")) {
            p = "";
        }
        return p;
    }

    /**
     * Turns an absolute (virtual) path used by the app into a URL the browser
     * can load, e.g. {@code data/minstrel/tmp/x.mp3}.
     */
    public static String toUrl(String appPath) {
        if (appPath == null) {
            return "";
        }
        String p = appPath;
        if (p.startsWith("file://")) {
            p = p.substring("file://".length());
        }
        p = p.replace('\\', '/');
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        return p;
    }
}