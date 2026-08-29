package com.github.naofum.minstrel.plugins.librarian;

import java.util.HashMap;
import java.util.Map.Entry;

import org.json.JSONObject;

public class Format implements JSONPrintable {

    String                      name     = "";
    String                      version  = "";
    HashMap<String, String>     metadata = new HashMap<String, String>();

    public Format(String name, String version) {
        this.name    = name;
        this.version = version;
    }
    public Format(String name) {
        this(name, "");
    }

    public String getName() { return this.name; }
    public String getVersion() { return this.version; }

    public void setMetadata(HashMap<String, String> metadata) { this.metadata = metadata; }
    public HashMap<String, String> getMetadata() { return this.metadata; }
    public void addMetadatum(String name, String value) { this.metadata.put(name, value); }
    public String getMetadatum(String name) { return this.metadata.get(name); }

    public boolean equals(String otherName) { return this.name.equals(otherName); }
    public boolean equals(String otherName, String otherVersion) {
        return this.name.equals(otherName) && this.version.equals(otherVersion);
    }

    public JSONObject toJSONObject() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("name",    this.name);
            obj.put("version", this.version);
            JSONObject meta = new JSONObject();
            for (Entry<String, String> e : this.metadata.entrySet()) {
                meta.put(e.getKey(), e.getValue());
            }
            obj.put("metadata", meta);
        } catch (Exception e) {
            // nop
        }
        return obj;
    }
}