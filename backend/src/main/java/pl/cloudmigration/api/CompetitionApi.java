package pl.cloudmigration.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import org.springframework.web.bind.annotation.*;

import pl.cloudmigration.documents.DocumentCodec.Type;
import pl.cloudmigration.documents.Models.Competition;
import pl.cloudmigration.workflow.Workflow;

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
@RequestMapping("/api/competition")
public class CompetitionApi {
    private final Workflow workflow;
    public CompetitionApi(Workflow workflow) {
        this.workflow = workflow;
    }

    @Operation(operationId = "getCompetition")
    @ApiResponse(responseCode = "200",
            description = "Ustawienia CloudMigration",
            useReturnTypeSchema = true)
    @GetMapping
    public Competition get() {
        return workflow.competition();
    }

    @Operation(operationId = "updateCompetition")
    @ApiResponse(responseCode = "200", description = "Ustawienia zapisane",
            useReturnTypeSchema = true)
    @PutMapping
    public Competition update(@RequestBody Competition value) {
        return (Competition) workflow.update(Type.competition,
                workflow.competition().id(), value);
    }
}
