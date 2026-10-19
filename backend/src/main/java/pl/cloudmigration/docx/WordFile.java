package pl.cloudmigration.docx;

import java.io.*;
import java.util.*;
import java.util.zip.*;
import javax.xml.XMLConstants;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.docx4j.XmlUtils;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.w3c.dom.*;
import pl.cloudmigration.api.AppException;
import pl.cloudmigration.documents.DocumentCodec;

/** Word packaging, visible-answer controls and readable layout; never loads uploaded packages into docx4j. */
final class WordFile {
    static final String W="http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    static final String FORM="cm:application:v2";
    record Cell(String key,String value,String prompt) {}
    private final WordprocessingMLPackage pack;
    private final Document xml;
    final Element content;
    private final Map<String,String> placeholders=new LinkedHashMap<>();

    WordFile(String kind) throws Exception {
        try(var input=WordFile.class.getResourceAsStream("/docx/readable-style.docx")){
            pack=WordprocessingMLPackage.load(Objects.requireNonNull(input,"Missing document styles"));
        }
        xml=DocumentCodec.parse(XmlUtils.marshaltoString(pack.getMainDocumentPart().getJaxbElement()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var body=(Element)xml.getElementsByTagNameNS(W,"body").item(0);
        var section=child(body,"sectPr");if(section==null)section=element("sectPr");
        var size=child(section,"pgSz");if(size==null){size=element("pgSz");section.appendChild(size);}attr(size,"w","11906");attr(size,"h","16838");
        while(body.hasChildNodes())body.removeChild(body.getFirstChild());
        var control=control(kind,null,null);body.appendChild(control);content=child(control,"sdtContent");if(section!=null)body.appendChild(section);
    }
    static Cell text(String value){return new Cell(null,value,null);}
    static Cell answer(String key,String value,String prompt){return new Cell("cm:"+key,value,prompt);}
    Element element(String name){return xml.createElementNS(W,"w:"+name);}
    void attr(Element node,String name,String value){node.setAttributeNS(W,"w:"+name,value);}
    private Element property(Element parent,String name,String value){var e=element(name);attr(e,"val",value);parent.appendChild(e);return e;}
    private Element paragraph(String style,String text,boolean bold,boolean placeholder){
        var p=element("p");var pr=element("pPr");p.appendChild(pr);if(style!=null)property(pr,"pStyle",style);
        for(var line:Objects.toString(text,"").split("\n",-1)){
            if(p.getElementsByTagNameNS(W,"r").getLength()>0){var br=element("r");br.appendChild(element("br"));p.appendChild(br);}
            var run=element("r");var rpr=element("rPr");run.appendChild(rpr);if(bold)rpr.appendChild(element("b"));
            if(placeholder)property(rpr,"rStyle","PlaceholderText");
            var t=element("t");t.setAttributeNS(XMLConstants.XML_NS_URI,"xml:space","preserve");t.setTextContent(line);run.appendChild(t);p.appendChild(run);
        }return p;
    }
    void p(String text){content.appendChild(paragraph(null,text,false,false));}
    void heading(String style,String text){content.appendChild(paragraph(style,text,false,false));}
    void strong(String text){content.appendChild(paragraph(null,text,true,false));}
    void field(String key,String label,String value,String prompt){heading("Heading2",label);content.appendChild(control("cm:"+key,value,prompt));}
    private Element control(String key,String value,String prompt){
        var sdt=element("sdt");var pr=element("sdtPr");sdt.appendChild(pr);property(pr,"tag",key);
        var body=element("sdtContent");sdt.appendChild(body);
        if(prompt!=null){
            // Plain text preserves multiline answers without Word automatically adding email hyperlinks.
            var plain=element("text");attr(plain,"multiLine","1");pr.appendChild(plain);
            property(pr,"alias",prompt);
            String name=key.replaceAll("[^a-zA-Z0-9]","_");placeholders.put(name,prompt);
            var placeholder=element("placeholder");property(placeholder,"docPart",name);pr.appendChild(placeholder);
            boolean blank=value==null||value.isBlank();if(blank)pr.appendChild(element("showingPlcHdr"));
            body.appendChild(paragraph(null,blank?prompt:value,false,blank));
        }
        return sdt;
    }
    void table(String key,List<String> headers,List<List<Cell>> rows){
        Element parent=content;if(key!=null){var wrapper=control("cm:"+key,null,null);content.appendChild(wrapper);parent=child(wrapper,"sdtContent");}
        var table=element("tbl");parent.appendChild(table);var properties=element("tblPr");table.appendChild(properties);property(properties,"tblStyle","Table");
        var width=element("tblW");attr(width,"type","pct");attr(width,"w","5000");properties.appendChild(width);
        var borders=element("tblBorders");properties.appendChild(borders);
        for(var side:List.of("top","left","bottom","right","insideH","insideV")){var b=element(side);attr(b,"val","single");attr(b,"sz","4");attr(b,"color","D5DFD7");borders.appendChild(b);}
        var grid=element("tblGrid");table.appendChild(grid);
        int[] widths=headers.size()==6?new int[]{2700,1100,650,850,1850,1850}:new int[headers.size()];
        if(headers.size()!=6)Arrays.fill(widths,9000/headers.size());
        for(int value:widths){var col=element("gridCol");attr(col,"w",Integer.toString(value));grid.appendChild(col);}
        var header=element("tr");var hpr=element("trPr");hpr.appendChild(element("tblHeader"));header.appendChild(hpr);table.appendChild(header);
        for(int i=0;i<headers.size();i++){var cell=cell(widths[i]);var shade=element("shd");attr(shade,"fill","EAF1EC");child(cell,"tcPr").appendChild(shade);cell.appendChild(paragraph(null,headers.get(i),true,false));header.appendChild(cell);}
        for(var row:rows){var tr=element("tr");table.appendChild(tr);for(int i=0;i<row.size();i++){var value=row.get(i);var cell=cell(widths[i]);tr.appendChild(cell);cell.appendChild(value.key()==null?paragraph(null,value.value(),false,false):control(value.key(),value.value(),value.prompt()));}}
        p("");
    }
    private Element cell(int width){var cell=element("tc");var pr=element("tcPr");cell.appendChild(pr);var w=element("tcW");attr(w,"type","dxa");attr(w,"w",Integer.toString(width));pr.appendChild(w);return cell;}
    byte[] save() throws Exception {
        pack.getMainDocumentPart().setJaxbElement((org.docx4j.wml.Document)XmlUtils.unmarshalString(serialize(xml)));
        if(!placeholders.isEmpty()){
            var glossary=element("glossaryDocument");var parts=element("docParts");glossary.appendChild(parts);
            for(var entry:placeholders.entrySet()){
                var part=element("docPart");parts.appendChild(part);var properties=element("docPartPr");part.appendChild(properties);property(properties,"name",entry.getKey());
                var types=element("types");property(types,"type","bbPlcHdr");properties.appendChild(types);
                var body=element("docPartBody");body.appendChild(paragraph(null,entry.getValue(),false,true));part.appendChild(body);
            }
            var glossaryPart=new org.docx4j.openpackaging.parts.WordprocessingML.GlossaryDocumentPart();
            glossaryPart.setJaxbElement((org.docx4j.wml.GlossaryDocument)XmlUtils.unmarshalString(serialize(glossary)));
            pack.getMainDocumentPart().addTargetPart(glossaryPart);
        }
        var out=new ByteArrayOutputStream();pack.save(out);return out.toByteArray();
    }
    static String serialize(Node document) throws Exception {
        var factory=TransformerFactory.newDefaultInstance();factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET,"");
        var output=new StringWriter();factory.newTransformer().transform(new DOMSource(document),new StreamResult(output));return output.toString();
    }
    static Element child(Element parent,String name){for(var n=parent.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element e&&W.equals(e.getNamespaceURI())&&name.equals(e.getLocalName()))return e;return null;}
    static List<Element> controls(Element scope,String key){
        var found=new ArrayList<Element>();var all=scope.getElementsByTagNameNS(W,"sdt");
        for(int i=0;i<all.getLength();i++){var sdt=(Element)all.item(i);var pr=child(sdt,"sdtPr");var tag=pr==null?null:child(pr,"tag");if(tag!=null&&key.equals(tag.getAttributeNS(W,"val")))found.add(sdt);}
        return found;
    }
    static String visible(Element scope){
        var lines=new ArrayList<String>();var paragraphs=scope.getElementsByTagNameNS(W,"p");
        for(int i=0;i<paragraphs.getLength();i++){var line=new StringBuilder();visibleNodes(paragraphs.item(i),line);lines.add(line.toString());}
        return String.join("\n",lines).strip();
    }
    private static void visibleNodes(Node node,StringBuilder text){
        if(node instanceof Element e&&W.equals(e.getNamespaceURI())){
            if(Set.of("del","moveFrom").contains(e.getLocalName()))return;
            if(e.getLocalName().equals("t")){text.append(e.getTextContent());return;}
            if(e.getLocalName().equals("br")){text.append('\n');return;}
            if(e.getLocalName().equals("tab")){text.append(' ');return;}
        }
        for(var child=node.getFirstChild();child!=null;child=child.getNextSibling())visibleNodes(child,text);
    }
    static Document upload(byte[] bytes){
        if(bytes.length>5*1024*1024)throw new AppException(413,"UPLOAD_SIZE","Plik przekracza 5 MB.");
        try{
            byte[] main=null;boolean type=false;long expanded=0;var paths=new HashSet<String>();
            try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes))){ZipEntry entry;byte[] buffer=new byte[8192];
                while((entry=zip.getNextEntry())!=null){
                    String name=entry.getName(),lower=name.toLowerCase(Locale.ROOT);
                    if(!paths.add(lower)||paths.size()>1000||name.contains("..")||name.startsWith("/"))throw AppException.invalid("Nieprawidłowa struktura archiwum DOCX.");
                    if(lower.contains("vbaproject"))throw AppException.invalid("Makra nie są obsługiwane.");
                    boolean isXml=lower.endsWith(".xml")||lower.endsWith(".rels");var out=new ByteArrayOutputStream();int n;
                    while((n=zip.read(buffer))!=-1){expanded+=n;if(expanded>20L*1024*1024)throw AppException.invalid("Zbyt duża zawartość rozpakowanego DOCX.");if(isXml)out.write(buffer,0,n);}
                    if(isXml){var parsed=DocumentCodec.parse(out.toByteArray());
                        if(name.equals("[Content_Types].xml")){var all=parsed.getElementsByTagNameNS("*","Override");for(int i=0;i<all.getLength();i++){var p=(Element)all.item(i);if("/word/document.xml".equals(p.getAttribute("PartName"))&&"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml".equals(p.getAttribute("ContentType")))type=true;}}
                        var relations=parsed.getElementsByTagNameNS("*","Relationship");for(int i=0;i<relations.getLength();i++){var relation=(Element)relations.item(i);if("External".equalsIgnoreCase(relation.getAttribute("TargetMode"))||relation.getAttribute("Target").matches("(?i)^[a-z][a-z0-9+.-]*:.*"))throw AppException.invalid("Zewnętrzne odwołania w DOCX nie są obsługiwane.");}
                    }
                    if(name.equals("word/document.xml"))main=out.toByteArray();
                }
            }
            if(main==null||!type)throw AppException.invalid("Plik nie jest obsługiwanym dokumentem DOCX.");
            var document=DocumentCodec.parse(main);if(!W.equals(document.getDocumentElement().getNamespaceURI())||!"document".equals(document.getDocumentElement().getLocalName()))throw AppException.invalid("Nieobsługiwany format dokumentu Word.");return document;
        }catch(AppException e){throw e;}catch(Exception e){throw AppException.invalid("Nie można odczytać pliku DOCX. Sprawdź, czy plik nie jest uszkodzony.");}
    }
}
