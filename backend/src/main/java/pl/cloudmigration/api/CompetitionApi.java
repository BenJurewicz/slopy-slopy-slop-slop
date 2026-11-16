package pl.cloudmigration.api;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;
import pl.cloudmigration.documents.DocumentCodec.Type;
import pl.cloudmigration.documents.Models.Competition;
import pl.cloudmigration.workflow.Workflow;

@io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Nieprawidłowe żądanie", content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = pl.cloudmigration.api.Errors.Error.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Brak zasobu", content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = pl.cloudmigration.api.Errors.Error.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Konflikt rewizji lub procesu", content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = pl.cloudmigration.api.Errors.Error.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "413", description = "Za duży plik", content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = pl.cloudmigration.api.Errors.Error.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Błąd walidacji", content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = pl.cloudmigration.api.Errors.Error.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "Baza niedostępna", content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = pl.cloudmigration.api.Errors.Error.class)))})
@RestController
@RequestMapping("/api/competition")
public class CompetitionApi {
    private final Workflow workflow;
    public CompetitionApi(Workflow workflow) {
        this.workflow = workflow;
    }
    @Operation(operationId = "getCompetition")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Ustawienia CloudMigration", useReturnTypeSchema = true)
    @GetMapping
    public Competition get() {
        return workflow.competition();
    }
    @Operation(operationId = "updateCompetition")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Ustawienia zapisane", useReturnTypeSchema = true)
    @PutMapping
    public Competition update(@RequestBody Competition value) {
        return (Competition) workflow.update(Type.competition,
                workflow.competition().id(), value);
    }
}
