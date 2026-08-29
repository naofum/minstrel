package com.github.naofum.minstrel.plugins;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.github.naofum.minstrel.plugins.librarian.FormatHandler;
import com.github.naofum.minstrel.plugins.librarian.FormatHandlerABZ;
import com.github.naofum.minstrel.plugins.librarian.FormatHandlerCBZ;
import com.github.naofum.minstrel.plugins.librarian.FormatHandlerEPUB;
import com.github.naofum.minstrel.plugins.librarian.JSONPrintable;
import com.github.naofum.minstrel.plugins.librarian.Publication;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@CapacitorPlugin(name = "Librarian")
public class LibrarianPlugin extends Plugin {

    public static final String FORMAT_EPUB = "epub";
    public static final String FORMAT_ABZ  = "abz";
    public static final String FORMAT_CBZ  = "cbz";

    @PluginMethod
    public void librarian(final PluginCall call) {
        String mode = call.getString("mode", "");
        String path = call.getString("path", "");
        JSONObject parameters = parseOption(call.getString("args", "{}"));

        new Thread(new Runnable() {
            public void run() {
                try {
                    String result = handle(mode, path, parameters);
                    JSObject ret = new JSObject();
                    ret.put("value", result);
                    call.resolve(ret);
                } catch (Exception e) {
                    call.reject("Exception " + e);
                }
            }
        }).start();
    }

    private String handle(String mode, String path, JSONObject parameters) throws Exception {
        boolean entireVolume = parameters.optBoolean("entireVolume", false);
        String format = parameters.optString("format", FORMAT_EPUB);
        JSONObject formats = parameters.optJSONObject("formats");
        boolean ignoreHidden = parameters.optBoolean("ignoreHidden", true);
        JSONArray paths = parameters.optJSONArray("paths");
        boolean recursive = parameters.optBoolean("recursive", true);
        String thumbnailDirectory = parameters.optString("thumbnailDirectory", "");
        int thumbnailHeight = parameters.optInt("thumbnailHeight", 400);
        int thumbnailWidth = parameters.optInt("thumbnailWidth", 300);

        if (mode.equals("createThumbnailDirectory")) {
            ensureThumbnailDirectoryExist(thumbnailDirectory, false);
            return "";
        }
        if (mode.equals("emptyThumbnailDirectory")) {
            ensureThumbnailDirectoryExist(thumbnailDirectory, true);
            return "";
        }

        if (mode.equals("full") || mode.equals("single")) {
            ensureThumbnailDirectoryExist(thumbnailDirectory, false);
            List<Publication> publications = new ArrayList<Publication>();

            if (mode.equals("single")) {
                FormatHandler fh = getFormatHandler(format);
                if (fh != null) {
                    fh.setThumbnailInfo(thumbnailDirectory, thumbnailWidth, thumbnailHeight);
                    File f = new File(path);
                    if (f.isFile()) {
                        discoverSingleFile(f, fh, publications);
                    }
                }
            }

            if (mode.equals("full")) {
                String[] scanPaths = paths != null ? JSONArrayToStringArray(paths) : null;
                if ((scanPaths != null) && (scanPaths.length > 0)) {
                    if (formats != null) {
                        Iterator<String> iter = formats.keys();
                        while (iter.hasNext()) {
                            String f = iter.next();
                            JSONArray extensions = formats.optJSONArray(f);
                            String[] extensions2 = JSONArrayToStringArray(extensions);
                            FormatHandler fh = getFormatHandler(f);
                            if (fh != null) {
                                fh.addAllowedLowercasedExtensions(extensions2);
                                fh.setThumbnailInfo(thumbnailDirectory, thumbnailWidth, thumbnailHeight);
                                for (String sp : scanPaths) {
                                    File dir = new File(sp);
                                    if (dir.isDirectory()) {
                                        discoverPublications(dir, fh, publications, recursive, ignoreHidden);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            finalizePublications(publications, thumbnailDirectory);
            return stringify(publications, "publications");
        }

        if (mode.equals("custom")) {
            FormatHandler fh = getFormatHandler(format);
            if (fh != null) {
                return fh.customAction(path, parameters);
            }
            return "";
        }

        return "";
    }

    private void discoverPublications(File dir, FormatHandler fh, List<Publication> acc, boolean recursive, boolean ignoreHidden) {
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
                    discoverPublications(child, fh, acc, recursive, ignoreHidden);
                }
            } else if (fh.isParsable(child.getName().toLowerCase())) {
                discoverSingleFile(child, fh, acc);
            }
        }
    }

    private void discoverSingleFile(File f, FormatHandler fh, List<Publication> publications) {
        Publication p = fh.parseFile(f);
        if (p.isValid()) {
            publications.add(p);
        }
    }

    private void finalizePublications(List<Publication> publications, String thumbnailDirectory) {
        for (Publication p : publications) {
            try {
                if (p.getFormats().isEmpty()) {
                    continue;
                }
                com.github.naofum.minstrel.plugins.librarian.Format defaultFormat = p.getFormats().get(0);
                String defaultFormatTitle = defaultFormat.getMetadatum("title");
                String relThumb = defaultFormat.getMetadatum("relativePathThumbnail");
                p.setMainFormat(defaultFormat.getName());
                if ((defaultFormatTitle != null) && (!defaultFormatTitle.equals(""))) {
                    p.setTitle(defaultFormatTitle);
                }
                if ((relThumb != null) && (!relThumb.equals(""))) {
                    p.setRelativePathThumbnail(relThumb);
                    p.setAbsolutePathThumbnail((new File(thumbnailDirectory, relThumb)).getAbsolutePath());
                }
            } catch (Exception e) {
                // nop
            }
        }
    }

    private FormatHandler getFormatHandler(String format) {
        if (FORMAT_ABZ.equals(format)) {
            return new FormatHandlerABZ();
        }
        if (FORMAT_CBZ.equals(format)) {
            return new FormatHandlerCBZ();
        }
        if (FORMAT_EPUB.equals(format)) {
            return new FormatHandlerEPUB();
        }
        return null;
    }

    private boolean ensureThumbnailDirectoryExist(String pathThumbnailDirectory, boolean delete) {
        try {
            File thumbnailDirectory = new File(pathThumbnailDirectory);

            if ((delete) && (thumbnailDirectory.exists())) {
                String[] thumbnailFileNames = thumbnailDirectory.list();
                if (thumbnailFileNames != null) {
                    for (String thumbnailFileName : thumbnailFileNames) {
                        new File(thumbnailDirectory, thumbnailFileName).delete();
                    }
                }
                final File to = new File(thumbnailDirectory.getAbsolutePath() + System.currentTimeMillis());
                thumbnailDirectory.renameTo(to);
                to.delete();
                thumbnailDirectory = null;
            }

            if (!thumbnailDirectory.exists()) {
                thumbnailDirectory = new File(pathThumbnailDirectory);
                thumbnailDirectory.mkdirs();
            }

            File nomediaFile = new File(thumbnailDirectory, ".nomedia");
            if (!nomediaFile.exists()) {
                nomediaFile.createNewFile();
            }

            return true;
        } catch (Exception e) {
            // nop
        }
        return false;
    }

    public static String stringify(List<?> items, String mainKey) {
        JSONObject obj = new JSONObject();
        try {
            JSONObject main = new JSONObject();
            JSONArray  arr  = new JSONArray();
            for (Object item : items) {
                JSONPrintable jItem = (JSONPrintable)item;
                arr.put(jItem.toJSONObject());
            }
            main.put("items", arr);
            obj.put(mainKey, main);
        } catch (Exception e) {
            // nop
        }
        return obj.toString();
    }

    private String[] JSONArrayToStringArray(JSONArray array) {
        if (array == null) {
            return null;
        }
        if (array.length() < 1) {
            return null;
        }
        String[] ret = new String[array.length()];
        for (int i = 0; i < array.length(); i++) {
            ret[i] = array.optString(i, "");
        }
        return ret;
    }

    private JSONObject parseOption(String option) {
        try {
            return new JSONObject(option == null ? "{}" : option);
        } catch (Exception e) {
            return new JSONObject();
        }
    }
}