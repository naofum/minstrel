package minstrel.server.librarian;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A publication discovered by the Librarian (port of Android {@code Publication}). */
public class Publication {

    public String id = "";
    public String absolutePath = "";
    public String relativePath = "";
    public String absolutePathThumbnail = "";
    public String relativePathThumbnail = "";
    public String title = "";
    public String mainFormat = "";
    public List<Format> formats = new ArrayList<>();
    public long size = 0;
    public boolean isSingleFile = true;
    public boolean isValid = false;

    public Map<String, Object> toJson() {
        Map<String, Object> obj = new LinkedHashMap<>();
        obj.put("id", id);
        obj.put("absolutePath", absolutePath);
        obj.put("relativePath", relativePath);
        obj.put("absolutePathThumbnail", absolutePathThumbnail);
        obj.put("relativePathThumbnail", relativePathThumbnail);
        obj.put("mainFormat", mainFormat);

        Map<String, Object> formatsJson = new LinkedHashMap<>();
        for (Format f : formats) {
            formatsJson.put(f.name, f.toJson());
        }
        obj.put("formats", formatsJson);

        obj.put("title", title);
        obj.put("size", size);
        obj.put("isSingleFile", isSingleFile);
        obj.put("isValid", isValid);
        return obj;
    }
}