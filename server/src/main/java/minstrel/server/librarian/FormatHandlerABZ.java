package minstrel.server.librarian;

import java.io.File;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * ABZ metadata parsing (port of Android {@code FormatHandlerABZ}).
 */
public class FormatHandlerABZ extends AbstractFormatHandler {

    private static final String[] AUDIO_EXTENSIONS = {
            ".aac", ".flac", ".m4a", ".mp3", ".mp4", ".oga", ".ogg", ".wav", ".webm"
    };
    private static final String M3U_HEADER = "#EXTM3U";
    private static final String M3U_PREAMBLE = "#EXTINF:";
    private static final Pattern M3U_LINE_PATTERN = Pattern.compile("#EXTINF:([0-9]+),(.*)");

    public FormatHandlerABZ() {
    }

    public Publication parseFile(File file) {
        Publication p = basePublication(file);
        Format format = new Format("abz");

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
                    zeCover = name;
                } else if (lower.endsWith(".m3u")) {
                    zePlaylist = name;
                } else if (fileHasAllowedExtension(lower, AUDIO_EXTENSIONS)) {
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

        if (!foundCover && zeCover != null) {
            format.addMetadatum("internalPathCover", zeCover);
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
     * Custom action: sorted list of audio assets, optionally driven by an
     * extended M3U playlist.
     */
    public List<ZipAsset> getSortedListOfAssets(File file, Map<String, Object> args) {
        String playlistEntry = args.get("internalPathPlaylist") == null ? null : args.get("internalPathPlaylist").toString();
        if (playlistEntry != null && !playlistEntry.isEmpty()) {
            return parseM3UPlaylist(file, playlistEntry);
        }
        return getSortedListOfAssets(file, AUDIO_EXTENSIONS);
    }

    private List<ZipAsset> parseM3UPlaylist(File file, String playlistEntry) {
        List<ZipAsset> assets = new ArrayList<>();
        try (ZipFile zip = openZip(file)) {
            ZipEntry ze = zip.getEntry(playlistEntry);
            if (ze == null) {
                return assets;
            }
            String[] lines = readEntryText(zip, ze).split("\n");
            if (lines.length == 0 || !lines[0].trim().startsWith(M3U_HEADER)) {
                return assets;
            }
            for (int i = 1; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.startsWith(M3U_PREAMBLE)) {
                    Map<String, String> meta = new LinkedHashMap<>();
                    Matcher m = M3U_LINE_PATTERN.matcher(line);
                    if (m.find()) {
                        meta.put("duration", m.group(1));
                        meta.put("title", m.group(2));
                    }
                    if (i + 1 < lines.length) {
                        String line2 = lines[i + 1].trim();
                        String entryPath = joinZipPath(parentDir(playlistEntry), line2);
                        assets.add(new ZipAsset(entryPath, meta));
                        i += 1;
                    }
                }
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