package com.github.naofum.minstrel.plugins.librarian;

import java.io.File;
import java.util.List;

import org.json.JSONObject;

public interface FormatHandler {

    /** Sets thumbnail generation parameters. */
    void setThumbnailInfo(String thumbnailDirectoryPath, int thumbnailWidth, int thumbnailHeight);

    /** Adds the allowed (lowercased) extensions. */
    void addAllowedLowercasedExtensions(String[] allowedLowercasedExtensions);

    /** True iff the given lowercased file name can be parsed. */
    boolean isParsable(String lowercasedFilename);

    /** Parses the given publication file, generating metadata and a thumbnail. */
    Publication parseFile(File file);

    /** Custom action (e.g. getSortedListOfAssets). */
    String customAction(String path, JSONObject parameters);
}