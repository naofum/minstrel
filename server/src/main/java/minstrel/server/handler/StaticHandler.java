package minstrel.server.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import minstrel.server.HttpUtil;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * Serves static resources (web root + data root) with HTTP Range support
 * required by the HTML5 audio element (seek).
 */
public class StaticHandler implements HttpHandler {

    private static final Map<String, String> MIME = new HashMap<>();

    static {
        MIME.put("html", "text/html; charset=utf-8");
        MIME.put("htm", "text/html; charset=utf-8");
        MIME.put("js", "application/javascript; charset=utf-8");
        MIME.put("css", "text/css; charset=utf-8");
        MIME.put("json", "application/json; charset=utf-8");
        MIME.put("xml", "application/xml; charset=utf-8");
        MIME.put("txt", "text/plain; charset=utf-8");
        MIME.put("png", "image/png");
        MIME.put("jpg", "image/jpeg");
        MIME.put("jpeg", "image/jpeg");
        MIME.put("gif", "image/gif");
        MIME.put("svg", "image/svg+xml");
        MIME.put("ico", "image/x-icon");
        MIME.put("webp", "image/webp");
        MIME.put("bmp", "image/bmp");
        MIME.put("tiff", "image/tiff");
        MIME.put("woff", "font/woff");
        MIME.put("woff2", "font/woff2");
        MIME.put("ttf", "font/ttf");
        MIME.put("otf", "font/otf");
        MIME.put("mp3", "audio/mpeg");
        MIME.put("m4a", "audio/mp4");
        MIME.put("m4b", "audio/mp4");
        MIME.put("aac", "audio/aac");
        MIME.put("ogg", "audio/ogg");
        MIME.put("oga", "audio/ogg");
        MIME.put("opus", "audio/ogg");
        MIME.put("wav", "audio/wav");
        MIME.put("flac", "audio/flac");
        MIME.put("mp4", "video/mp4");
        MIME.put("webm", "video/webm");
        MIME.put("epub", "application/epub+zip");
        MIME.put("cbz", "application/vnd.comicbook+zip");
        MIME.put("abz", "application/zip");
        MIME.put("zip", "application/zip");
        MIME.put("smil", "application/smil+xml");
        MIME.put("ncx", "application/x-dtbncx+xml");
        MIME.put("opf", "application/oebps-package+xml");
    }

    private final File webRoot;
    private final File dataRoot;

    public StaticHandler(File webRoot, File dataRoot) {
        this.webRoot = webRoot;
        this.dataRoot = dataRoot;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if ("HEAD".equalsIgnoreCase(ex.getRequestMethod())) {
            File f = resolve(path);
            if (f == null || !f.isFile()) {
                HttpUtil.sendText(ex, 404, "Not Found");
                return;
            }
            ex.getResponseHeaders().set("Content-Type", mime(f));
            ex.getResponseHeaders().set("Accept-Ranges", "bytes");
            ex.sendResponseHeaders(200, -1);
            ex.close();
            return;
        }
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            HttpUtil.sendText(ex, 405, "Method Not Allowed");
            return;
        }

        File f = resolve(path);
        if (f == null || !f.isFile()) {
            // META-INF/encryption.xml is optional: the EPUB parser probes it to
            // detect encrypted books, so a missing file should not 404 (which
            // the browser would log as a console error). An empty response is
            // equivalent to "no encryption file" for the probe.
            if (path.endsWith("/encryption.xml") || path.equals("encryption.xml")) {
                ex.getResponseHeaders().set("Content-Type", "application/xml; charset=utf-8");
                ex.sendResponseHeaders(200, -1);
                ex.close();
                return;
            }
            HttpUtil.sendText(ex, 404, "Not Found");
            return;
        }

        String mime = mime(f);
        long length = f.length();
        String range = ex.getRequestHeaders().getFirst("Range");

        ex.getResponseHeaders().set("Content-Type", mime);
        ex.getResponseHeaders().set("Accept-Ranges", "bytes");
        ex.getResponseHeaders().set("Cache-Control", "no-cache");

        if (range != null && range.startsWith("bytes=")) {
            String spec = range.substring("bytes=".length()).trim();
            long[] r = parseRange(spec, length);
            if (r == null) {
                ex.getResponseHeaders().set("Content-Range", "bytes */" + length);
                HttpUtil.sendText(ex, 416, "Range Not Satisfiable");
                return;
            }
            long start = r[0];
            long end = r[1];
            long len = end - start + 1;
            ex.getResponseHeaders().set("Content-Range", "bytes " + start + "-" + end + "/" + length);
            ex.sendResponseHeaders(206, len);
            try (RandomAccessFile raf = new RandomAccessFile(f, "r");
                 OutputStream os = ex.getResponseBody()) {
                raf.seek(start);
                byte[] buf = new byte[64 * 1024];
                long remaining = len;
                while (remaining > 0) {
                    int n = raf.read(buf, 0, (int) Math.min(buf.length, remaining));
                    if (n < 0) {
                        break;
                    }
                    os.write(buf, 0, n);
                    remaining -= n;
                }
            }
        } else {
            ex.sendResponseHeaders(200, length);
            try (OutputStream os = ex.getResponseBody()) {
                Files.copy(f.toPath(), os);
            }
        }
        ex.close();
    }

    private File resolve(String path) {
        File f = HttpUtil.resolveStatic(webRoot, dataRoot, path);
        if (f != null && f.isDirectory()) {
            File index = new File(f, "index.html");
            if (index.isFile()) {
                return index;
            }
            return null;
        }
        return f;
    }

    private static long[] parseRange(String spec, long length) {
        int dash = spec.indexOf('-');
        if (dash < 0) {
            return null;
        }
        String a = spec.substring(0, dash).trim();
        String b = spec.substring(dash + 1).trim();
        long start;
        long end;
        try {
            if (a.isEmpty()) {
                long suffix = Long.parseLong(b);
                if (suffix <= 0) {
                    return null;
                }
                start = Math.max(0, length - suffix);
                end = length - 1;
            } else {
                start = Long.parseLong(a);
                end = b.isEmpty() ? length - 1 : Long.parseLong(b);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        if (start < 0 || start >= length || end < start) {
            return null;
        }
        end = Math.min(end, length - 1);
        return new long[]{start, end};
    }

    private static String mime(File f) {
        String name = f.getName().toLowerCase();
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            String ext = name.substring(dot + 1);
            String m = MIME.get(ext);
            if (m != null) {
                return m;
            }
        }
        return "application/octet-stream";
    }
}