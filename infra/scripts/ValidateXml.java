import java.nio.file.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.SchemaFactory;
import org.w3c.dom.*;

/** Validate an in-memory migration before the single atomic BaseX update; no document copies on disk. */
class ValidateXml {
    public static void main(String[] args) throws Exception {
        var parser=DocumentBuilderFactory.newInstance();parser.setNamespaceAware(true);
        parser.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        parser.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");parser.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
        var input=parser.newDocumentBuilder().parse(System.in);var entries=input.getDocumentElement().getChildNodes();int count=0;
        for(int i=0;i<entries.getLength();i++)if(entries.item(i) instanceof Element entry){
            Element document=null;for(Node node=entry.getFirstChild();node!=null;node=node.getNextSibling())if(node instanceof Element e){document=e;break;}
            if(document==null)throw new IllegalArgumentException("Empty migration entry");
            String type=document.getLocalName();if(!java.util.Set.of("competition","application","evaluation","decision").contains(type))throw new IllegalArgumentException("Unknown document type");
            var factory=SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD,"");factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"file");
            factory.newSchema(Path.of(args[0],type+".xsd").toFile()).newValidator().validate(new DOMSource(document));count++;
        }
        System.out.println("Validated "+count+" enriched XML documents against v2 schemas.");
    }
}
