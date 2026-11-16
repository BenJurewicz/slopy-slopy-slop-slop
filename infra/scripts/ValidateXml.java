import java.nio.file.Path;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.SchemaFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Validate an in-memory migration before the single atomic BaseX update; no
 * document copies on disk.
 */
class ValidateXml {
    public static void main(String[] args) throws Exception {
        var input = parseInput();
        var entries = input.getDocumentElement().getChildNodes();
        int count = 0;
        for (int index = 0; index < entries.getLength(); index++) {
            if (!(entries.item(index) instanceof Element entry)) {
                continue;
            }
            Element document = firstDocument(entry);
            String type = documentType(document);
            var factory = schemaFactory();
            factory.newSchema(Path.of(args[0], type + ".xsd").toFile())
                    .newValidator().validate(new DOMSource(document));
            count++;
        }
        System.out.println("Validated " + count
                + " enriched XML documents against v2 schemas.");
    }

    private static Document parseInput() throws Exception {
        var parser = DocumentBuilderFactory.newInstance();
        parser.setNamespaceAware(true);
        parser.setFeature(
                "http://apache.org/xml/features/disallow-doctype-decl", true);
        parser.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        parser.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return parser.newDocumentBuilder().parse(System.in);
    }

    private static Element firstDocument(Element entry) {
        for (Node child = entry.getFirstChild(); child != null; child = child
                .getNextSibling()) {
            if (child instanceof Element document) {
                return document;
            }
        }
        throw new IllegalArgumentException("Empty migration entry");
    }

    private static String documentType(Element document) {
        String type = document.getLocalName();
        if (!Set.of("competition", "application", "evaluation", "decision")
                .contains(type)) {
            throw new IllegalArgumentException("Unknown document type");
        }
        return type;
    }

    private static SchemaFactory schemaFactory() throws Exception {
        var factory = SchemaFactory
                .newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file");
        return factory;
    }
}
