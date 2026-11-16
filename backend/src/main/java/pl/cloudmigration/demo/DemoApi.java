package pl.cloudmigration.demo;

import java.util.*;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import pl.cloudmigration.documents.*;
import pl.cloudmigration.documents.Models.*;
import pl.cloudmigration.documents.DocumentCodec.Type;
import pl.cloudmigration.workflow.Workflow;
import pl.cloudmigration.api.AppException;

@io.swagger.v3.oas.annotations.responses.ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="400",description="Nieprawidłowe żądanie",content=@io.swagger.v3.oas.annotations.media.Content(mediaType="application/json",schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=pl.cloudmigration.api.Errors.Error.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="404",description="Brak zasobu",content=@io.swagger.v3.oas.annotations.media.Content(mediaType="application/json",schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=pl.cloudmigration.api.Errors.Error.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="409",description="Konflikt rewizji lub procesu",content=@io.swagger.v3.oas.annotations.media.Content(mediaType="application/json",schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=pl.cloudmigration.api.Errors.Error.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="413",description="Za duży plik",content=@io.swagger.v3.oas.annotations.media.Content(mediaType="application/json",schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=pl.cloudmigration.api.Errors.Error.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="422",description="Błąd walidacji",content=@io.swagger.v3.oas.annotations.media.Content(mediaType="application/json",schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=pl.cloudmigration.api.Errors.Error.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="503",description="Baza niedostępna",content=@io.swagger.v3.oas.annotations.media.Content(mediaType="application/json",schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=pl.cloudmigration.api.Errors.Error.class)))
})
@RestController
@RequestMapping("/api/demo")
public class DemoApi {
    private final Workflow workflow;
    public DemoApi(Workflow workflow){this.workflow=workflow;}
    public record DemoResult(String competitionId,int applications) {}
    private record Example(String amount,Integer score,boolean eligible,boolean submitted) {}
    private static final List<Example> EXAMPLES=List.of(
        new Example("20000.00",98,true,true),new Example("25000.00",96,true,true),
        new Example("15000.00",96,true,true),new Example("30000.00",92,true,true),
        new Example("20000.00",90,true,true),new Example("25000.00",88,true,true),
        new Example("10000.00",86,true,true),new Example("15000.00",84,true,true),
        new Example("20000.00",82,true,true),new Example("15000.00",80,true,true),
        new Example("60000.00",78,true,true),new Example("10000.00",76,true,true),
        new Example("15000.00",74,true,true),new Example("50000.00",72,true,true),
        new Example("25000.00",70,true,true),new Example("10000.00",55,true,true),
        new Example("10000.00",80,false,true),new Example("20000.00",null,true,true),
        new Example("10000.00",null,true,false),new Example("5000.00",null,true,false));
    @Operation(operationId="loadDemo")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="200",description="Operacja wykonana",useReturnTypeSchema=true)
    @PostMapping("/load") public DemoResult load(){
        if(!workflow.repository.list(Type.application).isEmpty()||!workflow.repository.list(Type.evaluation).isEmpty()||!workflow.repository.list(Type.decision).isEmpty())throw AppException.conflict("Dane demonstracyjne można wczytać tylko, gdy nie ma wniosków, ocen ani decyzji.");
        var c=workflow.competition();
        if(!"OPEN".equals(c.stage()))throw AppException.conflict("Wczytanie przykładu wymaga otwartego naboru.");
        if(pl.cloudmigration.workflow.Rules.money(c.maxGrant()).compareTo(new java.math.BigDecimal("60000.00"))<0)throw AppException.conflict("Przykład wymaga limitu dotacji co najmniej 60000 PLN.");
        int maximum=c.criteria().stream().mapToInt(Criterion::maximum).sum();
        if(maximum!=100||c.threshold()!=60)throw AppException.conflict("Przykład wymaga kryteriów o sumie 100 punktów i progu 60.");
        for(int i=0;i<EXAMPLES.size();i++){
            var example=EXAMPLES.get(i);
            var changes=new HashMap<String,Object>();changes.put("competitionId",c.id());changes.put("title",String.format("Projekt testowy %02d",i+1));changes.put("requestedAmount",example.amount());changes.put("projectNumber",null);
            changes.put("company",new Company("Firma "+(i+1),String.format("%010d",1000000000L+i),example.eligible()?"MICRO":"OTHER","Anna Kowalska","firma"+i+"@example.org","ul. Testowa "+(i+1)+", 80-001 Gdańsk",String.format("%09d",100000000+i),"Anna Kowalska"));
            changes.put("budget",List.of(new BudgetLine("Migracja i konfiguracja","Usługi","1","projekt",example.amount(),example.amount())));
            var a=(Application)workflow.create(Type.application,Records.with(Examples.application(),changes));
            if(example.submitted())a=workflow.submit(a.id(),a.revision());
            if(example.score()==null)continue;
            var points=new ArrayList<Score>();int remaining=example.score();
            for(var criterion:c.criteria()){int score=Math.min(criterion.maximum(),remaining);remaining-=score;points.add(new Score(criterion.code(),score,"Ocena demonstracyjna"));}
            var e=(Evaluation)workflow.create(Type.evaluation,new Evaluation(null,0,a.id(),"DRAFT","Jan Nowak",java.time.LocalDate.now().toString(),example.eligible(),!example.eligible()?"Firma nie jest mikro ani małym przedsiębiorstwem.":"Warunki spełnione.",points,example.score(),null));
            workflow.finalizeEvaluation(e.id(),e.revision());
        }
        return new DemoResult(c.id(),EXAMPLES.size());
    }
}
