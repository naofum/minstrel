package minstrel.server.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import minstrel.server.HttpUtil;
import minstrel.server.Json;
import minstrel.server.commander.CommanderImpl;
import minstrel.server.librarian.LibrarianImpl;
import minstrel.server.unzipper.UnzipperImpl;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Routes the Cordova plugin replacement calls:
 * <ul>
 *   <li>{@code POST /api/commander}</li>
 *   <li>{@code POST /api/librarian}</li>
 *   <li>{@code POST /api/unzipper}</li>
 * </ul>
 * The response mirrors the original plugin contract: {@code {"success": "..."}}.
 */
public class ApiHandler implements HttpHandler {

    private final File webRoot;
    private final File dataRoot;

    public ApiHandler(File webRoot, File dataRoot) {
        this.webRoot = webRoot;
        this.dataRoot = dataRoot;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            String method = ex.getRequestMethod();
            if (!"POST".equalsIgnoreCase(method)) {
                HttpUtil.sendText(ex, 405, "Method Not Allowed");
                return;
            }

            String body = HttpUtil.readBody(ex);
            Map<String, Object> params = parseBody(body);

            String success;
            if (path.endsWith("/commander")) {
                success = CommanderImpl.handle(webRoot, dataRoot, params);
            } else if (path.endsWith("/librarian")) {
                success = LibrarianImpl.handle(dataRoot, params);
            } else if (path.endsWith("/unzipper")) {
                success = UnzipperImpl.handle(dataRoot, params);
            } else {
                HttpUtil.sendText(ex, 404, "Not Found");
                return;
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", success == null ? "" : success);
            HttpUtil.sendJsonOk(ex, Json.stringify(result));
        } catch (Exception e) {
            e.printStackTrace();
            HttpUtil.sendJsonError(ex, "Exception " + e);
        } finally {
            ex.close();
        }
    }

    private static Map<String, Object> parseBody(String body) {
        if (body == null || body.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Object o = Json.parse(body);
        if (o instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) o;
            return m;
        }
        return new LinkedHashMap<>();
    }
}