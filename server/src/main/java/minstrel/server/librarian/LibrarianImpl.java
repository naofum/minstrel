package minstrel.server.librarian;

import minstrel.server.HttpUtil;
import minstrel.server.Json;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Port of the Android {@code readbeyond-plugin-librarian}.
 * <p>
 * Handles modes {@code full} (library scan), {@code single}, {@code custom}
 * ({@code getSortedListOfAssets}), {@code createThumbnailDirectory} and
 * {@code emptyThumbnailDirectory}.
 */
public final class LibrarianImpl {

    private LibrarianImpl() {
    }

    public static String handle(File dataRoot, Map<String, Object> body) throws IOException {
        String mode = str(body.get("mode"));
        String path = str(body.get("path"));
        Map<String, Object> args = parseOption(str(body.get("args")));

        switch (mode) {
            case "createThumbnailDirectory":
                ensureThumbnailDirectory(dataRoot, str(args.get("thumbnailDirectory")), false);
                return "";
            case "emptyThumbnailDirectory":
                ensureThumbnailDirectory(dataRoot, str(args.get("thumbnailDirectory")), true);
                return "";
            case "full":
                return scan(dataRoot, args);
            case "single":
                return single(dataRoot, path, args);
            case "custom":
                return custom(dataRoot, path, args);
            default:
                return "";
        }
    }

    // ------------------------------------------------------------------
    // full scan
    // ------------------------------------------------------------------

    private static String scan(File dataRoot, Map<String, Object> args) {
        boolean recursive = bool(args.get("recursive"), true);
        boolean ignoreHidden = bool(args.get("ignoreHidden"), true);
        String thumbnailDirArg = str(args.get("thumbnailDirectory"));
        int thumbWidth = intVal(args.get("thumbnailWidth"), 300);
        int thumbHeight = intVal(args.get("thumbnailHeight"), 400);
        File thumbDir = ensureThumbnailDirectory(dataRoot, thumbnailDirArg, false);

        List<Publication> publications = new ArrayList<>();

        Map<String, Object> formats = map(args.get("formats"));
        for (Map.Entry<String, Object> e : formats.entrySet()) {
            String format = e.getKey();
            List<Object> extList = list(e.getValue());
            String[] extensions = extList.isEmpty() ? null : extList.stream().map(String::valueOf).toArray(String[]::new);

            FormatHandler fh = getFormatHandler(format);
            if (fh == null) {
                continue;
            }
            if (extensions != null) {
                fh.addAllowedLowercasedExtensions(extensions);
            }
            fh.setThumbnailInfo(thumbDir.getAbsolutePath(), thumbWidth, thumbHeight);

            List<Object> paths = list(args.get("paths"));
            for (Object p : paths) {
                File dir = HttpUtil.resolveData(dataRoot, String.valueOf(p));
                if (dir != null && dir.isDirectory()) {
                    discoverPublications(dataRoot, dir, fh, publications, recursive, ignoreHidden);
                }
            }
        }

        finalizePublications(dataRoot, publications, thumbDir);
        return Json.stringify(stringifyPublications(publications));
    }

    private static void discoverPublications(File dataRoot, File dir, FormatHandler fh, List<Publication> acc, boolean recursive, boolean ignoreHidden) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (ignoreHidden && child.getName().startsWith(".")) {
                continue;
            }
            if (child.isDirectory()) {
                if (recursive) {
                    discoverPublications(dataRoot, child, fh, acc, recursive, ignoreHidden);
                }
            } else if (fh.isParsable(child.getName().toLowerCase())) {
                Publication p = fh.parseFile(child);
                if (p != null && p.isValid) {
                    p.absolutePath = virtualPath(dataRoot, child);
                    acc.add(p);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // single
    // ------------------------------------------------------------------

    private static String single(File dataRoot, String path, Map<String, Object> args) {
        String format = str(args.get("format"));
        String thumbnailDirArg = str(args.get("thumbnailDirectory"));
        int thumbWidth = intVal(args.get("thumbnailWidth"), 300);
        int thumbHeight = intVal(args.get("thumbnailHeight"), 400);
        File thumbDir = ensureThumbnailDirectory(dataRoot, thumbnailDirArg, false);

        File file = HttpUtil.resolveData(dataRoot, path);
        if (file == null || !file.isFile()) {
            return Json.stringify(stringifyPublications(List.of()));
        }

        FormatHandler fh = getFormatHandler(format);
        List<Publication> publications = new ArrayList<>();
        if (fh != null) {
            fh.setThumbnailInfo(thumbDir.getAbsolutePath(), thumbWidth, thumbHeight);
            Publication p = fh.parseFile(file);
            if (p != null && p.isValid) {
                p.absolutePath = virtualPath(dataRoot, file);
                publications.add(p);
            }
        }
        finalizePublications(dataRoot, publications, thumbDir);
        return Json.stringify(stringifyPublications(publications));
    }

    // ------------------------------------------------------------------
    // custom (getSortedListOfAssets)
    // ------------------------------------------------------------------

    private static String custom(File dataRoot, String path, Map<String, Object> args) {
        File file = HttpUtil.resolveData(dataRoot, path);
        if (file == null || !file.isFile()) {
            return Json.stringify(stringifyAssets(List.of()));
        }
        String format = str(args.get("format"));
        List<ZipAsset> assets;
        switch (format == null ? "" : format) {
            case "cbz":
                assets = new FormatHandlerCBZ().getSortedListOfAssets(file, args);
                break;
            case "abz":
                assets = new FormatHandlerABZ().getSortedListOfAssets(file, args);
                break;
            default:
                assets = new FormatHandlerEPUB().getSortedListOfAssets(file, new String[]{".mp3", ".mp4", ".m4a", ".m4b", ".ogg", ".oga", ".opus", ".wav", ".aac", ".flac", ".webm"});
                break;
        }
        return Json.stringify(stringifyAssets(assets));
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static void finalizePublications(File dataRoot, List<Publication> publications, File thumbDir) {
        for (Publication p : publications) {
            if (p.formats.isEmpty()) {
                continue;
            }
            Format defaultFormat = p.formats.get(0);
            String title = defaultFormat.getMetadatum("title");
            String relThumb = defaultFormat.getMetadatum("relativePathThumbnail");
            p.mainFormat = defaultFormat.name;
            if (title != null && !title.isEmpty()) {
                p.title = title;
            }
            if (relThumb != null && !relThumb.isEmpty()) {
                p.relativePathThumbnail = relThumb;
                p.absolutePathThumbnail = virtualPath(dataRoot, thumbDir) + "/" + relThumb;
            }
        }
    }

    private static Map<String, Object> stringifyPublications(List<Publication> publications) {
        Map<String, Object> obj = new LinkedHashMap<>();
        Map<String, Object> main = new LinkedHashMap<>();
        List<Object> items = new ArrayList<>();
        for (Publication p : publications) {
            items.add(p.toJson());
        }
        main.put("items", items);
        obj.put("publications", main);
        return obj;
    }

    private static Map<String, Object> stringifyAssets(List<ZipAsset> assets) {
        Map<String, Object> obj = new LinkedHashMap<>();
        Map<String, Object> main = new LinkedHashMap<>();
        List<Object> items = new ArrayList<>();
        for (ZipAsset a : assets) {
            items.add(a.toJson());
        }
        main.put("items", items);
        obj.put("assets", main);
        return obj;
    }

    private static File ensureThumbnailDirectory(File dataRoot, String thumbnailDirArg, boolean delete) {
        File dir = HttpUtil.resolveData(dataRoot, thumbnailDirArg);
        if (dir == null) {
            dir = new File(dataRoot, "minstrel/.thumbnails");
        }
        if (delete && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    f.delete();
                }
            }
        }
        if (!dir.isDirectory()) {
            dir.mkdirs();
        }
        File nomedia = new File(dir, ".nomedia");
        if (!nomedia.exists()) {
            try {
                nomedia.createNewFile();
            } catch (IOException e) {
                // nop
            }
        }
        return dir;
    }

    private static FormatHandler getFormatHandler(String format) {
        if (format == null) {
            return null;
        }
        switch (format) {
            case "epub":
                return new FormatHandlerEPUB();
            case "cbz":
                return new FormatHandlerCBZ();
            case "abz":
                return new FormatHandlerABZ();
            default:
                return null;
        }
    }

    private static String virtualPath(File dataRoot, File file) {
        try {
            String d = dataRoot.getCanonicalPath();
            String p = file.getCanonicalPath();
            if (p.startsWith(d)) {
                String rel = p.substring(d.length()).replace(File.separatorChar, '/');
                while (rel.startsWith("/")) {
                    rel = rel.substring(1);
                }
                return "data" + (rel.isEmpty() ? "" : "/" + rel);
            }
        } catch (IOException e) {
            // fall through
        }
        return file.getAbsolutePath();
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
    private static Map<String, Object> map(Object o) {
        if (o instanceof Map) {
            return (Map<String, Object>) o;
        }
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object o) {
        if (o instanceof List) {
            return (List<Object>) o;
        }
        return new ArrayList<>();
    }

    private static boolean bool(Object o, boolean def) {
        if (o == null) {
            return def;
        }
        if (o instanceof Boolean) {
            return (Boolean) o;
        }
        if (o instanceof Number) {
            return ((Number) o).intValue() != 0;
        }
        return Boolean.parseBoolean(o.toString());
    }

    private static int intVal(Object o, int def) {
        if (o instanceof Number) {
            return ((Number) o).intValue();
        }
        if (o instanceof String) {
            try {
                return Integer.parseInt((String) o);
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