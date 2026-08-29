package minstrel.server.commander;

import minstrel.server.HttpUtil;
import minstrel.server.Json;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Port of the Android {@code readbeyond-plugin-commander}.
 * <p>
 * All commands return a {@code success} string, as the original plugin did.
 * Windows-irrelevant commands (toast, dim, brightness, orientation) are no-ops.
 */
public final class CommanderImpl {

    private CommanderImpl() {
    }

    public static String handle(File webRoot, File dataRoot, Map<String, Object> body) throws IOException {
        String command = str(body.get("command"));
        String option = str(body.get("option"));
        Map<String, Object> params = parseOption(option);

        switch (command) {
            case "filesystemInfo":
                return filesystemInfo();
            case "deleteAbsolute":
                delete(HttpUtil.resolveData(dataRoot, str(params.get("path"))));
                return "";
            case "deleteRelative":
                delete(HttpUtil.resolveData(dataRoot, str(params.get("path"))));
                return "";
            case "copy":
                return copyFile(str(params.get("source")), str(params.get("destination")), dataRoot);
            case "copyFromAssetsWWW":
                return copyFromAssetsWWW(webRoot, dataRoot, str(params.get("source")), str(params.get("destination")));
            case "move":
                return moveFile(str(params.get("source")), str(params.get("destination")), dataRoot);
            case "writeToFile":
                return writeToFile(dataRoot, str(params.get("destination")), str(params.get("string")));
            case "checkFileExists":
                File f = HttpUtil.resolveData(dataRoot, str(params.get("path")));
                return (f != null && f.exists()) ? "fileexists" : "filedoesnotexist";
            case "listSubdirectories": {
                String path = str(params.get("path"));
                boolean recursive = bool(params.get("recursive"), false);
                boolean ignoreHidden = bool(params.get("ignoreHidden"), true);
                File dir = (path == null || path.isEmpty()) ? dataRoot : HttpUtil.resolveData(dataRoot, path);
                return listSubdirectories(dataRoot, dir, recursive, ignoreHidden);
            }
            case "toast":
            case "dim":
            case "setbrightness":
            case "getbrightness":
            case "orient":
            case "openExternalURL":
            case "createDirectory":
            default:
                return "";
        }
    }

    private static String filesystemInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("root", "data");
        info.put("separator", "/");
        info.put("documentsDir", "");
        info.put("cacheDir", "");
        List<Object> roots = new ArrayList<>();
        roots.add("data");
        info.put("storageRoots", roots);
        return Json.stringify(info);
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

    private static void delete(File f) {
        if (f == null) {
            return;
        }
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File c : children) {
                    delete(c);
                }
            }
        }
        f.delete();
    }

    private static String copyFile(String source, String destination, File dataRoot) {
        File s = HttpUtil.resolveData(dataRoot, source);
        File d = HttpUtil.resolveData(dataRoot, destination);
        if (s == null || !s.exists()) {
            return "filedoesnotexist";
        }
        try {
            File parent = d.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            Files.copy(s.toPath(), d.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return "filecopied";
        } catch (IOException e) {
            return "errorwhilecopying";
        }
    }

    private static String copyFromAssetsWWW(File webRoot, File dataRoot, String source, String destination) {
        if (source == null) {
            return "errorwhilecopying";
        }
        String rel = source.replace('\\', '/');
        while (rel.startsWith("/")) {
            rel = rel.substring(1);
        }
        File s = new File(webRoot, rel).getAbsoluteFile();
        File d = HttpUtil.resolveData(dataRoot, destination);
        if (d == null) {
            return "errorwhilecopying";
        }
        try {
            if (!s.getCanonicalPath().startsWith(webRoot.getCanonicalPath() + File.separator)) {
                return "errorwhilecopying";
            }
            if (!s.isFile()) {
                return "errorwhilecopying";
            }
            File parent = d.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            Files.copy(s.toPath(), d.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return "filecopied";
        } catch (IOException e) {
            return "errorwhilecopying";
        }
    }

    private static String moveFile(String source, String destination, File dataRoot) {
        File s = HttpUtil.resolveData(dataRoot, source);
        File d = HttpUtil.resolveData(dataRoot, destination);
        if (s == null || !s.exists()) {
            return "filedoesnotexist";
        }
        try {
            File parent = d.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            s.renameTo(d);
            return "filemoved";
        } catch (Exception e) {
            return "errorwhilecopying";
        }
    }

    private static String writeToFile(File dataRoot, String destination, String contents) {
        File d = HttpUtil.resolveData(dataRoot, destination);
        if (d == null) {
            return "filenotwritten";
        }
        try {
            File parent = d.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            Files.write(d.toPath(), (contents == null ? "" : contents).getBytes(StandardCharsets.UTF_8));
            return "filewritten";
        } catch (IOException e) {
            return "filenotwritten";
        }
    }

    private static String listSubdirectories(File dataRoot, File dir, boolean recursive, boolean ignoreHidden) {
        Map<String, Object> obj = new LinkedHashMap<>();
        if (dir == null || !dir.isDirectory()) {
            return Json.stringify(obj);
        }
        Map<String, Object> sp = new LinkedHashMap<>();
        sp.put("internal", true);
        List<String> dirs = new ArrayList<>();
        collectDirectories(dir, recursive, ignoreHidden, dirs);
        sp.put("subdirectories", dirs);
        obj.put(virtualPath(dataRoot, dir), sp);
        return Json.stringify(obj);
    }

    private static void collectDirectories(File dir, boolean recursive, boolean ignoreHidden, List<String> acc) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (!f.isDirectory()) {
                continue;
            }
            if (ignoreHidden && f.isHidden()) {
                continue;
            }
            if (f.getName().startsWith(".") && ignoreHidden) {
                continue;
            }
            acc.add(f.getAbsolutePath());
            if (recursive) {
                collectDirectories(f, recursive, ignoreHidden, acc);
            }
        }
    }

    private static String virtualPath(File dataRoot, File dir) {
        try {
            String d = dataRoot.getCanonicalPath();
            String p = dir.getCanonicalPath();
            if (p.startsWith(d)) {
                String rel = p.substring(d.length()).replace(File.separatorChar, '/');
                return "data" + rel;
            }
        } catch (IOException e) {
            // fall through
        }
        return dir.getAbsolutePath();
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
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
}