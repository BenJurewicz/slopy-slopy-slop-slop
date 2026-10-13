package pl.cloudmigration.documents;

import org.junit.jupiter.api.Test;
import pl.cloudmigration.demo.Examples;
import pl.cloudmigration.docx.DocxDocuments;
import pl.cloudmigration.api.AppException;
import static org.junit.jupiter.api.Assertions.*;

class DocxDocumentsTest {
    final DocxDocuments documents=new DocxDocuments(new DocumentCodec());
    @Test void applicationRoundTripPreservesVisibleData(){
        var expected=Examples.application();var actual=documents.importApplication(documents.export(DocumentCodec.Type.application,expected));
        assertEquals(expected.company(),actual.company());assertEquals(expected.description(),actual.description());
        assertEquals(expected.budget(),actual.budget());assertEquals(expected.tasks(),actual.tasks());assertEquals(expected.team(),actual.team());
        assertNotEquals(expected.id(),actual.id());assertEquals("DRAFT",actual.state());
    }
    @Test void allDocumentTypesExport(){
        assertTrue(documents.export(DocumentCodec.Type.competition,Examples.competition()).length>1000);
        assertTrue(documents.export(DocumentCodec.Type.evaluation,Examples.evaluation()).length>1000);
        assertTrue(documents.export(DocumentCodec.Type.decision,Examples.decision()).length>1000);
    }
    @Test void rejectsInvalidFiles(){
        assertThrows(AppException.class,()->documents.importApplication("not a docx".getBytes()));
        assertThrows(AppException.class,()->documents.importApplication(new byte[5*1024*1024+1]));
    }
    byte[] zip(java.util.Map<String,byte[]> entries) throws Exception {
        var out=new java.io.ByteArrayOutputStream();try(var zip=new java.util.zip.ZipOutputStream(out)){
            for(var entry:entries.entrySet()){zip.putNextEntry(new java.util.zip.ZipEntry(entry.getKey()));zip.write(entry.getValue());zip.closeEntry();}
        }return out.toByteArray();
    }
    @Test void rejectsZipExpansionExternalRelationshipsAndDtd() throws Exception {
        var bomb=zip(java.util.Map.of("large.bin",new byte[20*1024*1024+1]));
        assertThrows(AppException.class,()->documents.importApplication(bomb));
        var external=zip(java.util.Map.of("word/_rels/document.xml.rels","<Relationships><Relationship TargetMode='External' Target='https://example.org/secret'/></Relationships>".getBytes()));
        assertThrows(AppException.class,()->documents.importApplication(external));
        var dtd=zip(java.util.Map.of("word/document.xml","<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///etc/passwd'>]><x>&e;</x>".getBytes()));
        assertThrows(AppException.class,()->documents.importApplication(dtd));
    }
    @Test void preservesRepeatedRowsAndMarkersSplitAcrossRuns() throws Exception {
        var original=Records.with(Examples.application(),java.util.Map.of("budget",java.util.List.of(Examples.application().budget().getFirst(),new Models.BudgetLine("Backup","Usługi","1.5","h","10.01","15.02"))));
        var bytes=documents.export(DocumentCodec.Type.application,original);var entries=new java.util.LinkedHashMap<String,byte[]>();
        try(var zip=new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(bytes))){
            java.util.zip.ZipEntry entry;while((entry=zip.getNextEntry())!=null)entries.put(entry.getName(),zip.readAllBytes());
        }
        var xml=DocumentCodec.parse(entries.get("word/document.xml"));var texts=xml.getElementsByTagNameNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main","t");
        for(int i=0;i<texts.getLength();i++)if("[application]".equals(texts.item(i).getTextContent())){
            var node=texts.item(i);node.setTextContent("[appli");var second=node.cloneNode(true);second.setTextContent("cation]");node.getParentNode().appendChild(second);break;
        }
        var out=new java.io.ByteArrayOutputStream();javax.xml.transform.TransformerFactory.newInstance().newTransformer().transform(new javax.xml.transform.dom.DOMSource(xml),new javax.xml.transform.stream.StreamResult(out));
        entries.put("word/document.xml",out.toByteArray());var actual=documents.importApplication(zip(entries));
        assertEquals(original.company(),actual.company());assertEquals(original.budget(),actual.budget());assertEquals(original.description(),actual.description());assertNull(actual.submittedAt());
    }

    @Test void importsTemplateEditedAndSavedByMicrosoftWord() throws Exception {
        var bytes=java.nio.file.Files.readAllBytes(java.nio.file.Path.of("../examples/docx/word-edited-application.docx"));
        var actual=documents.importApplication(bytes);
        assertEquals("Migracja Żółwia — edytowana w Word",actual.title());
        assertEquals(Examples.application().company(),actual.company());
        assertEquals(Examples.application().budget(),actual.budget());
        assertEquals(Examples.application().tasks(),actual.tasks());
        assertEquals(Examples.application().description(),actual.description());
        assertNull(actual.submittedAt());
    }

}
