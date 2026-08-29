package minstrel.server.librarian;

import java.io.File;

/** Common contract for the format handlers. */
public interface FormatHandler {

    /** Sets the allowed (lowercased) file extensions used by {@link #isParsable}. */
    void addAllowedLowercasedExtensions(String[] extensions);

    /** Sets thumbnail generation parameters. */
    void setThumbnailInfo(String thumbnailDirectoryPath, int thumbnailWidth, int thumbnailHeight);

    /** True if the given lowercased file name is a supported publication. */
    boolean isParsable(String lowercasedFilename);

    /** Parses the given publication file, generating metadata and a thumbnail. */
    Publication parseFile(File file);
}