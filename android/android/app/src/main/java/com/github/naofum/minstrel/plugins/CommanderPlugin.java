package com.github.naofum.minstrel.plugins;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.github.naofum.minstrel.plugins.commander.Storage;
import com.github.naofum.minstrel.plugins.commander.StorageInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

@CapacitorPlugin(name = "Commander")
public class CommanderPlugin extends Plugin {

    public static final String COMMAND_TOAST               = "toast";
    public static final String COMMAND_DIM                 = "dim";
    public static final String COMMAND_SET_BRIGHTNESS      = "setbrightness";
    public static final String COMMAND_GET_BRIGHTNESS      = "getbrightness";
    public static final String COMMAND_ORIENT              = "orient";
    public static final String COMMAND_OPEN_EXTERNAL_URL   = "openExternalURL";
    public static final String COMMAND_FILESYSTEM_INFO     = "filesystemInfo";
    public static final String COMMAND_CHECK_FILE_EXISTS   = "checkFileExists";
    public static final String COMMAND_DELETE_RELATIVE     = "deleteRelative";
    public static final String COMMAND_DELETE_ABSOLUTE     = "deleteAbsolute";
    public static final String COMMAND_WRITE_TO_FILE       = "writeToFile";
    public static final String COMMAND_COPY                = "copy";
    public static final String COMMAND_MOVE                = "move";
    public static final String COMMAND_LIST_SUBDIRECTORIES = "listSubdirectories";
    public static final String COMMAND_COPY_FROM_ASSETS_WWW = "copyFromAssetsWWW";

    @PluginMethod
    public void command(final PluginCall call) {
        String command = call.getString("command", "");
        JSONObject parameters = parseOption(call.getString("option", "{}"));

        try {
            String result = dispatch(command, parameters);
            JSObject ret = new JSObject();
            ret.put("value", result);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Exception " + e);
        }
    }

    private String dispatch(String command, JSONObject parameters) throws Exception {
        final Activity activity = getActivity();

        if (COMMAND_TOAST.equals(command)) {
            activity.runOnUiThread(new Runnable() {
                public void run() {
                    Toast.makeText(activity.getApplicationContext(),
                            parameters.optString("message", ""), Toast.LENGTH_LONG).show();
                }
            });
            return "";
        }
        if (COMMAND_DIM.equals(command)) {
            final boolean dim = parameters.optBoolean("dim", false);
            activity.runOnUiThread(new Runnable() {
                public void run() {
                    View rootView = activity.getWindow().getDecorView().getRootView();
                    if (rootView != null) {
                        if (dim) {
                            rootView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
                        } else {
                            rootView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
                        }
                    }
                }
            });
            return "";
        }
        if (COMMAND_SET_BRIGHTNESS.equals(command)) {
            final float brightness = (float)Double.parseDouble(parameters.optString("value", "1.0"));
            activity.runOnUiThread(new Runnable() {
                public void run() {
                    WindowManager.LayoutParams lp = activity.getWindow().getAttributes();
                    lp.screenBrightness = brightness;
                    activity.getWindow().setAttributes(lp);
                }
            });
            return "";
        }
        if (COMMAND_GET_BRIGHTNESS.equals(command)) {
            WindowManager.LayoutParams lp = activity.getWindow().getAttributes();
            return "" + lp.screenBrightness;
        }
        if (COMMAND_ORIENT.equals(command)) {
            String value = parameters.optString("value", "auto");
            if ("portrait".equals(value)) {
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
            } else if ("landscape".equals(value)) {
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            } else {
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            }
            return "";
        }
        if (COMMAND_OPEN_EXTERNAL_URL.equals(command)) {
            final String url = parameters.optString("url", "http://www.readbeyond.it/");
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            return "";
        }
        if (COMMAND_FILESYSTEM_INFO.equals(command)) {
            return getFilesystemInfoJSONString();
        }
        if (COMMAND_CHECK_FILE_EXISTS.equals(command)) {
            if (doesFileExist(parameters.optString("path", null))) {
                return "fileexists";
            }
            return "filedoesnotexist";
        }
        if (COMMAND_DELETE_RELATIVE.equals(command)) {
            delete(new File(getBaseDir(), parameters.optString("path", null)));
            return "";
        }
        if (COMMAND_DELETE_ABSOLUTE.equals(command)) {
            delete(new File(parameters.optString("path", null)));
            return "";
        }
        if (COMMAND_COPY.equals(command)) {
            copyFile(parameters.optString("source", null), parameters.optString("destination", null));
            return "";
        }
        if (COMMAND_MOVE.equals(command)) {
            moveFile(parameters.optString("source", null), parameters.optString("destination", null));
            return "";
        }
        if (COMMAND_WRITE_TO_FILE.equals(command)) {
            return writeStringToFile(parameters.optString("destination", null), parameters.optString("string", null));
        }
        if (COMMAND_LIST_SUBDIRECTORIES.equals(command)) {
            return getDirectoryListingJSONString(parameters.optString("path", null),
                    parameters.optBoolean("recursive", false),
                    parameters.optBoolean("ignoreHidden", true));
        }
        if (COMMAND_COPY_FROM_ASSETS_WWW.equals(command)) {
            copyFromAssetsWWW(parameters.optString("source", null), parameters.optString("destination", null));
            return "";
        }
        return "";
    }

    private File getBaseDir() {
        return new File(Storage.getPrimaryPath(getContext()));
    }

    private String getFilesystemInfoJSONString() throws Exception {
        JSONObject obj = new JSONObject();
        obj.put("root", Storage.getPrimaryPath(getContext()));
        obj.put("separator", File.separator);
        obj.put("documentsDir", "");
        obj.put("cacheDir", "");
        JSONArray arr = new JSONArray();
        for (String s : Storage.getStoragePaths(getContext())) {
            arr.put(s);
        }
        obj.put("storageRoots", arr);
        return obj.toString();
    }

    private String getDirectoryListingJSONString(String path, boolean recursive, boolean ignoreHidden) throws Exception {
        JSONObject obj = new JSONObject();
        if ((path == null) || (path.equals(""))) {
            List<StorageInfo> storagePathsInfo = Storage.getStoragePathsInfo(getContext());
            for (StorageInfo si : storagePathsInfo) {
                JSONObject sp = new JSONObject();
                sp.put("internal", !si.removable);
                sp.put("subdirectories", getDirectoryListingJSONArray(si.path, recursive, ignoreHidden));
                obj.put(si.path, sp);
            }
        } else {
            JSONObject sp = new JSONObject();
            sp.put("internal", true);
            sp.put("subdirectories", getDirectoryListingJSONArray(path, recursive, ignoreHidden));
            obj.put(path, sp);
        }
        return obj.toString();
    }

    private JSONArray getDirectoryListingJSONArray(String path, boolean recursive, boolean ignoreHidden) throws Exception {
        List<String> acc = new ArrayList<String>();
        listRecursively(new File(normalizePath(path)), recursive, ignoreHidden, true, acc);
        JSONArray arr = new JSONArray();
        for (String s : acc) {
            arr.put(s);
        }
        return arr;
    }

    private void listRecursively(File path, boolean recursive, boolean ignoreHidden, boolean directoriesOnly, List<String> acc) {
        File[] files = path.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            if ((f.canRead()) && (!f.isHidden())) {
                String n = f.getName();
                if (!((n.startsWith(".")) && (ignoreHidden))) {
                    if (f.isDirectory()) {
                        if (directoriesOnly) {
                            acc.add(f.getAbsolutePath());
                        }
                        if (recursive) {
                            listRecursively(f, recursive, ignoreHidden, directoriesOnly, acc);
                        }
                    } else {
                        if (!directoriesOnly) {
                            acc.add(f.getAbsolutePath());
                        }
                    }
                }
            }
        }
    }

    private boolean doesFileExist(String path) {
        try {
            return new File(normalizePath(path)).exists();
        } catch (Exception e) {
            return false;
        }
    }

    private String writeStringToFile(String path, String contents) {
        try {
            File f = new File(normalizePath(path));
            FileWriter out = new FileWriter(f);
            out.write(contents);
            out.flush();
            out.close();
            return "filewritten";
        } catch (Exception e) {
            return "filenotwritten";
        }
    }

    private void copyFile(String sourcePath, String destinationPath) {
        try {
            File f = new File(normalizePath(sourcePath));
            if (f.exists()) {
                File d = new File(normalizePath(destinationPath));
                File destinationParent = d.getParentFile();
                destinationParent.mkdirs();
                FileInputStream inStream  = new FileInputStream(f);
                FileOutputStream outStream = new FileOutputStream(d);
                FileChannel inChannel  = inStream.getChannel();
                FileChannel outChannel = outStream.getChannel();
                inChannel.transferTo(0, inChannel.size(), outChannel);
                inStream.close();
                outStream.close();
            }
        } catch (Exception e) {
            // nop
        }
    }

    private void moveFile(String sourcePath, String destinationPath) {
        try {
            File f = new File(normalizePath(sourcePath));
            if (f.exists()) {
                File d = new File(normalizePath(destinationPath));
                File destinationParent = d.getParentFile();
                destinationParent.mkdirs();
                f.renameTo(d);
            }
        } catch (Exception e) {
            // nop
        }
    }

    private void copyFromAssetsWWW(String sourcePath, String destinationPath) {
        String source = "www" + File.separator + sourcePath;
        try {
            File d = new File(normalizePath(destinationPath));
            File destinationParent = d.getParentFile();
            destinationParent.mkdirs();
            final int BUFFER = 4096;
            BufferedInputStream is = new BufferedInputStream(getActivity().getApplicationContext().getAssets().open(source));
            int numberOfBytesRead;
            byte data[] = new byte[BUFFER];
            FileOutputStream fos = new FileOutputStream(d);
            BufferedOutputStream dest = new BufferedOutputStream(fos, BUFFER);
            while ((numberOfBytesRead = is.read(data, 0, BUFFER)) > -1) {
                dest.write(data, 0, numberOfBytesRead);
            }
            dest.flush();
            dest.close();
            is.close();
            fos.close();
        } catch (Exception e) {
            // nop
        }
    }

    private void delete(File f) {
        deleteRecursively(f);
    }

    private void deleteRecursively(File f) {
        if (f == null) {
            return;
        }
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        f.delete();
    }

    private String normalizePath(String path) {
        if (path == null) {
            return path;
        }
        if ((path.length() > 0) && (path.startsWith(File.separator))) {
            return path;
        } else if ((path.length() > 7) && (path.startsWith("file://"))) {
            return path.substring(7);
        } else {
            return new File(getBaseDir(), path).getAbsolutePath();
        }
    }

    private JSONObject parseOption(String option) {
        try {
            return new JSONObject(option == null ? "{}" : option);
        } catch (Exception e) {
            return new JSONObject();
        }
    }
}