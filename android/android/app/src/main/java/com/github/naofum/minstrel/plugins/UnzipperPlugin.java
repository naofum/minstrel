package com.github.naofum.minstrel.plugins;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@CapacitorPlugin(name = "Unzipper")
public class UnzipperPlugin extends Plugin {

    public static final String ARGUMENT_MODE_ALL           = "all";
    public static final String ARGUMENT_MODE_ALL_NON_MEDIA = "allNonMedia";
    public static final String ARGUMENT_MODE_ALL_SMALL     = "allSmall";
    public static final String ARGUMENT_MODE_ALL_STRUCTURE = "allStructure";
    public static final String ARGUMENT_MODE_LIST          = "list";
    public static final String ARGUMENT_MODE_SELECTED      = "selected";

    public static final long DEFAULT_MAXIMUM_SIZE_FILE = 4194304;
    public static final int  BUFFER_SIZE               = 64 * 1024;

    @PluginMethod
    public void unzip(final PluginCall call) {
        String srcPath = call.getString("srcPath", "");
        String dstPath = call.getString("dstPath", "");
        String mode    = call.getString("mode", "");
        JSONObject parameters = parseOption(call.getString("args", "{}"));

        new Thread(new Runnable() {
            public void run() {
                try {
                    String result;
                    if (ARGUMENT_MODE_LIST.equals(mode)) {
                        result = listFile(srcPath);
                    } else {
                        result = unzipFile(srcPath, dstPath, mode, parameters);
                    }
                    JSObject ret = new JSObject();
                    ret.put("value", result);
                    call.resolve(ret);
                } catch (Exception e) {
                    call.reject("unzip failed: " + e.getMessage());
                }
            }
        }).start();
    }

    private String listFile(String inputZip) throws Exception {
        List<String> acc = new ArrayList<String>();
        ZipFile zipFile = new ZipFile(new File(inputZip), ZipFile.OPEN_READ);
        Enumeration<? extends ZipEntry> zipFileEntries = zipFile.entries();
        while (zipFileEntries.hasMoreElements()) {
            acc.add(zipFileEntries.nextElement().getName());
        }
        zipFile.close();
        Collections.sort(acc);
        return stringify(acc);
    }

    private String unzipFile(String inputZip, String destinationDirectory, String mode, JSONObject parameters) throws Exception {
        List<String> list        = new ArrayList<String>();
        List<String> decompressed = new ArrayList<String>();

        ZipFile zipFile = new ZipFile(new File(inputZip), ZipFile.OPEN_READ);
        File unzipDestinationDirectory = new File(destinationDirectory);
        unzipDestinationDirectory.mkdirs();

        if (ARGUMENT_MODE_ALL.equals(mode)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                list.add(entries.nextElement().getName());
            }
        }

        if (ARGUMENT_MODE_ALL_NON_MEDIA.equals(mode)) {
            String[] excludeExtensions = JSONArrayToStringArray(parameters.optJSONArray("excludeExtensions"));
            if (excludeExtensions != null) {
                Enumeration<? extends ZipEntry> entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    String name  = entries.nextElement().getName();
                    String lower = name.toLowerCase();
                    if (!isFile(lower, excludeExtensions)) {
                        list.add(name);
                    }
                }
            } else {
                Enumeration<? extends ZipEntry> entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    list.add(entries.nextElement().getName());
                }
            }
        }

        if (ARGUMENT_MODE_ALL_SMALL.equals(mode)) {
            long maximum_size = parameters.optLong("maximumFileSize", DEFAULT_MAXIMUM_SIZE_FILE);
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry ze = entries.nextElement();
                if (ze.getSize() <= maximum_size) {
                    list.add(ze.getName());
                }
            }
        }

        if (ARGUMENT_MODE_SELECTED.equals(mode)) {
            String[] entries = JSONArrayToStringArray(parameters.optJSONArray("entries"));
            if (entries != null) {
                for (String entry : entries) {
                    ZipEntry ze = zipFile.getEntry(entry);
                    if (ze != null) {
                        list.add(entry);
                    }
                }
            }
        }

        if (ARGUMENT_MODE_ALL_STRUCTURE.equals(mode)) {
            String[] extensions = JSONArrayToStringArray(parameters.optJSONArray("extensions"));
            if (extensions != null) {
                Enumeration<? extends ZipEntry> entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    String name  = entries.nextElement().getName();
                    String lower = name.toLowerCase();
                    if (isFile(lower, extensions)) {
                        list.add(name);
                    }
                }
            }
        }

        String canonicalDest = unzipDestinationDirectory.getCanonicalPath();
        for (String currentEntry : list) {
            ZipEntry entry = zipFile.getEntry(currentEntry);
            File destFile  = new File(unzipDestinationDirectory, currentEntry);
            String canonicalPath = destFile.getCanonicalPath();
            if (!canonicalPath.startsWith(canonicalDest)) {
                throw new SecurityException();
            }

            File destinationParent = destFile.getParentFile();
            destinationParent.mkdirs();

            if (!entry.isDirectory()) {
                BufferedInputStream is = new BufferedInputStream(zipFile.getInputStream(entry));
                int numberOfBytesRead;
                byte data[] = new byte[BUFFER_SIZE];
                FileOutputStream fos      = new FileOutputStream(destFile);
                BufferedOutputStream dest = new BufferedOutputStream(fos, BUFFER_SIZE);
                while ((numberOfBytesRead = is.read(data, 0, BUFFER_SIZE)) > -1) {
                    dest.write(data, 0, numberOfBytesRead);
                }
                dest.flush();
                dest.close();
                is.close();
                fos.close();
                decompressed.add(currentEntry);
            }
        }

        zipFile.close();
        return stringify(decompressed);
    }

    private String stringify(List<String> entries) {
        JSONObject obj = new JSONObject();
        try {
            JSONObject files = new JSONObject();
            JSONArray  items = new JSONArray();
            for (String s : entries) {
                items.put(s);
            }
            files.put("items", items);
            obj.put("files", files);
        } catch (Exception e) {
            // nop
        }
        return obj.toString();
    }

    private boolean isFile(String lower, String[] array) {
        for (String ext : array) {
            if (lower.endsWith(ext)) {
                return true;
            }
        }
        return false;
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