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
        var summary=reports.run("summary",new Reports.Parameters(c.id(),"0",0,100));assertEquals("50000",summary.rows().getFirst().get("awarded"));
    }
    @Test void crossDocumentReportsAndAllocationDataset(){
        var result=demo.load();var preview=funding.preview(result.competitionId());
        assertEquals(6,preview.rows().size());assertEquals(2,preview.rows().stream().filter(r->r.outcome().equals("AWARD")).count());
        assertEquals(1,reports.run("missing-evaluations",new Reports.Parameters(result.competitionId(),"0",0,100)).total());
        assertEquals(6,reports.run("ranking",new Reports.Parameters(result.competitionId(),"0",0,100)).total());
        assertEquals(4,reports.run("qualified",new Reports.Parameters(result.competitionId(),"0",0,100)).total());
        assertEquals(3,reports.run("over-budget",new Reports.Parameters(result.competitionId(),"50000",0,100)).total());
        var page=reports.run("applications",new Reports.Parameters(result.competitionId(),"0",2,2));assertEquals(7,page.total());assertEquals(2,page.rows().size());
        assertEquals(7,((Number)reports.run("providers",new Reports.Parameters(result.competitionId(),"0",0,100)).rows().getFirst().get("projects")).intValue());
        var drafts=funding.prepare(result.competitionId());assertEquals(6,drafts.size());
        funding.issue(drafts.stream().map(d->new Funding.IssueEntry(d.id(),d.revision())).toList());
        assertEquals("190000",reports.run("summary",new Reports.Parameters(result.competitionId(),"0",0,100)).rows().getFirst().get("awarded"));
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
        var demoResult=demo.load();var c=demoResult.competitionId();
        var preview=funding.preview(c);var allocation=reports.run("allocation",new Reports.Parameters(c,"0",0,100));
        assertEquals(preview.rows().stream().map(Funding.Allocation::outcome).toList(),allocation.rows().stream().map(row->row.get("outcome")).toList());
        var drafts=funding.prepare(c);var entries=new ArrayList<Funding.IssueEntry>();
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
        assertEquals(a.id(),funding.preview(c.id()).rows().getFirst().applicationId());
        assertEquals(a.id(),reports.run("ranking",new Reports.Parameters(c.id(),"0",0,100)).rows().getFirst().get("applicationId"));
        client.execute(adjust,Map.of("id",a.id(),"date","2026-10-12T00:00:00.100Z"));
        String expected=a.id().compareTo(b.id())<0?a.id():b.id();
        assertEquals(expected,funding.preview(c.id()).rows().getFirst().applicationId());
        assertEquals(expected,reports.run("ranking",new Reports.Parameters(c.id(),"0",0,100)).rows().getFirst().get("applicationId"));
    }

}
