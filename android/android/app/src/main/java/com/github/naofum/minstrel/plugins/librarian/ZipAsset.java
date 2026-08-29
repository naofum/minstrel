package com.github.naofum.minstrel.plugins.librarian;

import java.util.HashMap;

import org.json.JSONObject;

public class ZipAsset implements Comparable<ZipAsset>, JSONPrintable {

    String                  path     = "";
    HashMap<String, String> metadata = new HashMap<String, String>();

    public ZipAsset(String path, HashMap<String, String> metadata) {
        this.path = path;
        if (metadata != null) {
            this.metadata = metadata;
        }
    }

    public void setPath(String path) { this.path = path; }
    public String getPath() { return this.path; }
    public void setMetadata(HashMap<String, String> metadata) { this.metadata = metadata; }
    public HashMap<String, String> getMetadata() { return this.metadata; }

    public int compareTo(ZipAsset other) {
        return this.path.compareTo(other.getPath());
    }

    public JSONObject toJSONObject() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("path",     this.path);
            obj.put("metadata", new JSONObject(this.metadata));
        } catch (Exception e) {
            // nop
        }
        return obj;
    }
}