package pl.cloudmigration.api;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import pl.cloudmigration.documents.DocumentCodec.Type;
import pl.cloudmigration.documents.Models.Application;
import pl.cloudmigration.workflow.Workflow;

@io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Nieprawidłowe żądanie", content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = pl.cloudmigration.api.Errors.Error.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Brak zasobu", content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = pl.cloudmigration.api.Errors.Error.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Konflikt rewizji lub procesu", content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = pl.cloudmigration.api.Errors.Error.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "413", description = "Za duży plik", content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = pl.cloudmigration.api.Errors.Error.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Błąd walidacji", content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = pl.cloudmigration.api.Errors.Error.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "Baza niedostępna", content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = pl.cloudmigration.api.Errors.Error.class)))})
@RestController
@RequestMapping("/api/applications")
public class ApplicationApi {
    public record Revision(int revision) {
    }
    private final Workflow workflow;
    public ApplicationApi(Workflow workflow) {
        this.workflow = workflow;
    }

    @Operation(operationId = "listApplications")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Operacja wykonana", useReturnTypeSchema = true)
    @GetMapping
    public List<Application> list() {
        return workflow.repository.list(Type.application).stream()
                .map(Application.class::cast).toList();
    }

    @Operation(operationId = "getApplication")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Operacja wykonana", useReturnTypeSchema = true)
    @GetMapping("/{id}")
    public Application get(@PathVariable String id) {
        return workflow.repository.get(Type.application, id, Application.class);
    }

    @Operation(operationId = "createApplication")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Operacja wykonana", useReturnTypeSchema = true)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Application create(@RequestBody Application value) {
        return (Application) workflow.create(Type.application, value);
    }

    @Operation(operationId = "updateApplication")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Operacja wykonana", useReturnTypeSchema = true)
    @PutMapping("/{id}")
    public Application update(@PathVariable String id,
            @RequestBody Application value) {
        return (Application) workflow.update(Type.application, id, value);
    }

    @Operation(operationId = "deleteApplication")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Operacja wykonana")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id, @RequestParam int revision) {
        workflow.repository.delete(Type.application, id, revision);
    }

    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Operacja wykonana", useReturnTypeSchema = true)
    @PostMapping("/{id}/submit")
    public Application submit(@PathVariable String id,
            @RequestBody Revision request) {
        return workflow.submit(id, request.revision());
    }

}
