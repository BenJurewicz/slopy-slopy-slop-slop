package pl.cloudmigration.documents;

import jakarta.xml.bind.JAXBContext;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.*;
import pl.cloudmigration.api.AppException;

@Component
public class DocumentCodec {
    public enum Type {
        competition(Models.Competition.class), application(
                Models.Application.class), evaluation(
                        Models.Evaluation.class), decision(
                                Models.Decision.class);
        public final Class<?> dto;
        Type(Class<?> dto) {
            this.dto = dto;
        }

        public String namespace() {
            return "urn:cloudmigration:" + name() + ":v2";
        }

        public String path(String id) {
            return name() + "s/" + id + ".xml";
        }
    }
    private final JAXBContext context;
    private final Map<Type, Schema> schemas = new EnumMap<>(Type.class);
    public DocumentCodec() {
        try {
            context = JAXBContext.newInstance("pl.cloudmigration.xml.model");
            for (var type : Type.values()) {
                var factory = SchemaFactory
                        .newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
                factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
                factory.setResourceResolver(
                        (kind, ns, publicId, systemId, base) -> {
                            if (!"common.xsd".equals(systemId))
                                return null;
                            return new SchemaInput(
                                    getClass().getResourceAsStream(
                                            "/schemas/common.xsd"));
                        });
                schemas.put(type, factory.newSchema(
                        getClass().getResource("/schemas/" + type + ".xsd")));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load document schemas", e);
        }
    }

    public static Document parse(byte[] xml) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(
                "http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature(
                "http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature(
                "http://xml.org/sax/features/external-parameter-entities",
                false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute("jdk.xml.maxElementDepth", 128);
        return factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml));
    }

    private void validate(Type type, Document document) throws Exception {
        var validator = schemas.get(type).newValidator();
        validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        validator.validate(new DOMSource(document));
    }

    public Object readGenerated(Type type, byte[] xml) {
        try {
            var document = parse(xml);
            if (!type.name()
                    .equals(document.getDocumentElement().getLocalName())
                    || !type.namespace().equals(
                            document.getDocumentElement().getNamespaceURI()))
                throw AppException.invalid("Nieprawidłowy typ dokumentu XML.");
            validate(type, document);
            var reader = context.createUnmarshaller();
            reader.setSchema(schemas.get(type));
            return reader.unmarshal(new DOMSource(document));
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw AppException.invalid("xml",
                    "XML nie spełnia schematu: " + rootMessage(e));
        }
    }

    public String writeGenerated(Type type, Object model) {
        try {
            var writer = context.createMarshaller();
            writer.setSchema(schemas.get(type));
            writer.setProperty(
                    jakarta.xml.bind.Marshaller.JAXB_FORMATTED_OUTPUT, true);
            var output = new StringWriter();
            writer.marshal(model, output);
            validate(type, parse(output.toString()
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            return output.toString();
        } catch (Exception e) {
            throw AppException
                    .invalid("Nie można zapisać XML: " + rootMessage(e));
        }
    }

    public String encode(Type type, Object dto) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            var document = factory.newDocumentBuilder().newDocument();
            var root = document.createElementNS(type.namespace(), type.name());
            document.appendChild(root);
            append(document, root, dto, type.namespace(), true);
            return writeGenerated(type, context.createUnmarshaller()
                    .unmarshal(new DOMSource(document)));
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw AppException
                    .invalid("Nieprawidłowe dane dokumentu: " + rootMessage(e));
        }
    }

    public <T> T decode(Type type, String xml, Class<T> target) {
        try {
            var valid = writeGenerated(type, readGenerated(type,
                    xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            return target.cast(record(parse(
                    valid.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .getDocumentElement(), target));
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw AppException
                    .invalid("Nie można odczytać dokumentu: " + rootMessage(e));
        }
    }

    private void append(Document document, Element parent, Object value,
            String namespace, boolean isRoot) throws Exception {
        for (var component : value.getClass().getRecordComponents()) {
            var fieldValue = component.getAccessor().invoke(value);
            if (fieldValue == null)
                continue;

            if (isRoot && (component.getName().equals("id")
                    || component.getName().equals("revision"))) {
                parent.setAttribute(component.getName(), fieldValue.toString());
                continue;
            }

            var items = fieldValue instanceof List<?> list
                    ? list
                    : List.of(fieldValue);
            for (var item : items)
                appendElement(document, parent, component.getName(), item,
                        namespace);
        }
    }

    private void appendElement(Document document, Element parent, String name,
            Object value, String namespace) throws Exception {
        if (value == null)
            throw AppException.invalid("Lista zawiera pusty element.");

        var child = document.createElementNS(namespace, name);
        parent.appendChild(child);
        if (value.getClass().isRecord()) {
            append(document, child, value, "urn:cloudmigration:common:v2",
                    false);
            return;
        }
        child.setTextContent(value.toString());
    }

    private Object record(Element element, Class<?> target) throws Exception {
        var components = target.getRecordComponents();
        var values = new Object[components.length];
        var parameterTypes = new Class<?>[components.length];
        for (int index = 0; index < components.length; index++) {
            var component = components[index];
            parameterTypes[index] = component.getType();
            values[index] = componentValue(element, component);
        }
        return target.getDeclaredConstructor(parameterTypes)
                .newInstance(values);
    }

    private Object componentValue(Element element, RecordComponent component)
            throws Exception {
        var children = matchingChildren(element, component.getName());
        var type = component.getType();
        if (type == List.class) {
            var itemType = (Class<?>) ((ParameterizedType) component
                    .getGenericType()).getActualTypeArguments()[0];
            var items = new ArrayList<Object>();
            for (var child : children)
                items.add(record(child, itemType));
            return List.copyOf(items);
        }
        if (type.isRecord())
            return children.isEmpty()
                    ? null
                    : record(children.getFirst(), type);

        var text = componentText(element, component.getName(), children);
        if (type == int.class)
            return Integer.parseInt(text);
        if (type == boolean.class)
            return Boolean.parseBoolean(text);
        return text;
    }

    private List<Element> matchingChildren(Element parent, String name) {
        var children = new ArrayList<Element>();
        for (var node = parent.getFirstChild(); node != null; node = node
                .getNextSibling()) {
            if (node instanceof Element child
                    && name.equals(child.getLocalName()))
                children.add(child);
        }
        return children;
    }

    private String componentText(Element element, String name,
            List<Element> children) {
        if (element.hasAttribute(name))
            return element.getAttribute(name);
        if (children.isEmpty())
            return null;
        return children.getFirst().getTextContent();
    }

    private static String rootMessage(Throwable e) {
        while (e.getCause() != null)
            e = e.getCause();
        return Objects.toString(e.getMessage(), "Nieprawidłowy dokument");
    }
    private static class SchemaInput implements org.w3c.dom.ls.LSInput {
        private InputStream stream;
        SchemaInput(InputStream s) {
            stream = s;
        }

        public Reader getCharacterStream() {
            return null;
        }

        public void setCharacterStream(Reader r) {
        }

        public InputStream getByteStream() {
            return stream;
        }

        public void setByteStream(InputStream s) {
            stream = s;
        }

        public String getStringData() {
            return null;
        }

        public void setStringData(String s) {
        }

        public String getSystemId() {
            return "common.xsd";
        }

        public void setSystemId(String s) {
        }

        public String getPublicId() {
            return null;
        }

        public void setPublicId(String s) {
        }

        public String getBaseURI() {
            return null;
        }

        public void setBaseURI(String s) {
        }

        public String getEncoding() {
            return "UTF-8";
        }

        public void setEncoding(String s) {
        }

        public boolean getCertifiedText() {
            return false;
        }

        public void setCertifiedText(boolean b) {
        }
    }
}
