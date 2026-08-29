package minstrel.server;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Test fixture factory: builds sample EPUB / CBZ / ABZ archives and helper
 * request maps used by the unit tests.
 */
public final class TestFixtures {

    private TestFixtures() {
    }

    /** A decodable 1x1 red PNG generated at runtime (validated by ImageIO). */
    public static byte[] pngBytes() {
        try {
            java.awt.image.BufferedImage img =
                    new java.awt.image.BufferedImage(4, 4, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics g = img.getGraphics();
            g.setColor(java.awt.Color.RED);
            g.fillRect(0, 0, 4, 4);
            g.dispose();
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------
    // archive builders
    // ------------------------------------------------------------------

    public static File createEpub(File dir) throws IOException {
        return createZip(dir, "sample.epub", entries(
                "META-INF/container.xml", containerXml(),
                "OEBPS/content.opf", opfXml(),
                "OEBPS/cover.png", pngBytes(),
                "OEBPS/Text/ch1.xhtml", "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>Ch1</title></head><body><p>Hello</p></body></html>"
        ));
    }

    public static File createCbz(File dir) throws IOException {
        return createZip(dir, "sample.cbz", entries(
                "cover.png", pngBytes(),
                "page01.png", pngBytes(),
                "page02.png", pngBytes(),
                "metadata.txt", "title: Sample Comic\nauthor: Tester\nlanguage: en\n"
        ));
    }

    public static File createAbz(File dir) throws IOException {
        return createZip(dir, "sample.abz", entries(
                "cover.png", pngBytes(),
                "playlist.m3u", "#EXTM3U\n#EXTINF:2,Track One\ntrack01.wav\n#EXTINF:3,Track Two\ntrack02.wav\n",
                "track01.wav", new byte[]{1, 2, 3, 4},
                "track02.wav", new byte[]{5, 6, 7, 8},
                "metadata.txt", "title: Sample Audiobook\nauthor: Test Author\nnarrator: Test Narrator\nlanguage: en\nduration: 0:00:05.000\n"
        ));
    }

    public static File createZipWithTraversal(File dir) throws IOException {
        return createZip(dir, "evil.zip", entries(
                "../escape.txt", "nope"
        ));
    }

    private static String containerXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">"
                + "<rootfiles><rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/>"
                + "</rootfiles></container>";
    }

    private static String opfXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" unique-identifier=\"id\">"
                + "<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">"
                + "<dc:title>Test Book</dc:title>"
                + "<dc:creator>Jane Doe</dc:creator>"
                + "<dc:language>en</dc:language>"
                + "<meta property=\"media:duration\">0:10:00</meta>"
                + "<meta property=\"media:narrator\">Narrator One</meta>"
                + "<meta name=\"calibre:series\" content=\"Series X\"/>"
                + "<meta name=\"calibre:series_index\" content=\"2\"/>"
                + "</metadata>"
                + "<manifest>"
                + "<item id=\"cover\" href=\"cover.png\" media-type=\"image/png\" properties=\"cover-image\"/>"
                + "<item id=\"ch1\" href=\"Text/ch1.xhtml\" media-type=\"application/xhtml+xml\"/>"
                + "</manifest>"
                + "<spine><itemref idref=\"ch1\"/></spine>"
                + "</package>";
    }

    public static Map<String, byte[]> entries(Object... pairs) {
        Map<String, byte[]> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            Object v = pairs[i + 1];
            byte[] bytes;
            if (v instanceof byte[]) {
                bytes = (byte[]) v;
            } else {
                bytes = String.valueOf(v).getBytes(StandardCharsets.UTF_8);
            }
            map.put(String.valueOf(pairs[i]), bytes);
        }
        return map;
    }

    public static File createZip(File dir, String name, Map<String, byte[]> entries) throws IOException {
        File f = new File(dir, name);
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(f.toPath()))) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue());
                zos.closeEntry();
            }
        }
        return f;
    }

    // ------------------------------------------------------------------
    // request map builders
    // ------------------------------------------------------------------

    public static Map<String, Object> commander(String command, String optionJson) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("command", command);
        body.put("option", optionJson == null ? "{}" : optionJson);
        return body;
    }

    public static Map<String, Object> librarian(String mode, String path, Map<String, Object> args) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mode", mode);
        body.put("path", path == null ? "" : path);
        body.put("args", Json.stringify(args));
        return body;
    }

    public static Map<String, Object> unzipper(String mode, String srcPath, String dstPath, Map<String, Object> args) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mode", mode);
        body.put("srcPath", srcPath == null ? "" : srcPath);
        body.put("dstPath", dstPath == null ? "" : dstPath);
        body.put("args", Json.stringify(args));
        return body;
    }

    public static byte[] hexToBytes(String hex) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < hex.length(); i += 2) {
            out.write(Integer.parseInt(hex.substring(i, i + 2), 16));
        }
        return out.toByteArray();
    }

    public static void copy(File src, File dst) throws IOException {
        Files.copy(src.toPath(), dst.toPath());
    }
}