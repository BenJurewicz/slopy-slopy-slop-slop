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
            var f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            var doc = f.newDocumentBuilder().newDocument();
            var root = doc.createElementNS(type.namespace(), type.name());
            doc.appendChild(root);
            append(doc, root, dto, type.namespace(), true);
            return writeGenerated(type,
                    context.createUnmarshaller().unmarshal(new DOMSource(doc)));
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

    private void append(Document doc, Element parent, Object value, String ns,
            boolean root) throws Exception {
        for (var field : value.getClass().getRecordComponents()) {
            var v = field.getAccessor().invoke(value);
            if (v == null)
                continue;
            if (root && (field.getName().equals("id")
                    || field.getName().equals("revision"))) {
                parent.setAttribute(field.getName(), v.toString());
                continue;
            }
            var values = v instanceof List<?> list ? list : List.of(v);
            for (var item : values) {
                if (item == null)
                    throw AppException.invalid("Lista zawiera pusty element.");
                var child = doc.createElementNS(ns, field.getName());
                parent.appendChild(child);
                if (item.getClass().isRecord())
                    append(doc, child, item, "urn:cloudmigration:common:v2",
                            false);
                else
                    child.setTextContent(item.toString());
            }
        }
    }

    private Object record(Element element, Class<?> clazz) throws Exception {
        var components = clazz.getRecordComponents();
        var values = new Object[components.length];
        var classes = new Class<?>[components.length];
        for (int i = 0; i < components.length; i++) {
            var c = components[i];
            classes[i] = c.getType();
            var children = new ArrayList<Element>();
            for (var node = element.getFirstChild(); node != null; node = node
                    .getNextSibling())
                if (node instanceof Element e
                        && c.getName().equals(e.getLocalName()))
                    children.add(e);
            if (c.getType() == List.class) {
                Class<?> item = (Class<?>) ((ParameterizedType) c
                        .getGenericType()).getActualTypeArguments()[0];
                var list = new ArrayList<Object>();
                for (var child : children)
                    list.add(record(child, item));
                values[i] = List.copyOf(list);
            } else if (c.getType().isRecord())
                values[i] = children.isEmpty()
                        ? null
                        : record(children.getFirst(), c.getType());
            else {
                var text = element.hasAttribute(c.getName())
                        ? element.getAttribute(c.getName())
                        : children.isEmpty()
                                ? null
                                : children.getFirst().getTextContent();
                values[i] = c.getType() == int.class
                        ? Integer.parseInt(text)
                        : c.getType() == boolean.class
                                ? Boolean.parseBoolean(text)
                                : text;
            }
        }
        return clazz.getDeclaredConstructor(classes).newInstance(values);
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
