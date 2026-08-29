package com.github.naofum.minstrel.plugins.librarian;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.json.JSONObject;

public abstract class FormatHandlerAbstractZIP implements FormatHandler {

    public static final Pattern PATTERN_AUTHOR       = Pattern.compile("author:(.*)");
    public static final Pattern PATTERN_COVER        = Pattern.compile("cover:(.*)");
    public static final Pattern PATTERN_DURATION     = Pattern.compile("duration:(.*)");
    public static final Pattern PATTERN_LANGUAGE     = Pattern.compile("language:(.*)");
    public static final Pattern PATTERN_NARRATOR     = Pattern.compile("narrator:(.*)");
    public static final Pattern PATTERN_PLAYLIST     = Pattern.compile("playlist:(.*)");
    public static final Pattern PATTERN_SERIES       = Pattern.compile("series:(.*)");
    public static final Pattern PATTERN_SERIES_INDEX = Pattern.compile("seriesindex:(.*)");
    public static final Pattern PATTERN_TITLE        = Pattern.compile("title:(.*)");
    // read in chunks of 4 KB
    protected static final int   BUFFER_SIZE          = 4096;

    protected String thumbnailDirectoryPath;
    protected int    thumbnailWidth;
    protected int    thumbnailHeight;
    private String[] allowedLowercasedExtensions;
    private String   formatName;

    public FormatHandlerAbstractZIP() {
        this.allowedLowercasedExtensions = null;
    }

    public void addAllowedLowercasedExtensions(String[] allowedLowercasedExtensions) {
        this.allowedLowercasedExtensions = allowedLowercasedExtensions;
    }

    public void setThumbnailInfo(String thumbnailDirectoryPath, int thumbnailWidth, int thumbnailHeight) {
        this.thumbnailDirectoryPath = thumbnailDirectoryPath;
        this.thumbnailWidth         = thumbnailWidth;
        this.thumbnailHeight        = thumbnailHeight;
    }

    public String getFormatName() { return this.formatName; }
    protected void setFormatName(String formatName) { this.formatName = formatName; }

    public boolean isParsable(String lowercasedFilename) {
        if (this.allowedLowercasedExtensions == null) {
            return true;
        }
        return fileHasAllowedExtension(lowercasedFilename, this.allowedLowercasedExtensions);
    }

    public Publication parseFile(File file) {
        Publication p = new Publication();
        String name   = file.getName();
        String ap     = file.getAbsolutePath();
        String id     = Integer.toHexString(ap.hashCode());
        p.setAbsolutePath(ap);
        p.setRelativePath(name);
        p.setID(id);
        p.setSize(file.length());
        p.setTitle("File " + name);
        return p;
    }

    public String customAction(String path, JSONObject parameters) {
        return "";
    }

    // get the content of a given ZipEntry as a string
    protected String getZipEntryText(ZipFile zipFile, ZipEntry ze) {
        String toReturn = "";
        try {
            BufferedInputStream is = new BufferedInputStream(zipFile.getInputStream(ze));
            byte[] bytes = new byte[(int)(ze.getSize())];
            is.read(bytes, 0, bytes.length);
            is.close();
            toReturn = new String(bytes);
        } catch (Exception e) {
            // nothing
        }
        return toReturn;
    }

    protected boolean fileHasAllowedExtension(String lower, String[] array) {
        for (String ext : array) {
            if (lower.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    protected List<ZipAsset> getSortedListOfAssets(String path, String[] allowedExtensions) {
        List<ZipAsset> assets = new ArrayList<ZipAsset>();
        try {
            ZipFile zipFile = new ZipFile(new File(path), ZipFile.OPEN_READ);
            Enumeration<? extends ZipEntry> zipFileEntries = zipFile.entries();
            while (zipFileEntries.hasMoreElements()) {
                ZipEntry ze   = zipFileEntries.nextElement();
                String   name = ze.getName();
                String   lower = name.toLowerCase();
                if (this.fileHasAllowedExtension(lower, allowedExtensions)) {
                    assets.add(new ZipAsset(name, null));
                }
            }
            zipFile.close();
            Collections.sort(assets);
        } catch (Exception e) {
            // nop
        }
        return assets;
    }

    // create a thumbnail image from the given cover image
    protected void extractCover(File f, Format format, String publicationID) {
        String destinationName = publicationID + "." + format.getName() + ".png";
        String entryName       = format.getMetadatum("internalPathCover");

        if ((entryName == null) || (entryName.equals(""))) {
            format.addMetadatum("relativePathThumbnail", "");
            return;
        }

        try {
            ZipFile zipFile = new ZipFile(f, ZipFile.OPEN_READ);
            ZipEntry entry  = zipFile.getEntry(entryName);
            if (entry == null) {
                format.addMetadatum("relativePathThumbnail", "");
                zipFile.close();
                return;
            }

            File destFile          = new File(this.thumbnailDirectoryPath, "orig-" + destinationName);
            String destinationPath = destFile.getAbsolutePath();

            BufferedInputStream is = new BufferedInputStream(zipFile.getInputStream(entry));
            int numberOfBytesRead;
            byte data[] = new byte[BUFFER_SIZE];

            FileOutputStream fos      = new FileOutputStream(destFile);
            BufferedOutputStream dest = new BufferedOutputStream(fos, BUFFER_SIZE);

            while ((numberOfBytesRead = is.read(data, 0, BUFFER_SIZE)) > -1) {
                dest.write(data, 0, numberOfBytesRead);
            }
            dest.flush();
            dest.close();
            is.close();
            fos.close();

            // create thumbnail
            FileInputStream fis = new FileInputStream(destinationPath);
            Bitmap imageBitmap  = BitmapFactory.decodeStream(fis);
            if (imageBitmap != null) {
                imageBitmap = Bitmap.createScaledBitmap(imageBitmap, this.thumbnailWidth, this.thumbnailHeight, false);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                imageBitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
                byte[] imageData = baos.toByteArray();

                // write thumbnail to file
                File destFile2          = new File(this.thumbnailDirectoryPath, destinationName);
                FileOutputStream fos2   = new FileOutputStream(destFile2);
                fos2.write(imageData, 0, imageData.length);
                fos2.flush();
                fos2.close();
                baos.close();

                // set relativePathThumbnail
                format.addMetadatum("relativePathThumbnail", destinationName);
            }
            fis.close();

            // close ZIP
            zipFile.close();

            // delete original cover
            destFile.delete();

        } catch (Exception e) {
            // nop
        }
    }

    // parse metadata file in "key: value" lines format
    protected boolean parseMetadataFile(ZipFile zipFile, ZipEntry ze, Format format) {
        try {
            String   name     = ze.getName();
            String   metadata = getZipEntryText(zipFile, ze);
            Matcher  matcher;

            matcher = PATTERN_TITLE.matcher(metadata);
            if (matcher.find()) {
                format.addMetadatum("title", matcher.group(1).trim());
            }
            matcher = PATTERN_LANGUAGE.matcher(metadata);
            if (matcher.find()) {
                format.addMetadatum("language", matcher.group(1).trim());
            }
            matcher = PATTERN_AUTHOR.matcher(metadata);
            if (matcher.find()) {
                format.addMetadatum("author", matcher.group(1).trim());
            }
            matcher = PATTERN_NARRATOR.matcher(metadata);
            if (matcher.find()) {
                format.addMetadatum("narrator", matcher.group(1).trim());
            }
            matcher = PATTERN_DURATION.matcher(metadata);
            if (matcher.find()) {
                format.addMetadatum("duration", matcher.group(1).trim());
            }
            matcher = PATTERN_SERIES.matcher(metadata);
            if (matcher.find()) {
                format.addMetadatum("series", matcher.group(1).trim());
            }
            matcher = PATTERN_SERIES_INDEX.matcher(metadata);
            if (matcher.find()) {
                format.addMetadatum("seriesIndex", matcher.group(1).trim());
            }
            matcher = PATTERN_PLAYLIST.matcher(metadata);
            if (matcher.find()) {
                File w = new File(new File(name).getParent(), matcher.group(1).trim());
                format.addMetadatum("internalPathPlaylist", w.getAbsolutePath().substring(1));
            }
            matcher = PATTERN_COVER.matcher(metadata);
            if (matcher.find()) {
                File w = new File(new File(name).getParent(), matcher.group(1).trim());
                format.addMetadatum("internalPathCover", w.getAbsolutePath().substring(1));
            }

            return true;
        } catch (Exception e) {
            // nop
            return false;
        }
    }
}