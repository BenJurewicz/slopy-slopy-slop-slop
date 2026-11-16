package pl.cloudmigration.storage;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.*;
import java.nio.file.*;
import pl.cloudmigration.documents.*;
import pl.cloudmigration.documents.Models.*;
import pl.cloudmigration.documents.DocumentCodec.Type;
import pl.cloudmigration.workflow.*;
import pl.cloudmigration.demo.*;
import pl.cloudmigration.api.AppException;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
@SpringBootTest(properties="basex.database=cloudmigration_tests")
class WorkflowIntegrationTest {
    @Autowired Workflow workflow;
    @Autowired BaseXClient client;
    @Autowired Funding funding;
    @Autowired Reports reports;
    @Autowired DemoApi demo;
    @Autowired pl.cloudmigration.api.DocumentsApi documents;
    @BeforeEach void resetIsolatedTestDatabase() throws Exception {
        var props=new Properties();try(var in=Files.newInputStream(Path.of("../infra/.env"))){props.load(in);}
        var admin=new BaseXClient("http://127.0.0.1:8984/rest","admin",props.getProperty("BASEX_PASSWORD"),"cloudmigration_tests");
        admin.execute("declare variable $db external; if(db:exists($db)) then db:drop($db) else ()",Map.of());
        admin.execute("declare variable $db external; db:create($db)",Map.of());
    }
    Competition competition(){return (Competition)workflow.create(Type.competition,Examples.competition());}
    Application application(Competition c){return (Application)workflow.create(Type.application,Records.with(Examples.application(),Map.of("competitionId",c.id())));}
    Evaluation evaluate(Application a){
        var e=(Evaluation)workflow.create(Type.evaluation,Records.with(Examples.evaluation(),Map.of("applicationId",a.id())));
        return workflow.finalizeEvaluation(e.id(),e.revision());
    }
    @Test void singletonSettingsPreserveIdentityAndRejectOtherCompetitions(){
        var c=workflow.competition();assertEquals("CloudMigration",c.name());assertEquals(c,workflow.competition());
        assertThrows(AppException.class,()->workflow.create(Type.competition,Examples.competition()));
        var other=Records.with(c,Map.of("id",UUID.randomUUID().toString()));
        assertThrows(AppException.class,()->workflow.repository.save(Type.competition,Records.id(other),0,other));
        assertThrows(AppException.class,()->workflow.repository.delete(Type.competition,c.id(),c.revision()));
        var updated=(Competition)workflow.update(Type.competition,c.id(),Records.with(c,Map.of("budget","300000.00")));
        assertEquals(c.id(),updated.id());assertEquals("300000.00",workflow.competition().budget());
        assertThrows(AppException.class,()->workflow.update(Type.competition,c.id(),Records.with(updated,Map.of("name","Inny konkurs"))));
        var a=(Application)workflow.create(Type.application,Records.with(Examples.application(),java.util.Collections.singletonMap("competitionId",null)));
        assertEquals(c.id(),a.competitionId());
        assertThrows(AppException.class,()->workflow.create(Type.application,Records.with(Examples.application(),Map.of("competitionId",UUID.randomUUID().toString()))));
        assertThrows(AppException.class,()->workflow.update(Type.competition,c.id(),Records.with(updated,Map.of("budget","350000.00"))));
        var closed=(Competition)workflow.update(Type.competition,c.id(),Records.with(updated,Map.of("stage","CLOSED")));
        assertEquals("CLOSED",closed.stage());assertEquals(a,workflow.repository.get(Type.application,a.id()));
    }
    @Test void demoReplacesExistingRecordsAndSettingsWithoutAccumulatingDuplicates(){
        var c=competition();var a=application(c);a=workflow.submit(a.id(),a.revision());evaluate(a);
        var decisions=funding.prepare();funding.issue(decisions.stream().map(d->new Funding.IssueEntry(d.id(),d.revision())).toList());
        workflow.update(Type.competition,c.id(),Records.with(c,Map.of("stage","CLOSED")));
        var loaded=demo.load(new DemoApi.Confirmation(true));assertNotEquals(c.id(),loaded.competitionId());
        assertEquals("OPEN",workflow.competition().stage());assertEquals("250000.00",workflow.competition().budget());
        assertEquals(20,workflow.repository.list(Type.application).size());assertEquals(18,workflow.repository.list(Type.evaluation).size());
        assertTrue(workflow.repository.list(Type.decision).isEmpty());
        var previous=a;assertThrows(AppException.class,()->workflow.repository.get(Type.application,previous.id()));
        var firstIds=workflow.repository.list(Type.application).stream().map(Records::id).collect(java.util.stream.Collectors.toSet());
        demo.load(new DemoApi.Confirmation(true));assertEquals(20,workflow.repository.list(Type.application).size());
        assertTrue(workflow.repository.list(Type.application).stream().noneMatch(app->firstIds.contains(Records.id(app))));
        assertEquals(1,workflow.repository.list(Type.competition).size());
    }
    @Test void resetRemovesAllDocumentStatesButKeepsSettings(){
        demo.load(new DemoApi.Confirmation(true));var c=workflow.competition();
        var drafts=funding.prepare();funding.issue(drafts.stream().map(d->new Funding.IssueEntry(d.id(),d.revision())).toList());
        demo.reset(new DemoApi.Confirmation(true));assertEquals(c,workflow.competition());
        for(var type:List.of(Type.application,Type.evaluation,Type.decision))assertTrue(workflow.repository.list(type).isEmpty());
        demo.reset(new DemoApi.Confirmation(true));assertEquals(c,workflow.competition());
    }
    @Test void destructiveActionsRejectUnconfirmedRequestsWithoutChangingRecords(){
        demo.load(new DemoApi.Confirmation(true));var c=workflow.competition();var apps=workflow.repository.list(Type.application);
        assertThrows(AppException.class,()->demo.load(new DemoApi.Confirmation(false)));
        assertThrows(AppException.class,()->demo.reset(new DemoApi.Confirmation(false)));
        assertEquals(c,workflow.competition());assertEquals(apps,workflow.repository.list(Type.application));
    }
    @Test void invalidDemoReplacementDoesNotClearExistingRecords(){
        demo.load(new DemoApi.Confirmation(true));var c=workflow.competition();var apps=workflow.repository.list(Type.application);
        assertThrows(AppException.class,()->client.execute(client.resource("replace-demo"),Map.of("batch","<replacement/>")));
        assertEquals(c,workflow.competition());assertEquals(apps,workflow.repository.list(Type.application));
    }
    @Test void legacyMultipleCompetitionsAreRejectedWithoutDeletingData(){
        var c=competition();var second=Records.with(c,Map.of("id",UUID.randomUUID().toString()));
        var xml=new DocumentCodec().encode(Type.competition,second);
        client.execute("declare variable $db external; db:put($db,/*,concat('competitions/',string(/*/@id),'.xml'))",Map.of("xml",xml));
        assertEquals(409,assertThrows(AppException.class,()->workflow.competition()).status);
        assertEquals(2,workflow.repository.list(Type.competition).size());
        assertEquals(c,workflow.repository.get(Type.competition,c.id()));
    }
    @Test void concurrentInitializationCreatesExactlyOneConfiguration() throws Exception {
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(4)){
            var results=executor.invokeAll(java.util.Collections.nCopies(4,(java.util.concurrent.Callable<Competition>)workflow::competition));
            String id=results.getFirst().get().id();
            for(var result:results)assertEquals(id,result.get().id());
            assertEquals(1,workflow.repository.list(Type.competition).size());
        }
    }

    @Test void existingCompetitionIdentityAndLinkedDocumentsArePreserved(){
        var legacy=Records.with(Examples.competition(),Map.of("id",UUID.randomUUID().toString()));
        workflow.repository.save(Type.competition,Records.id(legacy),0,legacy);
        var a=application(legacy);assertEquals(legacy,workflow.competition());assertEquals(legacy.id(),a.competitionId());
        assertEquals(1,workflow.repository.list(Type.competition).size());
    }

    @Test void realCrudAndStaleRevision(){
        var c=competition();var a=application(c);assertEquals(a,workflow.repository.get(Type.application,a.id()));
        Application edited=Records.with(a,Map.of("title","Zmieniony tytuł"));
        var saved=(Application)workflow.update(Type.application,a.id(),edited);assertEquals(2,saved.revision());
        assertThrows(AppException.class,()->workflow.update(Type.application,a.id(),edited));
        assertThrows(AppException.class,()->workflow.repository.delete(Type.competition,c.id(),c.revision()));
        workflow.repository.delete(Type.application,a.id(),saved.revision());assertThrows(AppException.class,()->workflow.repository.get(Type.application,a.id()));
    }
    @Test void fullWorkflowAndAtomicBudget(){
        var c=competition();var a=application(c);a=workflow.submit(a.id(),a.revision());
        var submitted=a;assertThrows(AppException.class,()->workflow.update(Type.application,submitted.id(),submitted));
        var e=evaluate(a);var d=(Decision)workflow.create(Type.decision,Records.with(Examples.decision(),Map.of("applicationId",a.id(),"evaluationId",e.id())));
        assertThrows(AppException.class,()->funding.issue(List.of(new Funding.IssueEntry(d.id(),999))));
        assertEquals("DRAFT",workflow.repository.get(Type.decision,d.id(),Decision.class).state());
        var issued=funding.issue(List.of(new Funding.IssueEntry(d.id(),d.revision())));assertEquals("ISSUED",issued.getFirst().state());
        var summary=reports.run("summary",new Reports.Parameters("0",0,100));assertEquals("50000",summary.rows().getFirst().get("awarded"));
    }
    @Test void crossDocumentReportsAndAllocationDataset(){
        var result=demo.load(new DemoApi.Confirmation(true));assertEquals(20,result.applications());var preview=funding.preview();
        var applications=workflow.repository.list(Type.application).stream().map(Application.class::cast).toList();
        assertEquals(1,applications.stream().filter(app->"DRAFT".equals(app.state())).count());
        assertEquals(19,applications.stream().filter(app->"SUBMITTED".equals(app.state())).count());
        assertEquals(18,workflow.repository.list(Type.evaluation).size());
        assertTrue(workflow.repository.list(Type.decision).isEmpty());
        var outcomes=preview.rows().stream().collect(java.util.stream.Collectors.toMap(Funding.Allocation::title,Funding.Allocation::outcome));
        assertEquals("REJECT",outcomes.get("Projekt testowy 11"));
        assertEquals("AWARD",outcomes.get("Projekt testowy 12"));
        assertEquals("REJECT",outcomes.get("Projekt testowy 14"));
        assertEquals("AWARD",outcomes.get("Projekt testowy 15"));
        assertEquals("REJECT",outcomes.get("Projekt testowy 16"));
        assertEquals("REJECT",outcomes.get("Projekt testowy 17"));
        assertEquals(17,preview.rows().size());assertEquals(13,preview.rows().stream().filter(r->r.outcome().equals("AWARD")).count());
        assertEquals(2,reports.run("missing-evaluations",new Reports.Parameters("0",0,100)).total());
        assertEquals(17,reports.run("ranking",new Reports.Parameters("0",0,100)).total());
        assertEquals(15,reports.run("qualified",new Reports.Parameters("0",0,100)).total());
        assertEquals(1,reports.run("over-budget",new Reports.Parameters("50000",0,100)).total());
        var page=reports.run("applications",new Reports.Parameters("0",2,2));assertEquals(20,page.total());assertEquals(2,page.rows().size());
        assertEquals(20,((Number)reports.run("providers",new Reports.Parameters("0",0,100)).rows().getFirst().get("projects")).intValue());
        var drafts=funding.prepare();assertEquals(17,drafts.size());
        funding.issue(drafts.stream().map(d->new Funding.IssueEntry(d.id(),d.revision())).toList());
        var summary=reports.run("summary",new Reports.Parameters("0",0,100)).rows().getFirst();
        assertEquals("245000",summary.get("awarded"));assertEquals("5000",summary.get("remaining"));
    }
    @Test void demoHasOneEvaluationReadyForApprovalAndOneProjectWithoutAnEvaluation(){
        demo.load(new DemoApi.Confirmation(true));
        var apps=workflow.repository.list(Type.application).stream().map(Application.class::cast).collect(java.util.stream.Collectors.toMap(Application::title,app->app));
        var evaluations=workflow.repository.list(Type.evaluation).stream().map(Evaluation.class::cast).toList();
        var ready=evaluations.stream().filter(e->e.applicationId().equals(apps.get("Projekt testowy 18").id())).findFirst().orElseThrow();
        assertEquals("DRAFT",ready.state());assertEquals(80,ready.total());
        assertTrue(ready.scores().stream().allMatch(score->!score.comment().isBlank()));
        assertEquals("SUBMITTED",apps.get("Projekt testowy 19").state());
        assertTrue(evaluations.stream().noneMatch(e->e.applicationId().equals(apps.get("Projekt testowy 19").id())));
        assertEquals("DRAFT",apps.get("Projekt testowy 20").state());
        assertEquals(1,evaluations.stream().filter(e->"DRAFT".equals(e.state())).count());
        assertEquals("FINAL",workflow.finalizeEvaluation(ready.id(),ready.revision()).state());
        var pending=reports.run("missing-evaluations",new Reports.Parameters("0",0,100));
        assertEquals(1,pending.total());assertEquals("Projekt testowy 19",pending.rows().getFirst().get("title"));
    }

    @Test void filledExampleCanBeAddedAndGradedWithoutGradingThePendingDemoProject() throws Exception {
        demo.load(new DemoApi.Confirmation(true));
        var pending=reports.run("missing-evaluations",new Reports.Parameters("0",0,100)).rows().stream().filter(row->"Projekt testowy 19".equals(row.get("title"))).findFirst().orElseThrow();
        var file=new org.springframework.mock.web.MockMultipartFile("file","przyklad.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",documents.example().getBody());
        var preview=documents.preview(file);
        assertEquals("DRAFT",preview.application().state());
        assertEquals("Bezpieczna księgowość w chmurze",preview.application().title());
        var added=(Application)workflow.create(Type.application,preview.application());
        assertNotEquals(pending.get("applicationId"),added.id());
        assertEquals(21,workflow.repository.list(Type.application).size());
        added=workflow.submit(added.id(),added.revision());
        var evaluation=evaluate(added);assertEquals("FINAL",evaluation.state());assertEquals(80,evaluation.total());
        var ungraded=reports.run("missing-evaluations",new Reports.Parameters("0",0,100));
        assertEquals(2,ungraded.total());assertTrue(ungraded.rows().stream().anyMatch(row->pending.get("applicationId").equals(row.get("applicationId"))));
    }

    @Test void rejectsAnEntireOverBudgetBatchAndPreservesDrafts(){
        var c=(Competition)workflow.create(Type.competition,Records.with(Examples.competition(),Map.of("budget","60000.00","maxGrant","50000.00")));
        var drafts=new ArrayList<Decision>();
        for(int i=0;i<2;i++){
            var a=application(c);a=workflow.submit(a.id(),a.revision());var e=evaluate(a);
            drafts.add((Decision)workflow.create(Type.decision,Records.with(Examples.decision(),Map.of("applicationId",a.id(),"evaluationId",e.id()))));
        }
        assertThrows(AppException.class,()->funding.issue(drafts.stream().map(d->new Funding.IssueEntry(d.id(),d.revision())).toList()));
        drafts.forEach(d->assertEquals("DRAFT",workflow.repository.get(Type.decision,d.id(),Decision.class).state()));
        funding.issue(List.of(new Funding.IssueEntry(drafts.getFirst().id(),drafts.getFirst().revision())));
        var second=drafts.getLast();assertThrows(AppException.class,()->funding.issue(List.of(new Funding.IssueEntry(second.id(),second.revision()))));
        assertEquals("DRAFT",workflow.repository.get(Type.decision,second.id(),Decision.class).state());
    }
    @Test void staleEntryRollsBackMultiDecisionBatchAndAllocationAgreesWithXQuery(){
        var demoResult=demo.load(new DemoApi.Confirmation(true));var c=demoResult.competitionId();
        var preview=funding.preview();var allocation=reports.run("allocation",new Reports.Parameters("0",0,100));
        assertEquals(preview.rows().stream().map(Funding.Allocation::outcome).toList(),allocation.rows().stream().map(row->row.get("outcome")).toList());
        var drafts=funding.prepare();var entries=new ArrayList<Funding.IssueEntry>();
        for(var d:drafts)entries.add(new Funding.IssueEntry(d.id(),d.revision()));
        entries.set(entries.size()-1,new Funding.IssueEntry(drafts.getLast().id(),999));
        assertThrows(AppException.class,()->funding.issue(entries));
        drafts.forEach(d->assertEquals("DRAFT",workflow.repository.get(Type.decision,d.id(),Decision.class).state()));
    }
    @Test void preventsDuplicateEvaluationsAndFrozenEdits(){
        var c=competition();var a=application(c);var draft=a;
        assertThrows(AppException.class,()->workflow.create(Type.evaluation,Records.with(Examples.evaluation(),Map.of("applicationId",draft.id()))));
        a=workflow.submit(a.id(),a.revision());var finalEvaluation=evaluate(a);var submitted=a;
        assertThrows(AppException.class,()->workflow.create(Type.evaluation,Records.with(Examples.evaluation(),Map.of("applicationId",submitted.id()))));
        assertThrows(AppException.class,()->workflow.update(Type.evaluation,finalEvaluation.id(),finalEvaluation));
        assertThrows(AppException.class,()->workflow.repository.delete(Type.application,submitted.id(),submitted.revision()));
    }

    @Test void rankingOrdersInstantsRatherThanLexicalTimestampStrings(){
        var c=competition();var a=application(c);a=workflow.submit(a.id(),a.revision());evaluate(a);
        var b=application(c);b=workflow.submit(b.id(),b.revision());evaluate(b);
        String adjust="declare variable $db external; declare variable $id external; declare variable $date external; replace value of node db:get($db)/*:application[@id=$id]/*:submittedAt with $date";
        client.execute(adjust,Map.of("id",a.id(),"date","2026-10-12T00:00:00Z"));
        client.execute(adjust,Map.of("id",b.id(),"date","2026-10-12T00:00:00.1Z"));
        assertEquals(a.id(),funding.preview().rows().getFirst().applicationId());
        assertEquals(a.id(),reports.run("ranking",new Reports.Parameters("0",0,100)).rows().getFirst().get("applicationId"));
        client.execute(adjust,Map.of("id",a.id(),"date","2026-10-12T00:00:00.100Z"));
        String expected=a.id().compareTo(b.id())<0?a.id():b.id();
        assertEquals(expected,funding.preview().rows().getFirst().applicationId());
        assertEquals(expected,reports.run("ranking",new Reports.Parameters("0",0,100)).rows().getFirst().get("applicationId"));
    }

    @Test void readableExportsResolveActualLinkedDocumentsFromBaseX() throws Exception {
        var base=Examples.competition();var criteria=new ArrayList<>(base.criteria());criteria.set(0,new Criterion("technical","Rzeczywiste kryterium konkursu",25));
        var c=(Competition)workflow.create(Type.competition,Records.with(base,Map.of("name","Konkurs z bazy danych","criteria",criteria)));
        var a=(Application)workflow.create(Type.application,Records.with(Examples.application(),Map.of("competitionId",c.id(),"title","Projekt odczytany z bazy","company",new Company("Firma z rzeczywistego wniosku","9876543210","SMALL","Maria Testowa","maria@example.org","ul. Testowa 1, Gdańsk","123456785","Maria Testowa"))));a=workflow.submit(a.id(),a.revision());
        var e=(Evaluation)workflow.create(Type.evaluation,Records.with(Examples.evaluation(),Map.of("applicationId",a.id(),"reviewer","Rzeczywisty recenzent")));e=workflow.finalizeEvaluation(e.id(),e.revision());
        var d=(Decision)workflow.create(Type.decision,Records.with(Examples.decision(),Map.of("applicationId",a.id(),"evaluationId",e.id(),"reason","Uzasadnienie zapisane w bazie")));d=funding.issue(List.of(new Funding.IssueEntry(d.id(),d.revision()))).getFirst();
        for(var entry:Map.of(Type.application,a.id(),Type.evaluation,e.id(),Type.decision,d.id()).entrySet()){
            var bytes=documents.word(pl.cloudmigration.api.DocumentsApi.WordType.valueOf(entry.getKey().name()),entry.getValue()).getBody();String text="";
            try(var zip=new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(bytes))){java.util.zip.ZipEntry part;while((part=zip.getNextEntry())!=null)if(part.getName().equals("word/document.xml")){var xml=DocumentCodec.parse(zip.readAllBytes());var nodes=xml.getElementsByTagNameNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main","t");var visible=new StringBuilder();for(int i=0;i<nodes.getLength();i++)visible.append(nodes.item(i).getTextContent()).append('\n');text=visible.toString();}}
            if(entry.getKey()==Type.application)assertTrue(text.contains(c.name()));assertTrue(text.contains(a.title()));assertTrue(text.contains(a.company().name()));assertFalse(text.contains(Examples.application().company().name()));
            if(entry.getKey()==Type.evaluation){assertTrue(text.contains("Rzeczywiste kryterium konkursu"));assertTrue(text.contains("Rzeczywisty recenzent"));assertTrue(text.contains("Ocena końcowa"));}
            if(entry.getKey()==Type.decision){assertTrue(text.contains("Rzeczywisty recenzent"));assertTrue(text.contains("Uzasadnienie zapisane w bazie"));assertTrue(text.contains("Status: Wydana"));}
        }
    }

}
