package pl.cloudmigration.documents;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import pl.cloudmigration.api.AppException;
import pl.cloudmigration.demo.Examples;
import static org.junit.jupiter.api.Assertions.*;

class DocumentCodecTest {
    final DocumentCodec codec=new DocumentCodec();
    @TempDir Path temp;
    @Test void allDocumentsRoundTrip() {
        Object[] examples={Examples.competition(),Examples.application(),Examples.evaluation(),Examples.decision()};
        int i=0;
        for(var type:DocumentCodec.Type.values()) {
            var example=examples[i++]; var xml=codec.encode(type,example);
            assertEquals(example,codec.decode(type,xml,type.dto));
        }
    }
    @Test void generatedModelSupportsAccessAndModification() {
        var xml=codec.encode(DocumentCodec.Type.competition,Examples.competition());
        var model=(pl.cloudmigration.xml.model.Competition)codec.readGenerated(DocumentCodec.Type.competition,xml.getBytes(StandardCharsets.UTF_8));
        assertEquals(new BigDecimal("250000.00"),model.getBudget());
        model.setBudget(new BigDecimal("99999.99"));
        var result=codec.decode(DocumentCodec.Type.competition,codec.writeGenerated(DocumentCodec.Type.competition,model),Models.Competition.class);
        assertEquals("99999.99",result.budget());
    }
    @Test void generatedModelCreatesNewDocuments() {
        var existing=(pl.cloudmigration.xml.model.Competition)codec.readGenerated(DocumentCodec.Type.competition,codec.encode(DocumentCodec.Type.competition,Examples.competition()).getBytes(StandardCharsets.UTF_8));
        var fresh=new pl.cloudmigration.xml.model.Competition();
        fresh.setId(existing.getId());fresh.setRevision(existing.getRevision());fresh.setName(existing.getName());fresh.setDescription(existing.getDescription());
        fresh.setEligibilityRules(existing.getEligibilityRules());fresh.setCurrency(existing.getCurrency());fresh.setBudget(existing.getBudget());fresh.setMaxGrant(existing.getMaxGrant());fresh.setThreshold(existing.getThreshold());fresh.setStage(existing.getStage());fresh.getCriteria().addAll(existing.getCriteria());
        assertEquals(Examples.competition(),codec.decode(DocumentCodec.Type.competition,codec.writeGenerated(DocumentCodec.Type.competition,fresh),Models.Competition.class));
    }
    @Test void rejectsInvalidAmountAndMissingFields() {
        var xml=codec.encode(DocumentCodec.Type.competition,Examples.competition());
        assertThrows(AppException.class,()->codec.readGenerated(DocumentCodec.Type.competition,xml.replace("250000.00","-1").getBytes(StandardCharsets.UTF_8)));
        assertThrows(AppException.class,()->codec.readGenerated(DocumentCodec.Type.competition,"<competition/>".getBytes(StandardCharsets.UTF_8)));
    }
    @Test void rejectsExternalEntitiesAndWrongDocumentType() throws Exception {
        var secret=temp.resolve("secret");Files.writeString(secret,"secret-value");
        String xml="<!DOCTYPE competition [<!ENTITY xxe SYSTEM '"+secret.toUri()+"'>]><competition>&xxe;</competition>";
        assertThrows(AppException.class,()->codec.readGenerated(DocumentCodec.Type.competition,xml.getBytes(StandardCharsets.UTF_8)));
        assertThrows(AppException.class,()->codec.readGenerated(DocumentCodec.Type.application,codec.encode(DocumentCodec.Type.competition,Examples.competition()).getBytes(StandardCharsets.UTF_8)));
    }
    @Test void schemaRejectsDuplicateCriterionCodesAndWhitespaceText(){
        var xml=codec.encode(DocumentCodec.Type.competition,Examples.competition());
        assertThrows(AppException.class,()->codec.readGenerated(DocumentCodec.Type.competition,xml.replace(">security<",">technical<").getBytes(StandardCharsets.UTF_8)));
        assertThrows(AppException.class,()->codec.readGenerated(DocumentCodec.Type.competition,xml.replace("CloudMigration 2026","   ").getBytes(StandardCharsets.UTF_8)));
    }

}
