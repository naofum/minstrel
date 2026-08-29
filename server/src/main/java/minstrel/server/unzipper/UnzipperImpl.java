package minstrel.server.unzipper;

import minstrel.server.HttpUtil;
import minstrel.server.Json;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Port of the Android {@code readbeyond-plugin-unzipper}.
 * <p>
 * Supported modes: {@code all}, {@code allNonMedia}, {@code allStructure},
 * {@code selected}, {@code allSmall}, {@code list}. Returns a JSON string
 * {@code {"files":{"items":[...]}}} (list of extracted / listed entries).
 */
public final class UnzipperImpl {

    private static final String[] DEFAULT_STRUCTURE_EXTENSIONS = {
            ".htm", ".html", ".nav", ".ncx", ".opf", ".smil", ".txt", ".xhtml", ".xml"
    };
    private static final String[] DEFAULT_MEDIA_EXTENSIONS = {
            ".aac", ".flac", ".m4a", ".mp3", ".mp4", ".oga", ".ogg", ".wav", ".webm"
    };
    private static final long DEFAULT_MAXIMUM_SIZE_FILE = 4L * 1024 * 1024;

    private UnzipperImpl() {
    }

    public static String handle(File dataRoot, Map<String, Object> body) throws IOException {
        String mode = str(body.get("mode"));
        String srcPath = str(body.get("srcPath"));
        String dstPath = str(body.get("dstPath"));
        Map<String, Object> args = parseOption(str(body.get("args")));

        File src = HttpUtil.resolveData(dataRoot, srcPath);
        if (src == null || !src.isFile()) {
            return stringify(List.of());
        }

        if ("list".equals(mode)) {
            return stringify(list(src));
        }

        File dst = HttpUtil.resolveData(dataRoot, dstPath);
        if (dst == null) {
            return stringify(List.of());
        }
        dst.mkdirs();

        List<String> decompressed;
        try {
            decompressed = unzip(src, dst, mode, args);
        } catch (IOException e) {
            // malformed archive or path traversal attempt: return nothing extracted
            decompressed = new ArrayList<>();
        }
        return stringify(decompressed);
    }

    private static List<String> unzip(File src, File dst, String mode, Map<String, Object> args) throws IOException {
        List<String> decompressed = new ArrayList<>();

        String[] excludeExtensions = array(args.get("excludeExtensions"));
        String[] extensions = array(args.get("extensions"));
        if (extensions == null && "allStructure".equals(mode)) {
            extensions = DEFAULT_STRUCTURE_EXTENSIONS;
        }
        String[] entries = array(args.get("entries"));
        long maximumSize = num(args.get("maximumFileSize"), DEFAULT_MAXIMUM_SIZE_FILE);

        try (ZipFile zip = openZip(src)) {
            Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                ZipEntry ze = en.nextElement();
                String name = ze.getName();
                String lower = name.toLowerCase();

                boolean target = false;
                switch (mode) {
                    case "all":
                        target = true;
                        break;
                    case "allNonMedia":
                        target = !isFile(lower, excludeExtensions);
                        break;
                    case "allStructure":
                        target = isFile(lower, extensions);
                        break;
                    case "allSmall":
                        target = ze.getSize() <= maximumSize;
                        break;
                    case "selected":
                        if (entries != null) {
                            for (String e : entries) {
                                if (e != null && name.contains(e)) {
                                    target = true;
                                    break;
                                }
                            }
                        }
                        break;
                    default:
                        break;
                }

                if (target) {
                    File destFile = new File(dst, name);
                    String canonical = destFile.getCanonicalPath();
                    String dstCanonical = dst.getCanonicalPath();
                    if (!canonical.startsWith(dstCanonical + File.separator) && !canonical.equals(dstCanonical)) {
                        throw new IOException("Path traversal detected: " + name);
                    }
                    if (!ze.isDirectory()) {
                        File parent = destFile.getParentFile();
                        if (parent != null) {
                            parent.mkdirs();
                        }
                        try (InputStream is = zip.getInputStream(ze); OutputStream os = Files.newOutputStream(destFile.toPath())) {
                            is.transferTo(os);
                        }
                        decompressed.add(name);
                    }
                }
            }
        } catch (IOException e) {
            throw e;
        }
        Collections.sort(decompressed);
        return decompressed;
    }

    private static List<String> list(File src) throws IOException {
        List<String> names = new ArrayList<>();
        try (ZipFile zip = openZip(src)) {
            Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                names.add(en.nextElement().getName());
            }
        }
        Collections.sort(names);
        return names;
    }

    private static ZipFile openZip(File f) throws IOException {
        try {
            return new ZipFile(f, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // fall back to the Windows Japanese codepage (CP932), like Android's MS932
            return new ZipFile(f, Charset.forName("CP932"));
        }
    }

    private static String stringify(List<String> entries) {
        Map<String, Object> obj = new LinkedHashMap<>();
        Map<String, Object> files = new LinkedHashMap<>();
        List<Object> items = new ArrayList<>();
        for (String s : entries) {
            items.add(s);
        }
        files.put("items", items);
        obj.put("files", files);
        return Json.stringify(obj);
    }

    private static boolean isFile(String lower, String[] exts) {
        if (exts == null) {
            return false;
        }
        for (String ext : exts) {
            if (ext != null && lower.endsWith(ext.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Object> parseOption(String option) {
        if (option == null || option.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Object o = Json.parse(option);
        if (o instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) o;
            return m;
        }
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private static String[] array(Object o) {
        if (!(o instanceof List)) {
            return null;
        }
        List<Object> l = (List<Object>) o;
        if (l.isEmpty()) {
            return null;
        }
        String[] ret = new String[l.size()];
        for (int i = 0; i < l.size(); i++) {
            ret[i] = l.get(i) == null ? "" : l.get(i).toString();
        }
        return ret;
    }

    private static long num(Object o, long def) {
        if (o instanceof Number) {
            return ((Number) o).longValue();
        }
        if (o instanceof String) {
            try {
                return Long.parseLong((String) o);
            } catch (NumberFormatException e) {
                return def;
            }
        }
        return def;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}