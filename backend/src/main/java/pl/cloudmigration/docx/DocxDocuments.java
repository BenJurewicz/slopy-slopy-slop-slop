package pl.cloudmigration.docx;

import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.wml.*;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import pl.cloudmigration.api.AppException;
import pl.cloudmigration.documents.*;
import pl.cloudmigration.documents.DocumentCodec.Type;
import pl.cloudmigration.demo.Examples;

@Component
public class DocxDocuments {
    private final DocumentCodec codec;
    private static final long MAX_EXPANDED=20L*1024*1024;
    public DocxDocuments(DocumentCodec codec){this.codec=codec;}
    public byte[] template(){return export(Type.application,Examples.application());}
    public byte[] export(Type type,Object value){
        try {
            var pack=WordprocessingMLPackage.createPackage();var main=pack.getMainDocumentPart();
            main.addStyledParagraphOfText("Title","CloudMigration — "+switch(type){case application->"Wniosek grantowy";case evaluation->"Ocena wniosku";case decision->"Decyzja o finansowaniu";case competition->"Konkurs";});
            main.addParagraphOfText("Chmura dla Lokalnych Biznesów. Kwoty netto w PLN. Zachowaj oznaczenia pól w nawiasach kwadratowych.");
            main.addStyledParagraphOfText("Heading1","Dane dokumentu");
            var scalar=new ArrayList<List<String>>();scalar.add(List.of("["+type+"]","Wartość"));
            for(var field:value.getClass().getRecordComponents()) {
                var v=field.getAccessor().invoke(value);
                if(field.getType()!=List.class&&!field.getType().isRecord()) scalar.add(List.of(fieldLabel(field.getName()),v==null?"":v.toString()));
            }
            main.getContent().add(table(scalar));
            for(var field:value.getClass().getRecordComponents()){
                var v=field.getAccessor().invoke(value); if(v==null)continue;
                if(field.getType().isRecord()){
                    main.addStyledParagraphOfText("Heading1",label(field.getName()));
                    var rows=new ArrayList<List<String>>();rows.add(List.of("["+field.getName()+"]","Wartość"));
                    for(var f:v.getClass().getRecordComponents())rows.add(List.of(fieldLabel(f.getName()),Objects.toString(f.getAccessor().invoke(v),"")));
                    main.getContent().add(table(rows));
                }else if(v instanceof List<?> list&&!list.isEmpty()){
                    main.addStyledParagraphOfText("Heading1",label(field.getName()));
                    var rows=new ArrayList<List<String>>();rows.add(List.of("["+field.getName()+"]"));
                    var fields=list.getFirst().getClass().getRecordComponents();rows.add(Arrays.stream(fields).map(f->fieldLabel(f.getName())).toList());
                    for(var item:list){var row=new ArrayList<String>();for(var f:fields)row.add(Objects.toString(f.getAccessor().invoke(item),""));rows.add(row);}
                    main.getContent().add(table(rows));
                }
            }
            main.addStyledParagraphOfText("Heading1","Instrukcja");
            addInstructions(pack,List.of("Edytuj wartości, nie nazwy pól i oznaczenia sekcji.","Dodawaj pozycje przez kopiowanie wierszy tabel.","Ilości i kwoty zapisuj z kropką dziesiętną; daty: RRRR-MM-DD.","Wielkość firmy: MICRO lub SMALL. Waluta: PLN, kwoty netto."));
            var out=new ByteArrayOutputStream();pack.save(out);return out.toByteArray();
        }catch(Exception e){throw new IllegalStateException("DOCX export failed",e);}
    }
    public Models.Application importApplication(byte[] bytes){
        if(bytes.length>5*1024*1024)throw new AppException(413,"UPLOAD_SIZE","Plik przekracza 5 MB.");
        try {
            byte[] xml=null;boolean documentType=false;long expanded=0;int entries=0;var paths=new HashSet<String>();
            try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes))){
                ZipEntry entry;byte[] buffer=new byte[8192];
                while((entry=zip.getNextEntry())!=null){
                    if(!paths.add(entry.getName()) || ++entries>1000 || entry.getName().contains("..") || entry.getName().startsWith("/"))throw AppException.invalid("Nieprawidłowa struktura archiwum DOCX.");
                    if(entry.getName().toLowerCase(Locale.ROOT).contains("vbaproject"))throw AppException.invalid("Makra nie są obsługiwane.");
                    boolean isXml=entry.getName().toLowerCase(Locale.ROOT).endsWith(".xml")||entry.getName().toLowerCase(Locale.ROOT).endsWith(".rels");
                    var out=new ByteArrayOutputStream();int n;
                    while((n=zip.read(buffer))!=-1){expanded+=n;if(expanded>MAX_EXPANDED)throw AppException.invalid("Zbyt duża zawartość rozpakowanego DOCX.");if(isXml)out.write(buffer,0,n);}
                    if(isXml){
                        var safe=DocumentCodec.parse(out.toByteArray());
                        if(entry.getName().equals("[Content_Types].xml")){
                            var types=safe.getElementsByTagNameNS("*","Override");
                            for(int t=0;t<types.getLength();t++){
                                var part=(Element)types.item(t);
                                if("/word/document.xml".equals(part.getAttribute("PartName"))&&"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml".equals(part.getAttribute("ContentType")))documentType=true;
                            }
                        }
                        var relationships=safe.getElementsByTagNameNS("*","Relationship");
                        for(int r=0;r<relationships.getLength();r++){
                            var relation=(Element)relationships.item(r);
                            if("External".equalsIgnoreCase(relation.getAttribute("TargetMode"))||relation.getAttribute("Target").matches("(?i)^[a-z][a-z0-9+.-]*:.*"))throw AppException.invalid("Zewnętrzne odwołania w DOCX nie są obsługiwane.");
                        }
                    }
                    if(entry.getName().equals("word/document.xml"))xml=out.toByteArray();
                }
            }
            if(xml==null||!documentType)throw AppException.invalid("Plik nie jest obsługiwanym dokumentem DOCX.");
            // Import only visible WordML with the hardened parser. Unused package parts
            // never go through a second library parser or trigger external retrieval.
            var doc=DocumentCodec.parse(xml);
            if(!"document".equals(doc.getDocumentElement().getLocalName())||!"http://schemas.openxmlformats.org/wordprocessingml/2006/main".equals(doc.getDocumentElement().getNamespaceURI()))throw AppException.invalid("Nieobsługiwany format dokumentu Word.");
            var tables=doc.getElementsByTagNameNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main","tbl");
            var sections=new LinkedHashMap<String,List<List<String>>>();
            for(int i=0;i<tables.getLength();i++){
                var table=(Element)tables.item(i);var rows=new ArrayList<List<String>>();
                for(var tr=table.getFirstChild();tr!=null;tr=tr.getNextSibling())if(tr instanceof Element row&&row.getLocalName().equals("tr")){
                    var cells=new ArrayList<String>();
                    for(var cell=row.getFirstChild();cell!=null;cell=cell.getNextSibling())if(cell instanceof Element tc&&tc.getLocalName().equals("tc"))cells.add(cellText(tc));
                    if(!cells.isEmpty())rows.add(cells);
                }
                if(rows.isEmpty())continue;String marker=rows.getFirst().getFirst().strip();
                if(marker.startsWith("[")&&marker.endsWith("]")){String key=marker.substring(1,marker.length()-1);if(sections.putIfAbsent(key,rows)!=null)throw AppException.invalid("Powtórzona sekcja: "+key);}
            }
            if(!sections.containsKey("application"))throw AppException.invalid("Użyj szablonu wniosku CloudMigration.");
            var value=(Models.Application)readRecord(Models.Application.class,"application",sections);
            value=Records.with(value,new HashMap<>(){{put("id",UUID.randomUUID().toString());put("revision",1);put("state","DRAFT");put("submittedAt",null);}});
            codec.encode(Type.application,value);return value;
        }catch(AppException e){throw e;}catch(Exception e){throw AppException.invalid("Nie można odczytać szablonu DOCX. Sprawdź tabele i format danych.");}
    }
    private Object readRecord(Class<?> type,String section,Map<String,List<List<String>>> sections) throws Exception{
        var rows=sections.get(section);if(rows==null)throw AppException.invalid("Brak sekcji: "+section);
        var map=new HashMap<String,String>();for(int i=1;i<rows.size();i++)if(rows.get(i).size()>=2)map.put(rows.get(i).get(0).split(" — ",2)[0],rows.get(i).get(1));
        var fields=type.getRecordComponents();var args=new Object[fields.length];var types=new Class<?>[fields.length];
        for(int i=0;i<fields.length;i++){
            var f=fields[i];types[i]=f.getType();
            if(f.getType().isRecord())args[i]=readRecord(f.getType(),f.getName(),sections);
            else if(f.getType()==List.class){
                var table=sections.get(f.getName());if(table==null||table.size()<3)throw AppException.invalid("Brak pozycji sekcji: "+f.getName());
                var item=(Class<?>)((ParameterizedType)f.getGenericType()).getActualTypeArguments()[0];var list=new ArrayList<Object>();
                for(int row=2;row<table.size();row++){
                    if(table.get(row).stream().allMatch(String::isBlank))continue;
                    var scalar=new ArrayList<List<String>>();scalar.add(List.of("[item]","Wartość"));
                    for(int col=0;col<table.get(1).size();col++)scalar.add(List.of(table.get(1).get(col),col<table.get(row).size()?table.get(row).get(col):""));
                    list.add(readRecord(item,"item",Map.of("item",scalar)));
                }
                args[i]=List.copyOf(list);
            }else{String text=map.get(f.getName());args[i]=f.getType()==int.class?Integer.parseInt(text==null||text.isBlank()?"0":text):f.getType()==boolean.class?Boolean.parseBoolean(text):text==null||text.isEmpty()?null:text;}
        }
        return type.getDeclaredConstructor(types).newInstance(args);
    }
    private String cellText(Element cell){
        var result=new StringBuilder();var paragraphs=cell.getElementsByTagNameNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main","p");
        for(int i=0;i<paragraphs.getLength();i++){if(i>0)result.append('\n');var nodes=((Element)paragraphs.item(i)).getElementsByTagNameNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main","t");for(int j=0;j<nodes.getLength();j++)result.append(nodes.item(j).getTextContent());}
        return result.toString();
    }
    private void addInstructions(WordprocessingMLPackage pack,List<String> lines) throws Exception {
        var factory=new org.docx4j.wml.ObjectFactory();
        var numbering=new org.docx4j.openpackaging.parts.WordprocessingML.NumberingDefinitionsPart();
        var definition=factory.createNumbering();var abstractNum=factory.createNumberingAbstractNum();abstractNum.setAbstractNumId(java.math.BigInteger.ZERO);
        var level=factory.createLvl();level.setIlvl(java.math.BigInteger.ZERO);var format=factory.createNumFmt();format.setVal(NumberFormat.BULLET);level.setNumFmt(format);
        var text=factory.createLvlLvlText();text.setVal("•");level.setLvlText(text);abstractNum.getLvl().add(level);definition.getAbstractNum().add(abstractNum);
        var num=factory.createNumberingNum();num.setNumId(java.math.BigInteger.ONE);var abstractId=factory.createNumberingNumAbstractNumId();abstractId.setVal(java.math.BigInteger.ZERO);num.setAbstractNumId(abstractId);definition.getNum().add(num);
        numbering.setJaxbElement(definition);pack.getMainDocumentPart().addTargetPart(numbering);
        for(var line:lines){var p=pack.getMainDocumentPart().addParagraphOfText(line);var properties=factory.createPPr();var numPr=factory.createPPrBaseNumPr();
            var id=factory.createPPrBaseNumPrNumId();id.setVal(java.math.BigInteger.ONE);numPr.setNumId(id);var ilvl=factory.createPPrBaseNumPrIlvl();ilvl.setVal(java.math.BigInteger.ZERO);numPr.setIlvl(ilvl);properties.setNumPr(numPr);p.setPPr(properties);}
    }
    private Tbl table(List<List<String>> rows){
        var factory=new org.docx4j.wml.ObjectFactory();var table=factory.createTbl();
        for(var row:rows){var tr=factory.createTr();for(var value:row){var tc=factory.createTc();for(var line:value.split("\\n",-1)){var p=factory.createP();var r=factory.createR();var text=factory.createText();text.setValue(line);text.setSpace("preserve");r.getContent().add(text);p.getContent().add(r);tc.getContent().add(p);}tr.getContent().add(tc);}table.getContent().add(tr);}
        return table;
    }
    private String fieldLabel(String key){return key+" — "+switch(key){
        case "id"->"Identyfikator";case "revision"->"Wersja";case "state"->"Status";case "competitionId"->"Identyfikator konkursu";case "applicationId"->"Identyfikator wniosku";case "evaluationId"->"Identyfikator oceny";case "submittedAt"->"Data złożenia";
        case "name"->"Nazwa / osoba";case "description"->"Opis";case "title"->"Tytuł projektu";case "objectives"->"Cele";case "benefits"->"Korzyści";case "legalId"->"NIP / identyfikator";case "size"->"Wielkość firmy";case "contact"->"Osoba kontaktowa";case "email"->"E-mail";
        case "provider"->"Dostawca";case "start"->"Data rozpoczęcia";case "end"->"Data zakończenia";case "milestone"->"Kamień milowy";case "category"->"Kategoria";case "quantity"->"Ilość";case "unit"->"Jednostka";case "unitCost"->"Koszt jednostkowy";case "total"->"Suma";case "role"->"Rola";
        case "requestedAmount"->"Wnioskowana dotacja (PLN)";case "amount"->"Kwota (PLN)";case "outcome"->"Wynik AWARD / REJECT";case "reason"->"Uzasadnienie";case "reviewer"->"Oceniający";case "date"->"Data";case "eligible"->"Kwalifikowalny true / false";case "points"->"Punkty";case "comment"->"Komentarz";case "code"->"Kod";case "maximum"->"Maksimum punktów";case "eligibilityRules"->"Warunki kwalifikacji";case "currency"->"Waluta";case "budget"->"Budżet";case "maxGrant"->"Limit dotacji";case "threshold"->"Próg punktów";case "stage"->"Etap";default->key;
    };}
    private String label(String value){return switch(value){case "company"->"Wnioskodawca";case "currentInfrastructure"->"Obecna infrastruktura";case "targetInfrastructure"->"Docelowa chmura";case "tasks"->"Harmonogram";case "budget"->"Budżet";case "team"->"Zespół";case "scores"->"Punktacja";case "criteria"->"Kryteria";default->value;};}
}
