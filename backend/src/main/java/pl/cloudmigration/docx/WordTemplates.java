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
import pl.cloudmigration.documents.DocumentCodec.Type;

/** Fill the supplied Word documents in place; parse uploaded answers without loading untrusted packages into docx4j. */
final class WordTemplates {
    static final String W="http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private final WordprocessingMLPackage pack;
    final Document xml;
    final Element root;
    WordTemplates(Type type) throws Exception {
        if(type==Type.competition)throw AppException.invalid("Eksport DOCX jest dostępny dla wniosków, ocen i decyzji.");
        try(var in=WordTemplates.class.getResourceAsStream("/docx/"+type+".docx")){pack=WordprocessingMLPackage.load(Objects.requireNonNull(in));}
        xml=DocumentCodec.parse(XmlUtils.marshaltoString(pack.getMainDocumentPart().getJaxbElement()).getBytes(java.nio.charset.StandardCharsets.UTF_8));root=xml.getDocumentElement();
        var textControls=root.getElementsByTagNameNS(W,"text");for(int i=0;i<textControls.getLength();i++)((Element)textControls.item(i)).setAttributeNS(W,"w:multiLine","1");
    }
    void fill(Map<String,String> values){values.forEach((key,value)->{if(value!=null)for(var control:controls(root,key))write(control,value);});}
    void repeat(String tag,List<Map<String,String>> items){
        var groups=controls(root,tag);if(groups.size()!=1)throw new IllegalStateException("Expected one template group: "+tag);
        var original=groups.getFirst();var parent=original.getParentNode();
        for(var values:items){var clone=(Element)original.cloneNode(true);parent.insertBefore(clone,original);values.forEach((key,value)->{for(var field:controls(clone,key))write(field,value);});}
        parent.removeChild(original);
    }
    private Element el(String name){return xml.createElementNS(W,"w:"+name);}
    private void write(Element control,String value){
        var body=child(control,"sdtContent");var oldCell=child(body,"tc");var cellProperties=oldCell==null?null:child(oldCell,"tcPr");var paragraphs=body.getElementsByTagNameNS(W,"p");boolean block=paragraphs.getLength()>0;
        var runs=body.getElementsByTagNameNS(W,"r");Element run=runs.getLength()==0?null:(Element)runs.item(0);var rpr=run==null?null:child(run,"rPr");
        Element p=block?(Element)paragraphs.item(0).cloneNode(false):null;
        if(block){var properties=child((Element)paragraphs.item(0),"pPr");if(properties!=null)p.appendChild(properties.cloneNode(true));}
        while(body.hasChildNodes())body.removeChild(body.getFirstChild());var container=block?p:body;
        if(block){if(control.getParentNode() instanceof Element parent&&"tr".equals(parent.getLocalName())){var cell=el("tc");if(cellProperties!=null)cell.appendChild(cellProperties.cloneNode(true));body.appendChild(cell);cell.appendChild(p);}else body.appendChild(p);}
        var lines=value.split("\n",-1);
        for(int i=0;i<lines.length;i++){var r=el("r");if(rpr!=null)r.appendChild(rpr.cloneNode(true));if(i>0)r.appendChild(el("br"));var t=el("t");t.setAttributeNS(XMLConstants.XML_NS_URI,"xml:space","preserve");t.setTextContent(lines[i]);r.appendChild(t);container.appendChild(r);}
        var properties=child(control,"sdtPr");var placeholder=child(properties,"showingPlcHdr");if(placeholder!=null)properties.removeChild(placeholder);
    }
    void replace(String from,String to){replaceIn(root,from,to);}
    private void replaceIn(Element scope,String from,String to){
        var paragraphs=new ArrayList<Element>();if("p".equals(scope.getLocalName()))paragraphs.add(scope);else{var all=scope.getElementsByTagNameNS(W,"p");for(int i=0;i<all.getLength();i++)paragraphs.add((Element)all.item(i));}
        for(var paragraph:paragraphs){
            var texts=paragraph.getElementsByTagNameNS(W,"t");var joined=new StringBuilder();for(int j=0;j<texts.getLength();j++)joined.append(texts.item(j).getTextContent());
            int start=joined.indexOf(from);if(start<0)continue;int end=start+from.length(),offset=0;boolean inserted=false;
            for(int j=0;j<texts.getLength();j++){var t=texts.item(j);String old=t.getTextContent();int next=offset+old.length();
                if(next>start&&offset<end){String prefix=old.substring(0,Math.max(0,start-offset));String suffix=old.substring(Math.min(old.length(),Math.max(0,end-offset)));t.setTextContent(prefix+(inserted?"":to)+suffix);inserted=true;}
                offset=next;
            }
        }
    }
    static String scoreTag(String code){return switch(code){case "technical"->"technical_feasibility_score";case "security"->"security_score";case "benefits"->"business_benefits_score";case "team"->"schedule_and_team_score";case "budget"->"budget_justification_score";default->"criterion_"+code+"_score";};}
    private Element ancestor(Element element,String name){Node n=element;while(n!=null){if(n instanceof Element e&&W.equals(e.getNamespaceURI())&&name.equals(e.getLocalName()))return e;n=n.getParentNode();}throw new IllegalStateException("Missing "+name);}
    private void tag(Element control,String tag){child(child(control,"sdtPr"),"tag").setAttributeNS(W,"w:val",tag);}
    private void plain(Element element,String value){var texts=element.getElementsByTagNameNS(W,"t");for(int i=0;i<texts.getLength();i++)texts.item(i).setTextContent(i==0?value:"");}
    void scores(List<pl.cloudmigration.documents.Models.Criterion> criteria,List<pl.cloudmigration.documents.Models.Score> scores){
        var points=new HashMap<String,pl.cloudmigration.documents.Models.Score>();scores.forEach(v->points.put(v.code(),v));
        var controls=controls(root,"technical_feasibility_score");var row=ancestor(controls.getFirst(),"tr");var table=row.getParentNode();var prototype=(Element)row.cloneNode(true);
        var summary=ancestor(controls(root,"total_score").getFirst(),"tr");var header=table.getFirstChild();while(header!=null&&!(header instanceof Element e&&"tr".equals(e.getLocalName())))header=header.getNextSibling();
        for(var n=table.getFirstChild();n!=null;){var next=n.getNextSibling();if(n instanceof Element e&&"tr".equals(e.getLocalName())&&n!=header&&n!=summary)table.removeChild(n);n=next;}
        for(var criterion:criteria){var clone=(Element)prototype.cloneNode(true);var cells=clone.getElementsByTagNameNS(W,"tc");plain((Element)cells.item(0),criterion.name());plain((Element)cells.item(2),Integer.toString(criterion.maximum()));var field=controls(clone,"technical_feasibility_score").getFirst();tag(field,scoreTag(criterion.code()));write(field,Integer.toString(points.get(criterion.code()).points()));table.insertBefore(clone,summary);}
        plain((Element)summary.getElementsByTagNameNS(W,"tc").item(2),Integer.toString(criteria.stream().mapToInt(v->v.maximum()).sum()));
        var heading=ancestor(controls.get(1),"p");var description=controls(root,"technical_feasibility_score_description").getFirst();var headingTemplate=(Element)heading.cloneNode(true);var descriptionTemplate=(Element)description.cloneNode(true);var parent=heading.getParentNode();
        Node end=heading;while(end!=null&&!(end instanceof Element e&&text(e).startsWith("4. Podsumowanie")))end=end.getNextSibling();if(end==null)throw new IllegalStateException("Missing evaluation summary");
        for(Node n=heading;n!=end;){Node next=n.getNextSibling();parent.removeChild(n);n=next;}
        for(var criterion:criteria){var h=(Element)headingTemplate.cloneNode(true);var d=(Element)descriptionTemplate.cloneNode(true);var score=points.get(criterion.code());replaceIn(h,"Wykonalność techniczna:",criterion.name()+":");replaceIn(h,"z 25 punktów","z "+criterion.maximum()+" punktów");var field=controls(h,"technical_feasibility_score").getFirst();tag(field,scoreTag(criterion.code()));write(field,Integer.toString(score.points()));tag(d,scoreTag(criterion.code())+"_description");write(d,score.comment());parent.insertBefore(h,end);parent.insertBefore(d,end);}
    }
    void omitFrom(String heading){var body=(Element)root.getElementsByTagNameNS(W,"body").item(0);boolean remove=false;for(var n=body.getFirstChild();n!=null;){var next=n.getNextSibling();if(n instanceof Element e){if(visible(e).contains(heading)||("p".equals(e.getLocalName())&&text(e).contains(heading)))remove=true;if(remove&&!"sectPr".equals(e.getLocalName()))body.removeChild(e);}n=next;}}
    void note(String text){var body=(Element)root.getElementsByTagNameNS(W,"body").item(0);var p=el("p");var r=el("r");var t=el("t");t.setTextContent(text);r.appendChild(t);p.appendChild(r);body.insertBefore(p,child(body,"sectPr"));}
    void notice(String value){var body=child(root,"body");var p=el("p");var r=el("r");var pr=el("rPr");pr.appendChild(el("b"));r.appendChild(pr);var t=el("t");t.setTextContent(value);r.appendChild(t);p.appendChild(r);body.insertBefore(p,body.getFirstChild());}
    byte[] save() throws Exception {
        // Word requires unique control identities even after a repeated block is cloned.
        var ids=root.getElementsByTagNameNS(W,"id");for(int i=0;i<ids.getLength();i++)((Element)ids.item(i)).setAttributeNS(W,"w:val",Integer.toString(i+1));
        var paragraphs=root.getElementsByTagNameNS(W,"p");var w14="http://schemas.microsoft.com/office/word/2010/wordml";
        for(int i=0;i<paragraphs.getLength();i++){var paragraph=(Element)paragraphs.item(i);if(paragraph.hasAttributeNS(w14,"paraId"))paragraph.setAttributeNS(w14,"w14:paraId",String.format(Locale.ROOT,"%08X",i+1));}
        pack.getMainDocumentPart().setJaxbElement((org.docx4j.wml.Document)XmlUtils.unmarshalString(serialize(xml)));
        var out=new ByteArrayOutputStream();pack.save(out);return out.toByteArray();
    }
    static String serialize(Node node) throws Exception {var f=TransformerFactory.newDefaultInstance();f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");f.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET,"");var out=new StringWriter();f.newTransformer().transform(new DOMSource(node),new StreamResult(out));return out.toString();}
    static String text(Element element){var text=new StringBuilder();visibleNodes(element,text);return text.toString().strip();}
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
            if(e.getLocalName().equals("p")&&text.length()>0&&text.charAt(text.length()-1)!='\n')text.append('\n');
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
