package minstrel.server.librarian;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * CBZ metadata parsing (port of Android {@code FormatHandlerCBZ}).
 */
public class FormatHandlerCBZ extends AbstractFormatHandler {

    private static final String[] IMAGE_EXTENSIONS = {
            ".apng", ".bmp", ".gif", ".jpeg", ".jpg", ".png", ".svg", ".tiff"
    };

    public FormatHandlerCBZ() {
    }

    public Publication parseFile(File file) {
        Publication p = basePublication(file);
        Format format = new Format("cbz");

        List<String> assets = new ArrayList<>();
        String zeCover = null;
        String zePlaylist = null;
        boolean foundCover = false;
        boolean foundPlaylist = false;

        try (ZipFile zip = openZip(file)) {
            Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                ZipEntry ze = en.nextElement();
                String name = ze.getName();
                String lower = name.toLowerCase();

                if (lower.endsWith("cover.jpg") || lower.endsWith("cover.png")) {
                    p.isValid = true;
                    zeCover = name;
                    assets.add(name);
                } else if (lower.endsWith("playlist.txt")) {
                    zePlaylist = name;
                } else if (fileHasAllowedExtension(lower, IMAGE_EXTENSIONS)) {
                    p.isValid = true;
                    assets.add(name);
                } else if (lower.endsWith("metadata.txt")) {
                    p.isValid = true;
                    if (parseMetadataFile(zip, ze, format)) {
                        if (format.getMetadatum("internalPathPlaylist") != null) {
                            foundPlaylist = true;
                        }
                        if (format.getMetadatum("internalPathCover") != null) {
                            foundCover = true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            p.isValid = false;
        }

        if (!foundCover) {
            if (zeCover != null) {
                format.addMetadatum("internalPathCover", zeCover);
            } else if (!assets.isEmpty()) {
                Collections.sort(assets);
                format.addMetadatum("internalPathCover", assets.get(0));
            }
        }
        if (!foundPlaylist && zePlaylist != null) {
            format.addMetadatum("internalPathPlaylist", zePlaylist);
        }
        format.addMetadatum("numberOfAssets", "" + assets.size());

        p.formats.add(format);
        extractCover(file, format, p.id);
        return p;
    }

    /**
     * Custom action: sorted list of image assets, optionally driven by a
     * playlist.txt (filename / duration / title triples).
     */
    public List<ZipAsset> getSortedListOfAssets(File file, Map<String, Object> args) {
        String playlistEntry = args.get("internalPathPlaylist") == null ? null : args.get("internalPathPlaylist").toString();
        if (playlistEntry != null && !playlistEntry.isEmpty()) {
            return parseImagePlaylist(file, playlistEntry);
        }
        return getSortedListOfAssets(file, IMAGE_EXTENSIONS);
    }

    private List<ZipAsset> parseImagePlaylist(File file, String playlistEntry) {
        List<ZipAsset> assets = new ArrayList<>();
        try (ZipFile zip = openZip(file)) {
            ZipEntry ze = zip.getEntry(playlistEntry);
            if (ze == null) {
                return assets;
            }
            String[] lines = readEntryText(zip, ze).split("\n");
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                Map<String, String> meta = new LinkedHashMap<>();
                if (i + 1 < lines.length) {
                    String line2 = lines[i + 1].trim();
                    if (!line2.isEmpty() && !line2.startsWith("#")) {
                        i += 1;
                        meta.put("duration", line2);
                        if (i + 1 < lines.length) {
                            String line3 = lines[i + 1].trim();
                            if (!line3.isEmpty() && !line3.startsWith("#")) {
                                i += 1;
                                meta.put("title", line3);
                            }
                        }
                    }
                }
                String entryPath = joinZipPath(parentDir(playlistEntry), line);
                assets.add(new ZipAsset(entryPath, meta));
            }
        } catch (Exception e) {
            // nop
        }
        return assets;
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

    private Publication basePublication(File file) {
        Publication p = new Publication();
        String name = file.getName();
        p.absolutePath = file.getPath();
        p.relativePath = name;
        p.id = Integer.toHexString(p.absolutePath.hashCode());
        p.size = file.length();
        p.title = "File " + name;
        return p;
    }
}