package com.github.naofum.minstrel.plugins;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;

import androidx.activity.result.ActivityResult;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.github.naofum.minstrel.plugins.commander.Storage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * SAF-based book importer.
 *
 *  - importDocuments():  ACTION_OPEN_DOCUMENT (multi-select single files)
 *  - importDirectory():  ACTION_OPEN_DOCUMENT_TREE (a whole folder, permission persisted)
 *  - handleOpenWithIntent(): called by MainActivity when the app is opened
 *    with an ACTION_VIEW intent from a file manager ("Open with Minstrel").
 *
 * Selected books are copied into the app library directory
 * (files/minstrel/) so the existing path-based Librarian/Unzipper
 * pipeline keeps working unchanged.
 */
@CapacitorPlugin(name = "Importer")
public class ImporterPlugin extends Plugin {

    private static final String[] BOOK_EXTENSIONS = { ".epub", ".cbz", ".abz", ".zip" };

    private JSObject pendingOpenRequest = null;

    //--------------------------------------------------------------------------
    // plugin methods
    //--------------------------------------------------------------------------

    @PluginMethod
    public void importDocuments(final PluginCall call) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/epub+zip",
                "application/x-cbz",
                "application/vnd.comicbook+zip",
                "application/zip",
                "application/octet-stream" });
        startActivityForResult(call, intent, "openDocumentResult");
    }

    @PluginMethod
    public void importDirectory(final PluginCall call) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(call, intent, "openTreeResult");
    }

    @PluginMethod
    public void getPendingOpenRequest(final PluginCall call) {
        JSObject ret = new JSObject();
        if (pendingOpenRequest != null) {
            ret.put("request", pendingOpenRequest);
            pendingOpenRequest = null;
        }
        call.resolve(ret);
    }

    //--------------------------------------------------------------------------
    // activity results
    //--------------------------------------------------------------------------

    @ActivityCallback
    public void openDocumentResult(PluginCall call, ActivityResult result) {
        if ((result.getResultCode() != Activity.RESULT_OK) || (result.getData() == null)) {
            call.resolve(new JSObject());
            return;
        }
        List<String> imported = new ArrayList<String>();
        Intent data = result.getData();
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                File f = copyUriToLibrary(data.getClipData().getItemAt(i).getUri());
                if (f != null) {
                    imported.add(f.getName());
                }
            }
        } else if (data.getData() != null) {
            File f = copyUriToLibrary(data.getData());
            if (f != null) {
                imported.add(f.getName());
            }
        }
        JSObject ret = new JSObject();
        ret.put("imported", imported.toArray(new String[0]));
        call.resolve(ret);
    }

    @ActivityCallback
    public void openTreeResult(PluginCall call, ActivityResult result) {
        if ((result.getResultCode() != Activity.RESULT_OK) || (result.getData() == null)) {
            call.resolve(new JSObject());
            return;
        }
        Uri treeUri = result.getData().getData();
        List<String> imported = new ArrayList<String>();
        try {
            getContext().getContentResolver().takePersistableUriPermission(
                    treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            collectBooksFromTree(treeUri, imported, 0);
        } catch (Exception e) {
            // nop
        }
        JSObject ret = new JSObject();
        ret.put("imported", imported.toArray(new String[0]));
        call.resolve(ret);
    }

    //--------------------------------------------------------------------------
    // open with (called from MainActivity)
    //--------------------------------------------------------------------------

    public void handleOpenWithIntent(final Uri uri) {
        if (uri == null) {
            return;
        }
        new Thread(new Runnable() {
            public void run() {
                File f = copyUriToLibrary(uri);
                JSObject request = new JSObject();
                request.put("name", (f != null) ? f.getName() : "");
                if (f != null) {
                    request.put("path", f.getAbsolutePath());
                }
                pendingOpenRequest = request;
                notifyListeners("openRequest", request);
            }
        }).start();
    }

    //--------------------------------------------------------------------------
    // helpers
    //--------------------------------------------------------------------------

    private File getLibraryDir() {
        File dir = new File(Storage.getPrimaryPath(getContext()), "minstrel");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private String getDisplayName(Uri uri) {
        try {
            ContentResolver resolver = getContext().getContentResolver();
            Cursor cursor = resolver.query(uri, null, null, null, null);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        if ((idx >= 0) && (!cursor.isNull(idx))) {
                            String name = cursor.getString(idx);
                            if ((name != null) && (!name.isEmpty())) {
                                return name;
                            }
                        }
                    }
                } finally {
                    cursor.close();
                }
            }
        } catch (Exception e) {
            // nop
        }
        String last = uri.getLastPathSegment();
        return (last != null) ? last : "book";
    }

    private File copyUriToLibrary(Uri uri) {
        try {
            String name = getDisplayName(uri);
            name = name.replaceAll("[\\\\/:*?\"<>|]", "_");
            if (!hasBookExtension(name)) {
                return null;
            }
            File dest = uniqueFile(getLibraryDir(), name);
            InputStream is = getContext().getContentResolver().openInputStream(uri);
            if (is == null) {
                return null;
            }
            OutputStream os = new FileOutputStream(dest);
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = is.read(buf)) > 0) {
                os.write(buf, 0, n);
            }
            os.flush();
            os.close();
            is.close();
            return dest;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean hasBookExtension(String name) {
        String lower = name.toLowerCase();
        for (String ext : BOOK_EXTENSIONS) {
            if (lower.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    private File uniqueFile(File dir, String name) {
        File f = new File(dir, name);
        if (!f.exists()) {
            return f;
        }
        int dot = name.lastIndexOf('.');
        String base = (dot > 0) ? name.substring(0, dot) : name;
        String ext  = (dot > 0) ? name.substring(dot) : "";
        for (int i = 1; i < 1000; i++) {
            File candidate = new File(dir, base + "_" + i + ext);
            if (!candidate.exists()) {
                return candidate;
            }
        }
        return f;
    }

    private void collectBooksFromTree(Uri treeUri, List<String> imported, int depth) {
        if (depth > 6) {
            return;
        }
        ContentResolver resolver = getContext().getContentResolver();
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri, DocumentsContract.getTreeDocumentId(treeUri));
        try {
            Cursor cursor = resolver.query(childrenUri, new String[]{
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE }, null, null, null);
            if (cursor == null) {
                return;
            }
            List<Uri> subdirs = new ArrayList<Uri>();
            try {
                while (cursor.moveToNext()) {
                    int idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
                    int nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
                    int mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE);
                    String id = (idIdx >= 0) ? cursor.getString(idIdx) : null;
                    String name = (nameIdx >= 0) ? cursor.getString(nameIdx) : null;
                    String mime = (mimeIdx >= 0) ? cursor.getString(mimeIdx) : null;
                    if (id == null) {
                        continue;
                    }
                    Uri docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id);
                    if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                        subdirs.add(docUri);
                    } else if ((name != null) && (hasBookExtension(name))) {
                        File f = copyUriToLibrary(docUri);
                        if (f != null) {
                            imported.add(f.getName());
                        }
                    }
                }
            } finally {
                cursor.close();
            }
            for (Uri sub : subdirs) {
                collectBooksFromTree(sub, imported, depth + 1);
            }
        } catch (Exception e) {
            // nop
        }
    }
}