package pl.cloudmigration.docx;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import pl.cloudmigration.api.AppException;
import pl.cloudmigration.documents.DocumentCodec;

final class WordUpload {
    private static final int MAX_UPLOAD_BYTES = 5 * 1024 * 1024;
    private static final long MAX_EXPANDED_BYTES = 20L * 1024 * 1024;
    private static final int MAX_ENTRIES = 1000;
    private static final String WORD_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml";

    private final Set<String> paths = new HashSet<>();
    private final byte[] buffer = new byte[8192];
    private long expandedBytes;
    private byte[] mainDocument;
    private boolean wordContentType;

    static Document read(byte[] bytes) {
        if (bytes.length > MAX_UPLOAD_BYTES)
            throw new AppException(413, "UPLOAD_SIZE", "Plik przekracza 5 MB.");
        try {
            return new WordUpload().readArchive(bytes);
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            throw AppException.invalid(
                    "Nie można odczytać pliku DOCX. Sprawdź, czy plik nie jest uszkodzony.");
        }
    }

    private Document readArchive(byte[] bytes) throws Exception {
        try (var archive = new ZipInputStream(
                new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = archive.getNextEntry()) != null)
                readEntry(archive, entry);
        }
        if (mainDocument == null || !wordContentType)
            throw AppException
                    .invalid("Plik nie jest obsługiwanym dokumentem DOCX.");

        var document = DocumentCodec.parse(mainDocument);
        var root = document.getDocumentElement();
        if (!WordTemplates.W.equals(root.getNamespaceURI())
                || !"document".equals(root.getLocalName()))
            throw AppException.invalid("Nieobsługiwany format dokumentu Word.");
        return document;
    }

    private void readEntry(ZipInputStream archive, ZipEntry entry)
            throws Exception {
        String path = entry.getName();
        String normalized = path.toLowerCase(Locale.ROOT);
        validatePath(path, normalized);

        boolean xml = normalized.endsWith(".xml")
                || normalized.endsWith(".rels");
        var contents = readContents(archive, xml);
        if (xml) {
            var document = DocumentCodec.parse(contents);
            if (path.equals("[Content_Types].xml")
                    && hasWordContentType(document))
                wordContentType = true;
            rejectExternalRelationships(document);
        }
        if (path.equals("word/document.xml"))
            mainDocument = contents;
    }

    private void validatePath(String path, String normalized) {
        if (!paths.add(normalized) || paths.size() > MAX_ENTRIES
                || path.contains("..") || path.startsWith("/"))
            throw AppException
                    .invalid("Nieprawidłowa struktura archiwum DOCX.");
        if (normalized.contains("vbaproject"))
            throw AppException.invalid("Makra nie są obsługiwane.");
    }

    private byte[] readContents(ZipInputStream archive, boolean keepXml)
            throws Exception {
        var output = new ByteArrayOutputStream();
        int count;
        while ((count = archive.read(buffer)) != -1) {
            expandedBytes += count;
            if (expandedBytes > MAX_EXPANDED_BYTES)
                throw AppException
                        .invalid("Zbyt duża zawartość rozpakowanego DOCX.");
            if (keepXml)
                output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private boolean hasWordContentType(Document document) {
        var overrides = document.getElementsByTagNameNS("*", "Override");
        for (int index = 0; index < overrides.getLength(); index++) {
            var override = (Element) overrides.item(index);
            if ("/word/document.xml".equals(override.getAttribute("PartName"))
                    && WORD_CONTENT_TYPE
                            .equals(override.getAttribute("ContentType")))
                return true;
        }
        return false;
    }

    private void rejectExternalRelationships(Document document) {
        var relationships = document.getElementsByTagNameNS("*",
                "Relationship");
        for (int index = 0; index < relationships.getLength(); index++) {
            var relationship = (Element) relationships.item(index);
            if ("External"
                    .equalsIgnoreCase(relationship.getAttribute("TargetMode"))
                    || relationship.getAttribute("Target")
                            .matches("(?i)^[a-z][a-z0-9+.-]*:.*"))
                throw AppException.invalid(
                        "Zewnętrzne odwołania w DOCX nie są obsługiwane.");
        }
    }
}
