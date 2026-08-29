package minstrel.server.librarian;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Shared logic of the format handlers (port of Android
 * {@code FormatHandlerAbstractZIP} + {@code ThumbnailUtils}).
 */
public abstract class AbstractFormatHandler implements FormatHandler {

    private static final Pattern PATTERN_TITLE = Pattern.compile("title:(.*)");
    private static final Pattern PATTERN_LANGUAGE = Pattern.compile("language:(.*)");
    private static final Pattern PATTERN_AUTHOR = Pattern.compile("author:(.*)");
    private static final Pattern PATTERN_NARRATOR = Pattern.compile("narrator:(.*)");
    private static final Pattern PATTERN_DURATION = Pattern.compile("duration:(.*)");
    private static final Pattern PATTERN_SERIES = Pattern.compile("series:(.*)");
    private static final Pattern PATTERN_SERIES_INDEX = Pattern.compile("seriesindex:(.*)");
    private static final Pattern PATTERN_PLAYLIST = Pattern.compile("playlist:(.*)");
    private static final Pattern PATTERN_COVER = Pattern.compile("cover:(.*)");

    protected String thumbnailDirectoryPath = "minstrel/.thumbnails";
    protected int thumbnailWidth = 300;
    protected int thumbnailHeight = 400;
    private String[] allowedLowercasedExtensions;

    public void addAllowedLowercasedExtensions(String[] extensions) {
        this.allowedLowercasedExtensions = extensions;
    }

    public void setThumbnailInfo(String dir, int width, int height) {
        this.thumbnailDirectoryPath = dir;
        this.thumbnailWidth = width;
        this.thumbnailHeight = height;
    }

    public boolean isParsable(String lowercasedFilename) {
        if (allowedLowercasedExtensions == null) {
            return true;
        }
        for (String ext : allowedLowercasedExtensions) {
            if (lowercasedFilename.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    protected boolean fileHasAllowedExtension(String lower, String[] array) {
        if (array == null) {
            return false;
        }
        for (String ext : array) {
            if (lower.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // metadata.txt / playlist parsing
    // ------------------------------------------------------------------

    protected boolean parseMetadataFile(ZipFile zip, ZipEntry ze, Format format) {
        try {
            String name = ze.getName();
            String metadata = readEntryText(zip, ze);
            Matcher m;

            if ((m = PATTERN_TITLE.matcher(metadata)).find()) {
                format.addMetadatum("title", m.group(1).trim());
            }
            if ((m = PATTERN_LANGUAGE.matcher(metadata)).find()) {
                format.addMetadatum("language", m.group(1).trim());
            }
            if ((m = PATTERN_AUTHOR.matcher(metadata)).find()) {
                format.addMetadatum("author", m.group(1).trim());
            }
            if ((m = PATTERN_NARRATOR.matcher(metadata)).find()) {
                format.addMetadatum("narrator", m.group(1).trim());
            }
            if ((m = PATTERN_DURATION.matcher(metadata)).find()) {
                format.addMetadatum("duration", m.group(1).trim());
            }
            if ((m = PATTERN_SERIES.matcher(metadata)).find()) {
                format.addMetadatum("series", m.group(1).trim());
            }
            if ((m = PATTERN_SERIES_INDEX.matcher(metadata)).find()) {
                format.addMetadatum("seriesIndex", m.group(1).trim());
            }
            if ((m = PATTERN_PLAYLIST.matcher(metadata)).find()) {
                format.addMetadatum("internalPathPlaylist", joinZipPath(parentDir(name), m.group(1).trim()));
            }
            if ((m = PATTERN_COVER.matcher(metadata)).find()) {
                format.addMetadatum("internalPathCover", joinZipPath(parentDir(name), m.group(1).trim()));
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // asset listing (custom action "getSortedListOfAssets")
    // ------------------------------------------------------------------

    protected List<ZipAsset> getSortedListOfAssets(File file, String[] allowedExtensions) {
        List<ZipAsset> assets = new ArrayList<>();
        try (ZipFile zip = openZip(file)) {
            Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                ZipEntry ze = en.nextElement();
                String lower = ze.getName().toLowerCase();
                if (fileHasAllowedExtension(lower, allowedExtensions)) {
                    assets.add(new ZipAsset(ze.getName(), null));
                }
            }
        } catch (Exception e) {
            // nop
        }
        Collections.sort(assets);
        return assets;
    }

    // ------------------------------------------------------------------
    // thumbnail extraction
    // ------------------------------------------------------------------

    /**
     * Reads the cover entry from the ZIP, scales it to the configured
     * thumbnail size and writes a PNG into the thumbnail directory.
     */
    protected void extractCover(File file, Format format, String publicationID) {
        String entryName = format.getMetadatum("internalPathCover");
        if (entryName == null || entryName.isEmpty()) {
            format.addMetadatum("relativePathThumbnail", "");
            return;
        }
        String destinationName = publicationID + "." + format.name + ".png";
        try (ZipFile zip = openZip(file)) {
            ZipEntry entry = zip.getEntry(entryName);
            if (entry == null) {
                format.addMetadatum("relativePathThumbnail", "");
                return;
            }
            byte[] data;
            try (InputStream is = zip.getInputStream(entry)) {
                data = is.readAllBytes();
            }
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
            if (image == null) {
                format.addMetadatum("relativePathThumbnail", "");
                return;
            }
            BufferedImage scaled = new BufferedImage(thumbnailWidth, thumbnailHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = scaled.createGraphics();
            try {
                g.drawImage(image, 0, 0, thumbnailWidth, thumbnailHeight, null);
            } finally {
                g.dispose();
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(scaled, "png", baos);

            File dest = new File(thumbnailDirectoryPath, destinationName);
            File parent = dest.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            Files.write(dest.toPath(), baos.toByteArray());
            format.addMetadatum("relativePathThumbnail", destinationName);
        } catch (Exception e) {
            format.addMetadatum("relativePathThumbnail", "");
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    protected static String readEntryText(ZipFile zip, ZipEntry ze) throws IOException {
        String text;
        try (InputStream is = zip.getInputStream(ze)) {
            text = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        return text;
    }

    protected static ZipFile openZip(File f) throws IOException {
        try {
            return new ZipFile(f, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return new ZipFile(f, java.nio.charset.Charset.forName("CP932"));
        }
    }

    private static String parentDir(String name) {
        int slash = name.lastIndexOf('/');
        if (slash < 0) {
            return "";
        }
        return name.substring(0, slash);
    }

    private static String joinZipPath(String parent, String child) {
        if (parent == null || parent.isEmpty()) {
            return child;
        }
        return parent + "/" + child;
    }
}