package pl.cloudmigration.api;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import org.springframework.web.bind.annotation.*;

import pl.cloudmigration.documents.Models.Decision;
import pl.cloudmigration.workflow.*;

@ApiResponses({
        @ApiResponse(responseCode = "400",
                description = "Nieprawidłowe żądanie",
                content = @Content(mediaType = "application/json",
                        schema = @Schema(implementation = Errors.Error.class))),
        @ApiResponse(responseCode = "404", description = "Brak zasobu",
                content = @Content(mediaType = "application/json",
                        schema = @Schema(implementation = Errors.Error.class))),
        @ApiResponse(responseCode = "409",
                description = "Konflikt rewizji lub procesu",
                content = @Content(mediaType = "application/json",
                        schema = @Schema(implementation = Errors.Error.class))),
        @ApiResponse(responseCode = "413", description = "Za duży plik",
                content = @Content(mediaType = "application/json",
                        schema = @Schema(implementation = Errors.Error.class))),
        @ApiResponse(responseCode = "422", description = "Błąd walidacji",
                content = @Content(mediaType = "application/json",
                        schema = @Schema(implementation = Errors.Error.class))),
        @ApiResponse(responseCode = "503", description = "Baza niedostępna",
                content = @Content(mediaType = "application/json",
                        schema = @Schema(
                                implementation = Errors.Error.class)))})
@RestController
@RequestMapping("/api")
public class ReportsApi {
    private final Reports reports;
    private final Funding funding;
    public ReportsApi(Reports reports, Funding funding) {
        this.reports = reports;
        this.funding = funding;
    }

    @Operation(operationId = "listQueries")
    @ApiResponse(responseCode = "200", description = "Operacja wykonana",
            useReturnTypeSchema = true)
    @GetMapping("/queries")
    public List<Reports.Query> queries() {
        return reports.catalogue();
    }

    @Operation(operationId = "executeQuery")
    @ApiResponse(responseCode = "200", description = "Operacja wykonana",
            useReturnTypeSchema = true)
    @PostMapping("/queries/{id}/execute")
    public Reports.Result execute(@PathVariable String id,
            @RequestBody Reports.Parameters parameters) {
        return reports.run(id, parameters);
    }

    @Operation(operationId = "previewFunding")
    @ApiResponse(responseCode = "200", description = "Operacja wykonana",
            useReturnTypeSchema = true)
    @GetMapping("/funding")
    public Funding.Preview preview() {
        return funding.preview();
    }

    @Operation(operationId = "prepareDecisions")
    @ApiResponse(responseCode = "200", description = "Operacja wykonana",
            useReturnTypeSchema = true)
    @PostMapping("/funding/prepare")
    public List<Decision> prepare() {
        return funding.prepare();
    }

    @Operation(operationId = "issueDecisions")
    @ApiResponse(responseCode = "200", description = "Operacja wykonana",
            useReturnTypeSchema = true)
    @PostMapping("/funding/issue")
    public List<Decision> issue(@RequestBody List<Funding.IssueEntry> entries) {
        return funding.issue(entries);
    }
}
