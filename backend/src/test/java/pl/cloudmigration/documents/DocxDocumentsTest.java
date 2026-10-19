package pl.cloudmigration.documents;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.junit.jupiter.api.Test;
import org.w3c.dom.*;
import pl.cloudmigration.demo.Examples;
import pl.cloudmigration.docx.DocxDocuments;
import pl.cloudmigration.api.AppException;
import static org.junit.jupiter.api.Assertions.*;

class DocxDocumentsTest {
    static final String W="http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    final DocxDocuments documents=new DocxDocuments(new DocumentCodec());
    final DocxDocuments.Context context=new DocxDocuments.Context(Examples.competition(),Examples.application(),Examples.evaluation());
    final String competitionId=Examples.competition().id();
    byte[] application(){return documents.export(DocumentCodec.Type.application,Examples.application(),context);}
    Models.Application read(byte[] bytes){return documents.importApplication(bytes,competitionId);}
    Map<String,byte[]> entries(byte[] bytes) throws Exception {
        var entries=new LinkedHashMap<String,byte[]>();try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes))){ZipEntry e;while((e=zip.getNextEntry())!=null)entries.put(e.getName(),zip.readAllBytes());}return entries;
    }
    byte[] zip(Map<String,byte[]> entries) throws Exception {
        var out=new ByteArrayOutputStream();try(var zip=new ZipOutputStream(out)){for(var e:entries.entrySet()){zip.putNextEntry(new ZipEntry(e.getKey()));zip.write(e.getValue());zip.closeEntry();}}return out.toByteArray();
    }
    byte[] changed(byte[] bytes,java.util.function.Consumer<Document> edit) throws Exception {
        var parts=entries(bytes);var xml=DocumentCodec.parse(parts.get("word/document.xml"));edit.accept(xml);
        var out=new ByteArrayOutputStream();TransformerFactory.newDefaultInstance().newTransformer().transform(new DOMSource(xml),new StreamResult(out));parts.put("word/document.xml",out.toByteArray());return zip(parts);
    }
    Element field(Document xml,String key){var tags=xml.getElementsByTagNameNS(W,"tag");for(int i=0;i<tags.getLength();i++)if(key.equals(((Element)tags.item(i)).getAttributeNS(W,"val")))return (Element)tags.item(i).getParentNode().getParentNode();throw new AssertionError("Missing control: "+key);}
    void set(Document xml,String key,String value){var field=field(xml,key);var texts=field.getElementsByTagNameNS(W,"t");for(int i=texts.getLength()-1;i>0;i--)texts.item(i).getParentNode().removeChild(texts.item(i));texts.item(0).setTextContent(value);}
    String visible(byte[] bytes) throws Exception {var xml=DocumentCodec.parse(entries(bytes).get("word/document.xml"));var text=new StringBuilder();var texts=xml.getElementsByTagNameNS(W,"t");for(int i=0;i<texts.getLength();i++)text.append(texts.item(i).getTextContent()).append('\n');return text.toString();}
    void assertData(Models.Application expected,Models.Application actual){
        assertEquals(expected.company(),actual.company());assertEquals(expected.title(),actual.title());assertEquals(expected.description(),actual.description());assertEquals(expected.objectives(),actual.objectives());assertEquals(expected.benefits(),actual.benefits());
        assertEquals(expected.currentInfrastructure(),actual.currentInfrastructure());assertEquals(expected.targetInfrastructure(),actual.targetInfrastructure());assertEquals(expected.tasks(),actual.tasks());assertEquals(expected.budget(),actual.budget());assertEquals(expected.team(),actual.team());assertEquals(expected.requestedAmount(),actual.requestedAmount());
        assertNotEquals(expected.id(),actual.id());assertEquals("DRAFT",actual.state());assertEquals(1,actual.revision());assertNull(actual.submittedAt());
    }
    @Test void applicationRoundTripPreservesAllVisibleAnswers(){assertData(Examples.application(),read(application()));}
    @Test void importedApplicationUsesSelectedCompetitionNotFileContext(){var selected=UUID.randomUUID().toString();var actual=documents.importApplication(application(),selected);assertEquals(selected,actual.competitionId());assertNotEquals(Examples.application().competitionId(),actual.competitionId());}
    @Test void blankTemplateHasGuidanceNotSampleDataAndNamesMissingField() throws Exception {
        var bytes=documents.template(Examples.competition());var text=visible(bytes);
        assertTrue(text.contains("To pusty formularz"));assertTrue(text.contains("Wpisz pełną nazwę firmy"));assertFalse(text.contains(Examples.application().company().name()));assertFalse(text.contains(Examples.application().title()));assertFalse(text.contains("50000.00"));
        assertTrue(assertThrows(AppException.class,()->read(bytes)).getMessage().contains("Nazwa firmy"));
        var page=(Element)DocumentCodec.parse(entries(bytes).get("word/document.xml")).getElementsByTagNameNS(W,"pgSz").item(0);assertEquals("11906",page.getAttributeNS(W,"w"));assertEquals("16838",page.getAttributeNS(W,"h"));
    }
    @Test void fillingBlankTemplateImportsAnswersWithoutRemovingPlaceholderFlags() throws Exception {
        var example=DocumentCodec.parse(entries(application()).get("word/document.xml"));
        var filled=changed(documents.template(Examples.competition()),xml->{var tags=example.getElementsByTagNameNS(W,"tag");for(int i=0;i<tags.getLength();i++){String key=((Element)tags.item(i)).getAttributeNS(W,"val");var source=field(example,key);var text=source.getElementsByTagNameNS(W,"t");if(!Set.of("cm:application:v2","cm:tasks","cm:team","cm:budget","cm:currentInfrastructure","cm:targetInfrastructure").contains(key)){var value=new StringBuilder();for(int n=0;n<text.getLength();n++){if(n>0)value.append("\n");value.append(text.item(n).getTextContent());}set(xml,key,value.toString());}}});
        assertData(Examples.application(),read(filled));
    }
    @Test void exportsUsePolishLabelsWithoutVisibleTechnicalIdentifiers() throws Exception {
        Object[] values={Examples.competition(),Examples.application(),Examples.evaluation(),Examples.decision()};int i=0;
        for(var type:DocumentCodec.Type.values()){
            var text=visible(documents.export(type,values[i++],context));assertFalse(text.contains("[application]"));assertFalse(text.contains("company.name"));assertFalse(text.contains(" — title"));assertFalse(text.contains(Examples.application().id()));assertFalse(text.contains(competitionId));assertFalse(text.contains(Examples.evaluation().id()));
        }
        var evaluation=visible(documents.export(DocumentCodec.Type.evaluation,Examples.evaluation(),context));assertTrue(evaluation.contains(Examples.application().company().name()));assertTrue(evaluation.contains(Examples.application().title()));assertTrue(evaluation.contains(Examples.competition().criteria().getFirst().name()));assertTrue(evaluation.contains("nie oznacza automatycznego"));
        var decision=visible(documents.export(DocumentCodec.Type.decision,Examples.decision(),context));assertTrue(decision.contains(Examples.application().title()));assertTrue(decision.contains("Powiązane dokumenty"));assertTrue(decision.contains("To szkic decyzji"));
    }
    @Test void rejectionAndIssuedDecisionWordingReflectActualState() throws Exception {
        var rejection=Records.with(Examples.decision(),Map.of("state","ISSUED","outcome","REJECT","amount","0.00"));var text=visible(documents.export(DocumentCodec.Type.decision,rejection,context));assertTrue(text.contains("Odmawia się"));assertTrue(text.contains("Nie przyznano środków"));assertFalse(text.contains("obejmuje całą kwotę"));
        var award=Records.with(Examples.decision(),Map.of("state","ISSUED"));text=visible(documents.export(DocumentCodec.Type.decision,award,context));assertTrue(text.contains("Przyznaje się"));assertFalse(text.contains("To szkic decyzji"));
    }
    @Test void preservesCopiedRowsSplitRunsAndPolishNumbers() throws Exception {
        var original=Records.with(Examples.application(),Map.of("budget",List.of(Examples.application().budget().getFirst(),new Models.BudgetLine("Backup","Usługi","1.5","h","10.01","15.02"))));
        var bytes=documents.export(DocumentCodec.Type.application,original,context);
        bytes=changed(bytes,xml->{var t=field(xml,"cm:title").getElementsByTagNameNS(W,"t").item(0);String value=t.getTextContent();t.setTextContent(value.substring(0,3));var rest=t.cloneNode(true);rest.setTextContent(value.substring(3));t.getParentNode().appendChild(rest);});
        assertData(original,read(bytes));
        var copied=changed(application(),xml->{var task=field(xml,"cm:tasks.name");Node row=task;while(!"tr".equals(row.getLocalName()))row=row.getParentNode();row.getParentNode().appendChild(row.cloneNode(true));});assertEquals(2,read(copied).tasks().size());
    }
    @Test void readsVisibleEditsNotCachedMetadataAndAcceptsMultilineText() throws Exception {
        var edited=changed(application(),xml->{set(xml,"cm:title","Nowy tytuł z Worda");set(xml,"cm:description","Pierwszy akapit\nDrugi akapit — Łódź");set(xml,"cm:budget.total","");set(xml,"cm:tasks.start","01.11.2026");});
        var parts=entries(edited);parts.put("customXml/cached.xml","<application><title>Stary tytuł</title><requestedAmount>1.00</requestedAmount></application>".getBytes(StandardCharsets.UTF_8));var actual=read(zip(parts));
        assertEquals("Nowy tytuł z Worda",actual.title());assertEquals("Pierwszy akapit\nDrugi akapit — Łódź",actual.description());assertEquals("2026-11-01",actual.tasks().getFirst().start());assertEquals(Examples.application().budget(),actual.budget());assertEquals(Examples.application().requestedAmount(),actual.requestedAmount());
    }
    @Test void rejectsBudgetMismatchMissingDuplicateControlsAndInvalidDates() throws Exception {
        var bad=changed(application(),xml->set(xml,"cm:budget.total","1,00"));assertTrue(assertThrows(AppException.class,()->read(bad)).getMessage().contains("nie zgadza się"));
        var duplicate=changed(application(),xml->{var f=field(xml,"cm:company.name");f.getParentNode().appendChild(f.cloneNode(true));});assertTrue(assertThrows(AppException.class,()->read(duplicate)).getMessage().contains("powtórzone pole"));
        var missing=changed(application(),xml->{var f=field(xml,"cm:company.email");f.getParentNode().removeChild(f);});assertTrue(assertThrows(AppException.class,()->read(missing)).getMessage().contains("Adres e-mail"));
        var date=changed(application(),xml->set(xml,"cm:tasks.start","31.02.2026"));assertTrue(assertThrows(AppException.class,()->read(date)).getMessage().contains("Nieprawidłowa data"));
    }
    @Test void noLegacyMarkerParserRemains() throws Exception {
        var old=changed(application(),xml->{field(xml,"cm:application:v2").getElementsByTagNameNS(W,"tag").item(0).getAttributes().getNamedItemNS(W,"val").setNodeValue("[application]");});
        assertTrue(assertThrows(AppException.class,()->read(old)).getMessage().contains("Stare formularze techniczne"));
    }
    @Test void rejectsInvalidFilesAndMissingCompetition(){
        assertThrows(AppException.class,()->read("not a docx".getBytes()));assertThrows(AppException.class,()->read(new byte[5*1024*1024+1]));assertThrows(AppException.class,()->documents.importApplication(application(),""));
    }
    @Test void rejectsExpansionExternalRelationshipsDtdMacrosAndUnsupportedContentType() throws Exception {
        assertThrows(AppException.class,()->read(zip(Map.of("large.bin",new byte[20*1024*1024+1]))));
        assertThrows(AppException.class,()->read(zip(Map.of("word/_rels/document.xml.rels","<Relationships><Relationship TargetMode='External' Target='https://example.org/secret'/></Relationships>".getBytes()))));
        assertThrows(AppException.class,()->read(zip(Map.of("word/document.xml","<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///etc/passwd'>]><x>&e;</x>".getBytes()))));
        assertThrows(AppException.class,()->read(zip(Map.of("word/vbaProject.bin",new byte[]{1}))));
        var parts=entries(application());parts.put("[Content_Types].xml",new String(parts.get("[Content_Types].xml"),StandardCharsets.UTF_8).replace("application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml","application/vnd.ms-word.document.macroEnabled.main+xml").getBytes(StandardCharsets.UTF_8));assertThrows(AppException.class,()->read(zip(parts)));
    }
    @Test void rejectsAmbiguousZipPathsAndExcessivelyNestedXml() throws Exception {
        var parts=entries(application());parts.put("word/DOCUMENT.xml",parts.get("word/document.xml"));assertTrue(assertThrows(AppException.class,()->read(zip(parts))).getMessage().contains("struktura archiwum"));
        var deep=("<x>".repeat(150)+"</x>".repeat(150)).getBytes(StandardCharsets.UTF_8);assertThrows(AppException.class,()->read(zip(Map.of("word/document.xml",deep))));
    }
    @Test void importsBlankFormCompletedInMicrosoftWord() throws Exception {
        assertData(Examples.application(),read(Files.readAllBytes(Path.of("../examples/docx/word-filled-template.docx"))));
    }
    @Test void importsReadableFormEditedAndSavedByMicrosoftWord() throws Exception {
        var actual=read(Files.readAllBytes(Path.of("../examples/docx/word-edited-application.docx")));assertEquals("Migracja Żółwia — edytowana w Word",actual.title());
        var expected=Records.with(Examples.application(),Map.of("title",actual.title()));assertData(expected,actual);
    }
}
