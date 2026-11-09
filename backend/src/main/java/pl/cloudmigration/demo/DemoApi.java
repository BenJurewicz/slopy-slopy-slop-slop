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
    @Operation(operationId="loadDemo")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="200",description="Operacja wykonana",useReturnTypeSchema=true)
    @PostMapping("/load") public DemoResult load(){
        if(!workflow.repository.list(Type.competition).isEmpty())throw AppException.conflict("Dane demonstracyjne można wczytać tylko do pustej bazy.");
        var c=(Competition)workflow.create(Type.competition,Examples.competition());
        String[] amounts={"140000.00","130000.00","50000.00","65000.00","30000.00","20000.00","45000.00"};
        String[] names={"Migracja księgowości","E-commerce bezserwerowy","Backup małej firmy","Chmurowy system magazynowy","Projekt poniżej progu","Duże przedsiębiorstwo","Wniosek oczekujący na ocenę"};
        int[] scores={90,85,85,84,55,80,0};
        for(int i=0;i<amounts.length;i++){
            var changes=new HashMap<String,Object>();changes.put("competitionId",c.id());changes.put("title",names[i]);changes.put("requestedAmount",amounts[i]);changes.put("projectNumber",null);
            changes.put("company",new Company("Firma "+(i+1),"123456789"+i,i==5?"OTHER":"MICRO","Anna Kowalska","firma"+i+"@example.org","ul. Demonstracyjna "+(i+1)+", 80-001 Gdańsk",String.format("%09d",100000000+i),"Anna Kowalska – właścicielka"));
            changes.put("budget",List.of(new BudgetLine("Migracja i konfiguracja","Usługi","1","projekt",amounts[i],amounts[i])));
            var a=(Application)workflow.create(Type.application,Records.with(Examples.application(),changes));a=workflow.submit(a.id(),a.revision());
            if(i==6)continue;
            var points=new ArrayList<Score>();int remaining=scores[i];
            for(var criterion:c.criteria()){int score=Math.min(criterion.maximum(),remaining);remaining-=score;points.add(new Score(criterion.code(),score,"Ocena demonstracyjna"));}
            var e=(Evaluation)workflow.create(Type.evaluation,new Evaluation(null,0,a.id(),"DRAFT","Jan Nowak",java.time.LocalDate.now().toString(),i!=5,i==5?"Firma nie jest mikro ani małym przedsiębiorstwem.":"Warunki spełnione.",points,scores[i],null));
            workflow.finalizeEvaluation(e.id(),e.revision());
        }
        return new DemoResult(c.id(),7);
    }
}
