package pl.cloudmigration.api;

import java.util.List;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import pl.cloudmigration.workflow.*;
import pl.cloudmigration.documents.Models.Decision;


@io.swagger.v3.oas.annotations.responses.ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="400",description="Nieprawidłowe żądanie",content=@io.swagger.v3.oas.annotations.media.Content(mediaType="application/json",schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=pl.cloudmigration.api.Errors.Error.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="404",description="Brak zasobu",content=@io.swagger.v3.oas.annotations.media.Content(mediaType="application/json",schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=pl.cloudmigration.api.Errors.Error.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="409",description="Konflikt rewizji lub procesu",content=@io.swagger.v3.oas.annotations.media.Content(mediaType="application/json",schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=pl.cloudmigration.api.Errors.Error.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="413",description="Za duży plik",content=@io.swagger.v3.oas.annotations.media.Content(mediaType="application/json",schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=pl.cloudmigration.api.Errors.Error.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="422",description="Błąd walidacji",content=@io.swagger.v3.oas.annotations.media.Content(mediaType="application/json",schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=pl.cloudmigration.api.Errors.Error.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="503",description="Baza niedostępna",content=@io.swagger.v3.oas.annotations.media.Content(mediaType="application/json",schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=pl.cloudmigration.api.Errors.Error.class)))
})
@RestController
@RequestMapping("/api")
public class ReportsApi {
    private final Reports reports;private final Funding funding;
    public ReportsApi(Reports reports,Funding funding){this.reports=reports;this.funding=funding;}
    @Operation(operationId="listQueries")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="200",description="Operacja wykonana",useReturnTypeSchema=true)
    @GetMapping("/queries") public List<Reports.Query> queries(){return reports.catalogue();}
    @Operation(operationId="executeQuery")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="200",description="Operacja wykonana",useReturnTypeSchema=true)
    @PostMapping("/queries/{id}/execute") public Reports.Result execute(@PathVariable String id,@RequestBody Reports.Parameters parameters){return reports.run(id,parameters);}
    @Operation(operationId="previewFunding")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="200",description="Operacja wykonana",useReturnTypeSchema=true)
    @GetMapping("/funding") public Funding.Preview preview(){return funding.preview();}
    @Operation(operationId="prepareDecisions")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="200",description="Operacja wykonana",useReturnTypeSchema=true)
    @PostMapping("/funding/prepare") public List<Decision> prepare(){return funding.prepare();}
    @Operation(operationId="issueDecisions")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="200",description="Operacja wykonana",useReturnTypeSchema=true)
    @PostMapping("/funding/issue") public List<Decision> issue(@RequestBody List<Funding.IssueEntry> entries){return funding.issue(entries);}
}
