package minstrel.server.librarian;

import java.util.LinkedHashMap;
import java.util.Map;

/** An asset inside a ZIP (port of Android {@code ZipAsset}). */
public class ZipAsset implements Comparable<ZipAsset> {

    public String path;
    public Map<String, String> metadata;

    public ZipAsset(String path, Map<String, String> metadata) {
        this.path = path;
        this.metadata = metadata == null ? new LinkedHashMap<>() : metadata;
    }

    @Override
    public int compareTo(ZipAsset other) {
        return this.path.compareTo(other.path);
    }

    public Map<String, Object> toJson() {
        Map<String, Object> obj = new LinkedHashMap<>();
        obj.put("path", path);
        obj.put("metadata", new LinkedHashMap<>(metadata));
        return obj;
    }
}