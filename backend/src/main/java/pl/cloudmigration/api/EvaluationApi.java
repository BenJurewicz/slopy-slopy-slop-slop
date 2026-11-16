package pl.cloudmigration.api;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import pl.cloudmigration.documents.DocumentCodec.Type;
import pl.cloudmigration.documents.Models.Evaluation;
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
@RequestMapping("/api/evaluations")
public class EvaluationApi {
    public record Revision(int revision) {
    }
    private final Workflow workflow;
    public EvaluationApi(Workflow workflow) {
        this.workflow = workflow;
    }

    @Operation(operationId = "listEvaluations")
    @ApiResponse(responseCode = "200", description = "Operacja wykonana",
            useReturnTypeSchema = true)
    @GetMapping
    public List<Evaluation> list() {
        return workflow.repository.list(Type.evaluation).stream()
                .map(Evaluation.class::cast).toList();
    }

    @Operation(operationId = "getEvaluation")
    @ApiResponse(responseCode = "200", description = "Operacja wykonana",
            useReturnTypeSchema = true)
    @GetMapping("/{id}")
    public Evaluation get(@PathVariable String id) {
        return workflow.repository.get(Type.evaluation, id, Evaluation.class);
    }

    @Operation(operationId = "createEvaluation")
    @ApiResponse(responseCode = "201", description = "Operacja wykonana",
            useReturnTypeSchema = true)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Evaluation create(@RequestBody Evaluation value) {
        return (Evaluation) workflow.create(Type.evaluation, value);
    }

    @Operation(operationId = "updateEvaluation")
    @ApiResponse(responseCode = "200", description = "Operacja wykonana",
            useReturnTypeSchema = true)
    @PutMapping("/{id}")
    public Evaluation update(@PathVariable String id,
            @RequestBody Evaluation value) {
        return (Evaluation) workflow.update(Type.evaluation, id, value);
    }

    @Operation(operationId = "deleteEvaluation")
    @ApiResponse(responseCode = "204", description = "Operacja wykonana")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id, @RequestParam int revision) {
        workflow.repository.delete(Type.evaluation, id, revision);
    }

    @ApiResponse(responseCode = "200", description = "Operacja wykonana",
            useReturnTypeSchema = true)
    @PostMapping("/{id}/finalize")
    public Evaluation finalizeDocument(@PathVariable String id,
            @RequestBody Revision request) {
        return workflow.finalizeEvaluation(id, request.revision());
    }

}
