package minstrel.server.librarian;

import java.util.LinkedHashMap;
import java.util.Map;

/** A format of a publication (port of Android {@code Format}). */
public class Format {

    public String name = "";
    public String version = "";
    public Map<String, String> metadata = new LinkedHashMap<>();

    public Format(String name, String version) {
        this.name = name;
        this.version = version;
    }

    public Format(String name) {
        this(name, "");
    }

    public void addMetadatum(String key, String value) {
        metadata.put(key, value);
    }

    public String getMetadatum(String key) {
        return metadata.get(key);
    }

    public Map<String, Object> toJson() {
        Map<String, Object> obj = new LinkedHashMap<>();
        obj.put("name", name);
        obj.put("version", version);
        obj.put("metadata", new LinkedHashMap<>(metadata));
        return obj;
    }
}