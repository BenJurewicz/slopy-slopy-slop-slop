package pl.cloudmigration.api;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import pl.cloudmigration.documents.DocumentCodec.Type;
import pl.cloudmigration.documents.Models.Competition;
import pl.cloudmigration.workflow.Workflow;

@io.swagger.v3.oas.annotations.responses.ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="400",description="Nieprawidłowe żądanie",content=@io.swagger.v3.oas.annotations.media.Content(mediaType="application/json",schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=pl.cloudmigration.api.Errors.Error.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="404",description="Brak zasobu",content=@io.swagger.v3.oas.annotations.media.Content(mediaType="application/json",schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=pl.cloudmigration.api.Errors.Error.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="409",description="Konflikt rewizji lub procesu",content=@io.swagger.v3.oas.annotations.media.Content(mediaType="application/json",schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=pl.cloudmigration.api.Errors.Error.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="413",description="Za duży plik",content=@io.swagger.v3.oas.annotations.media.Content(mediaType="application/json",schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=pl.cloudmigration.api.Errors.Error.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="422",description="Błąd walidacji",content=@io.swagger.v3.oas.annotations.media.Content(mediaType="application/json",schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=pl.cloudmigration.api.Errors.Error.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="503",description="Baza niedostępna",content=@io.swagger.v3.oas.annotations.media.Content(mediaType="application/json",schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=pl.cloudmigration.api.Errors.Error.class)))
})
@RestController
@RequestMapping("/api/competitions")
public class CompetitionApi {
    public record Revision(int revision) {}
    private final Workflow workflow;
    public CompetitionApi(Workflow workflow){this.workflow=workflow;}
    @Operation(operationId="listCompetitions")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="200",description="Operacja wykonana",useReturnTypeSchema=true)
    @GetMapping public List<Competition> list(){return workflow.repository.list(Type.competition).stream().map(Competition.class::cast).toList();}
    @Operation(operationId="getCompetition")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="200",description="Operacja wykonana",useReturnTypeSchema=true)
    @GetMapping("/{id}") public Competition get(@PathVariable String id){return workflow.repository.get(Type.competition,id,Competition.class);}
    @Operation(operationId="createCompetition")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="201",description="Operacja wykonana",useReturnTypeSchema=true)
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public Competition create(@RequestBody Competition value){return (Competition)workflow.create(Type.competition,value);}
    @Operation(operationId="updateCompetition")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="200",description="Operacja wykonana",useReturnTypeSchema=true)
    @PutMapping("/{id}") public Competition update(@PathVariable String id,@RequestBody Competition value){return (Competition)workflow.update(Type.competition,id,value);}
    @Operation(operationId="deleteCompetition")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="204",description="Operacja wykonana")
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable String id,@RequestParam int revision){workflow.repository.delete(Type.competition,id,revision);}

}
