package com.github.naofum.minstrel.plugins.commander;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.os.Environment;

public class Storage {

    // app-specific external dir is used as the primary writable storage,
    // avoiding scoped-storage permission issues on modern Android
    public static String getPrimaryPath(Context context) {
        try {
            File dir = context.getExternalFilesDir(null);
            if (dir != null) {
                return dir.getAbsolutePath();
            }
        } catch (Exception e) {
            // nop
        }
        return context.getCacheDir().getAbsolutePath();
    }

    public static String[] getStoragePaths(Context context) {
        List<String> paths = new ArrayList<String>();
        String primary = getPrimaryPath(context);
        if (!paths.contains(primary)) {
            paths.add(primary);
        }
        String external = Environment.getExternalStorageDirectory().getAbsolutePath();
        if (!paths.contains(external)) {
            paths.add(external);
        }
        return paths.toArray(new String[0]);
    }

    public static List<StorageInfo> getStoragePathsInfo(Context context) {
        List<StorageInfo> list = new ArrayList<StorageInfo>();
        String primary = getPrimaryPath(context);
        list.add(new StorageInfo(primary, false, false, -1));
        String external = Environment.getExternalStorageDirectory().getAbsolutePath();
        if (!external.equals(primary)) {
            list.add(new StorageInfo(external, false, true, 1));
        }
        return list;
    }
}