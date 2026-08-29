package minstrel.server.librarian;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * EPUB metadata parsing (port of Android {@code FormatHandlerEPUB}).
 * Parses META-INF/container.xml and the OPF package file.
 */
public class FormatHandlerEPUB extends AbstractFormatHandler {

    private static final String NS_CONTAINER = "urn:oasis:names:tc:opendocument:xmlns:container";
    private static final String NS_OPF = "http://www.idpf.org/2007/opf";
    private static final String NS_DC = "http://purl.org/dc/elements/1.1/";
    private static final String CONTAINER_PATH = "META-INF/container.xml";

    public FormatHandlerEPUB() {
    }

    public Publication parseFile(File file) {
        Publication p = basePublication(file);
        Format format = new Format("epub");
        String opfPath = getOPFPath(file);

        if (opfPath != null) {
            try (ZipFile zip = openZip(file)) {
                ZipEntry opf = zip.getEntry(opfPath);
                if (opf != null) {
                    String opfText = readEntryText(zip, opf);
                    String[] parts = parseOPF(opfText);
                    format.addMetadatum("title", parts[0]);
                    format.addMetadatum("author", parts[1]);
                    format.addMetadatum("language", parts[2]);
                    if (parts[3] != null) {
                        format.addMetadatum("duration", parts[3]);
                    }
                    if (parts[4] != null) {
                        format.addMetadatum("narrator", parts[4]);
                    }
                    if (parts[5] != null) {
                        format.addMetadatum("series", parts[5]);
                    }
                    if (parts[6] != null) {
                        format.addMetadatum("seriesIndex", parts[6]);
                    }
                    String cover = parts[7];
                    if (cover != null) {
                        format.addMetadatum("internalPathCover", joinZipPath(parentDir(opfPath), cover));
                    }
                    p.isValid = true;
                }
            } catch (Exception e) {
                p.isValid = false;
            }
        }

        p.formats.add(format);
        extractCover(file, format, p.id);
        return p;
    }

    private String getOPFPath(File file) {
        try (ZipFile zip = openZip(file)) {
            ZipEntry container = zip.getEntry(CONTAINER_PATH);
            if (container == null) {
                return null;
            }
            Document doc = parseXml(readEntryText(zip, container));
            NodeList rootfiles = doc.getElementsByTagNameNS(NS_CONTAINER, "rootfile");
            for (int i = 0; i < rootfiles.getLength(); i++) {
                Element rf = (Element) rootfiles.item(i);
                String mediaType = rf.getAttribute("media-type");
                if ("application/oebps-package+xml".equals(mediaType)) {
                    return rf.getAttribute("full-path");
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * @return [title, author, language, duration, narrator, series, seriesIndex, coverHref]
     */
    private String[] parseOPF(String opfText) throws Exception {
        Document doc = parseXml(opfText);
        String title = "";
        String author = "";
        String language = "";
        String duration = null;
        String narrator = null;
        String series = null;
        String seriesIndex = null;
        String coverHref = null;
        String coverManifestId = null;

        NodeList metaNodes = doc.getElementsByTagNameNS(NS_OPF, "metadata");
        if (metaNodes.getLength() > 0) {
            Element metadata = (Element) metaNodes.item(0);
            NodeList children = metadata.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                Element el = (Element) child;
                String local = el.getLocalName();
                if (title.isEmpty() && "title".equals(local) && NS_DC.equals(el.getNamespaceURI())) {
                    title = el.getTextContent().trim();
                } else if (author.isEmpty() && "creator".equals(local) && NS_DC.equals(el.getNamespaceURI())) {
                    author = el.getTextContent().trim();
                } else if (language.isEmpty() && "language".equals(local) && NS_DC.equals(el.getNamespaceURI())) {
                    language = el.getTextContent().trim();
                } else if ("meta".equals(local)) {
                    String property = el.getAttribute("property");
                    String refines = el.getAttribute("refines");
                    String name = el.getAttribute("name");
                    String content = el.getAttribute("content");
                    // DOM returns "" for absent attributes, like a null in the
                    // original Android XmlPullParser implementation
                    boolean hasProperty = property != null && !property.isEmpty();
                    boolean hasRefines = refines != null && !refines.isEmpty();
                    boolean hasNameContent = name != null && !name.isEmpty()
                            && content != null && !content.isEmpty();
                    if (!hasRefines && hasProperty) {
                        if ("media:duration".equals(property)) {
                            duration = el.getTextContent().trim();
                        } else if ("media:narrator".equals(property)) {
                            narrator = el.getTextContent().trim();
                        }
                    } else if (hasNameContent) {
                        if ("cover".equals(name)) {
                            coverManifestId = content;
                        } else if ("calibre:series".equals(name)) {
                            series = content;
                        } else if ("calibre:series_index".equals(name)) {
                            seriesIndex = content;
                        }
                    }
                }
            }
        }

        NodeList manifest = doc.getElementsByTagNameNS(NS_OPF, "manifest");
        if (manifest.getLength() > 0) {
            Element m = (Element) manifest.item(0);
            NodeList items = m.getElementsByTagNameNS(NS_OPF, "item");
            for (int i = 0; i < items.getLength(); i++) {
                Element item = (Element) items.item(i);
                String id = item.getAttribute("id");
                String href = item.getAttribute("href");
                String properties = item.getAttribute("properties");
                if ((properties != null && properties.contains("cover-image"))
                        || (coverManifestId != null && coverManifestId.equals(id))) {
                    coverHref = href;
                    break;
                }
            }
        }

        return new String[]{title, author, language, duration, narrator, series, seriesIndex, coverHref};
    }

    private static Document parseXml(String text) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setExpandEntityReferences(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
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