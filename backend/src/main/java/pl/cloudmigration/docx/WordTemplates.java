package pl.cloudmigration.docx;

import java.io.*;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.docx4j.XmlUtils;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.w3c.dom.*;
import pl.cloudmigration.api.AppException;
import pl.cloudmigration.documents.DocumentCodec;
import pl.cloudmigration.documents.DocumentCodec.Type;
import pl.cloudmigration.documents.Models.Criterion;
import pl.cloudmigration.documents.Models.Score;

final class WordTemplates {
    static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private final WordprocessingMLPackage pack;
    final Document xml;
    final Element root;
    WordTemplates(Type type) throws Exception {
        if (type == Type.competition)
            throw AppException.invalid(
                    "Eksport DOCX jest dostępny dla wniosków, ocen i decyzji.");
        try (var in = WordTemplates.class
                .getResourceAsStream("/docx/" + type + ".docx")) {
            pack = WordprocessingMLPackage.load(Objects.requireNonNull(in));
        }
        xml = DocumentCodec.parse(XmlUtils
                .marshaltoString(pack.getMainDocumentPart().getJaxbElement())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        root = xml.getDocumentElement();
        var textControls = root.getElementsByTagNameNS(W, "text");
        for (int i = 0; i < textControls.getLength(); i++)
            ((Element) textControls.item(i)).setAttributeNS(W, "w:multiLine",
                    "1");
    }

    void fill(Map<String, String> values) {
        values.forEach((key, value) -> {
            if (value != null)
                for (var control : controls(root, key))
                    write(control, value);
        });
    }

    void repeat(String tag, List<Map<String, String>> items) {
        var groups = controls(root, tag);
        if (groups.size() != 1)
            throw new IllegalStateException(
                    "Expected one template group: " + tag);
        var original = groups.getFirst();
        var parent = original.getParentNode();
        for (var values : items) {
            var clone = (Element) original.cloneNode(true);
            parent.insertBefore(clone, original);
            values.forEach((key, value) -> {
                for (var field : controls(clone, key))
                    write(field, value);
            });
        }
        parent.removeChild(original);
    }

    private Element wordElement(String name) {
        return xml.createElementNS(W, "w:" + name);
    }

    private void write(Element control, String value) {
        var body = child(control, "sdtContent");
        var oldCell = child(body, "tc");
        var cellProperties = oldCell == null ? null : child(oldCell, "tcPr");
        var paragraphs = body.getElementsByTagNameNS(W, "p");
        boolean block = paragraphs.getLength() > 0;
        var runs = body.getElementsByTagNameNS(W, "r");
        Element prototypeRun = runs.getLength() == 0
                ? null
                : (Element) runs.item(0);
        var runProperties = prototypeRun == null
                ? null
                : child(prototypeRun, "rPr");
        Element paragraph = block
                ? (Element) paragraphs.item(0).cloneNode(false)
                : null;
        if (block) {
            var properties = child((Element) paragraphs.item(0), "pPr");
            if (properties != null)
                paragraph.appendChild(properties.cloneNode(true));
        }
        while (body.hasChildNodes())
            body.removeChild(body.getFirstChild());
        var container = block ? paragraph : body;
        if (block)
            attachParagraph(control, body, paragraph, cellProperties);
        appendTextLines(container, runProperties, value);
        var properties = child(control, "sdtPr");
        var placeholder = child(properties, "showingPlcHdr");
        if (placeholder != null)
            properties.removeChild(placeholder);
    }

    private void attachParagraph(Element control, Element body,
            Element paragraph, Element cellProperties) {
        if (!(control.getParentNode() instanceof Element parent)
                || !"tr".equals(parent.getLocalName())) {
            body.appendChild(paragraph);
            return;
        }
        var cell = wordElement("tc");
        if (cellProperties != null)
            cell.appendChild(cellProperties.cloneNode(true));
        body.appendChild(cell);
        cell.appendChild(paragraph);
    }

    private void appendTextLines(Element container, Element runProperties,
            String value) {
        var lines = value.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            var run = wordElement("r");
            if (runProperties != null)
                run.appendChild(runProperties.cloneNode(true));
            if (i > 0)
                run.appendChild(wordElement("br"));
            var textNode = wordElement("t");
            textNode.setAttributeNS(XMLConstants.XML_NS_URI, "xml:space",
                    "preserve");
            textNode.setTextContent(lines[i]);
            run.appendChild(textNode);
            container.appendChild(run);
        }
    }

    void replace(String from, String to) {
        replaceIn(root, from, to);
    }

    private void replaceIn(Element scope, String from, String to) {
        var paragraphs = new ArrayList<Element>();
        if ("p".equals(scope.getLocalName()))
            paragraphs.add(scope);
        else {
            var all = scope.getElementsByTagNameNS(W, "p");
            for (int i = 0; i < all.getLength(); i++)
                paragraphs.add((Element) all.item(i));
        }
        for (var paragraph : paragraphs) {
            var texts = paragraph.getElementsByTagNameNS(W, "t");
            var joined = new StringBuilder();
            for (int j = 0; j < texts.getLength(); j++)
                joined.append(texts.item(j).getTextContent());
            int start = joined.indexOf(from);
            if (start < 0)
                continue;
            int end = start + from.length(), offset = 0;
            boolean inserted = false;
            for (int j = 0; j < texts.getLength(); j++) {
                var textNode = texts.item(j);
                String old = textNode.getTextContent();
                int next = offset + old.length();
                if (next > start && offset < end) {
                    String prefix = old.substring(0,
                            Math.max(0, start - offset));
                    String suffix = old.substring(
                            Math.min(old.length(), Math.max(0, end - offset)));
                    textNode.setTextContent(
                            prefix + (inserted ? "" : to) + suffix);
                    inserted = true;
                }
                offset = next;
            }
        }
    }

    static String scoreTag(String code) {
        return switch (code) {
            case "technical" -> "technical_feasibility_score";
            case "security" -> "security_score";
            case "benefits" -> "business_benefits_score";
            case "team" -> "schedule_and_team_score";
            case "budget" -> "budget_justification_score";
            default -> "criterion_" + code + "_score";
        };
    }

    private Element ancestor(Element element, String name) {
        Node node = element;
        while (node != null) {
            if (node instanceof Element matchedElement
                    && W.equals(matchedElement.getNamespaceURI())
                    && name.equals(matchedElement.getLocalName()))
                return matchedElement;
            node = node.getParentNode();
        }
        throw new IllegalStateException("Missing " + name);
    }

    private void tag(Element control, String tag) {
        child(child(control, "sdtPr"), "tag").setAttributeNS(W, "w:val", tag);
    }

    private void plain(Element element, String value) {
        var texts = element.getElementsByTagNameNS(W, "t");
        for (int i = 0; i < texts.getLength(); i++)
            texts.item(i).setTextContent(i == 0 ? value : "");
    }

    void scores(List<Criterion> criteria, List<Score> scores) {
        var points = new HashMap<String, Score>();
        scores.forEach(value -> points.put(value.code(), value));
        var controls = controls(root, "technical_feasibility_score");
        fillScoreTable(criteria, points, controls);
        fillScoreDescriptions(criteria, points, controls);
    }

    private void fillScoreTable(List<Criterion> criteria,
            Map<String, Score> points, List<Element> controls) {
        var row = ancestor(controls.getFirst(), "tr");
        var table = row.getParentNode();
        var prototype = (Element) row.cloneNode(true);
        var summary = ancestor(controls(root, "total_score").getFirst(), "tr");
        var header = table.getFirstChild();
        while (header != null && !(header instanceof Element matchedElement
                && "tr".equals(matchedElement.getLocalName())))
            header = header.getNextSibling();
        for (var node = table.getFirstChild(); node != null;) {
            var next = node.getNextSibling();
            if (node instanceof Element matchedElement
                    && "tr".equals(matchedElement.getLocalName())
                    && node != header && node != summary)
                table.removeChild(node);
            node = next;
        }
        for (var criterion : criteria) {
            var clone = (Element) prototype.cloneNode(true);
            var cells = clone.getElementsByTagNameNS(W, "tc");
            plain((Element) cells.item(0), criterion.name());
            plain((Element) cells.item(2),
                    Integer.toString(criterion.maximum()));
            var field = controls(clone, "technical_feasibility_score")
                    .getFirst();
            tag(field, scoreTag(criterion.code()));
            write(field,
                    Integer.toString(points.get(criterion.code()).points()));
            table.insertBefore(clone, summary);
        }
        plain((Element) summary.getElementsByTagNameNS(W, "tc").item(2),
                Integer.toString(criteria.stream()
                        .mapToInt(value -> value.maximum()).sum()));
    }

    private void fillScoreDescriptions(List<Criterion> criteria,
            Map<String, Score> points, List<Element> controls) {
        var heading = ancestor(controls.get(1), "p");
        var description = controls(root,
                "technical_feasibility_score_description").getFirst();
        var headingTemplate = (Element) heading.cloneNode(true);
        var descriptionTemplate = (Element) description.cloneNode(true);
        var parent = heading.getParentNode();
        Node end = heading;
        while (end != null && !(end instanceof Element matchedElement
                && text(matchedElement).startsWith("4. Podsumowanie")))
            end = end.getNextSibling();
        if (end == null)
            throw new IllegalStateException("Missing evaluation summary");
        for (Node node = heading; node != end;) {
            Node next = node.getNextSibling();
            parent.removeChild(node);
            node = next;
        }
        for (var criterion : criteria) {
            var headingClone = (Element) headingTemplate.cloneNode(true);
            var descriptionClone = (Element) descriptionTemplate
                    .cloneNode(true);
            var score = points.get(criterion.code());
            replaceIn(headingClone, "Wykonalność techniczna:",
                    criterion.name() + ":");
            replaceIn(headingClone, "z 25 punktów",
                    "z " + criterion.maximum() + " punktów");
            var field = controls(headingClone, "technical_feasibility_score")
                    .getFirst();
            tag(field, scoreTag(criterion.code()));
            write(field, Integer.toString(score.points()));
            tag(descriptionClone, scoreTag(criterion.code()) + "_description");
            write(descriptionClone, score.comment());
            parent.insertBefore(headingClone, end);
            parent.insertBefore(descriptionClone, end);
        }
    }

    void omitFrom(String heading) {
        var body = (Element) root.getElementsByTagNameNS(W, "body").item(0);
        boolean remove = false;
        for (var node = body.getFirstChild(); node != null;) {
            var next = node.getNextSibling();
            if (node instanceof Element matchedElement) {
                if (visible(matchedElement).contains(heading)
                        || ("p".equals(matchedElement.getLocalName())
                                && text(matchedElement).contains(heading)))
                    remove = true;
                if (remove && !"sectPr".equals(matchedElement.getLocalName()))
                    body.removeChild(matchedElement);
            }
            node = next;
        }
    }

    void note(String text) {
        var body = (Element) root.getElementsByTagNameNS(W, "body").item(0);
        var paragraph = wordElement("p");
        var run = wordElement("r");
        var textNode = wordElement("t");
        textNode.setTextContent(text);
        run.appendChild(textNode);
        paragraph.appendChild(run);
        body.insertBefore(paragraph, child(body, "sectPr"));
    }

    void notice(String value) {
        var body = child(root, "body");
        var paragraph = wordElement("p");
        var run = wordElement("r");
        var properties = wordElement("rPr");
        properties.appendChild(wordElement("b"));
        run.appendChild(properties);
        var textNode = wordElement("t");
        textNode.setTextContent(value);
        run.appendChild(textNode);
        paragraph.appendChild(run);
        body.insertBefore(paragraph, body.getFirstChild());
    }

    byte[] save() throws Exception {
        // Word requires unique control identities even after a repeated block
        // is cloned.
        var ids = root.getElementsByTagNameNS(W, "id");
        for (int i = 0; i < ids.getLength(); i++)
            ((Element) ids.item(i)).setAttributeNS(W, "w:val",
                    Integer.toString(i + 1));
        var paragraphs = root.getElementsByTagNameNS(W, "p");
        var w14 = "http://schemas.microsoft.com/office/word/2010/wordml";
        for (int i = 0; i < paragraphs.getLength(); i++) {
            var paragraph = (Element) paragraphs.item(i);
            if (paragraph.hasAttributeNS(w14, "paraId"))
                paragraph.setAttributeNS(w14, "w14:paraId",
                        String.format(Locale.ROOT, "%08X", i + 1));
        }
        pack.getMainDocumentPart()
                .setJaxbElement((org.docx4j.wml.Document) XmlUtils
                        .unmarshalString(serialize(xml)));
        var output = new ByteArrayOutputStream();
        pack.save(output);
        return output.toByteArray();
    }

    static String serialize(Node node) throws Exception {
        var factory = TransformerFactory.newDefaultInstance();
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        var output = new StringWriter();
        factory.newTransformer().transform(new DOMSource(node),
                new StreamResult(output));
        return output.toString();
    }

    static String text(Element element) {
        var text = new StringBuilder();
        visibleNodes(element, text);
        return text.toString().strip();
    }

    static Element child(Element parent, String name) {
        for (var node = parent.getFirstChild(); node != null; node = node
                .getNextSibling())
            if (node instanceof Element matchedElement
                    && W.equals(matchedElement.getNamespaceURI())
                    && name.equals(matchedElement.getLocalName()))
                return matchedElement;
        return null;
    }

    static List<Element> controls(Element scope, String key) {
        var found = new ArrayList<Element>();
        var all = scope.getElementsByTagNameNS(W, "sdt");
        for (int i = 0; i < all.getLength(); i++) {
            var sdt = (Element) all.item(i);
            var properties = child(sdt, "sdtPr");
            var tag = properties == null ? null : child(properties, "tag");
            if (tag != null && key.equals(tag.getAttributeNS(W, "val")))
                found.add(sdt);
        }
        return found;
    }

    static String visible(Element scope) {
        var lines = new ArrayList<String>();
        var paragraphs = scope.getElementsByTagNameNS(W, "p");
        for (int i = 0; i < paragraphs.getLength(); i++) {
            var line = new StringBuilder();
            visibleNodes(paragraphs.item(i), line);
            lines.add(line.toString());
        }
        return String.join("\n", lines).strip();
    }

    private static void visibleNodes(Node node, StringBuilder text) {
        if (node instanceof Element matchedElement
                && W.equals(matchedElement.getNamespaceURI())) {
            if (Set.of("del", "moveFrom")
                    .contains(matchedElement.getLocalName()))
                return;
            if (matchedElement.getLocalName().equals("p") && text.length() > 0
                    && text.charAt(text.length() - 1) != '\n')
                text.append('\n');
            if (matchedElement.getLocalName().equals("t")) {
                text.append(matchedElement.getTextContent());
                return;
            }
            if (matchedElement.getLocalName().equals("br")) {
                text.append('\n');
                return;
            }
            if (matchedElement.getLocalName().equals("tab")) {
                text.append(' ');
                return;
            }
        }
        for (var child = node.getFirstChild(); child != null; child = child
                .getNextSibling())
            visibleNodes(child, text);
    }

}
