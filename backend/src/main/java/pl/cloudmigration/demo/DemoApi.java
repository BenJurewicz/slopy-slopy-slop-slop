package pl.cloudmigration.demo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import org.springframework.web.bind.annotation.*;

import pl.cloudmigration.api.Errors;

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
@RequestMapping("/api/demo")
public class DemoApi {
    public record Confirmation(boolean confirmed) {
    }
    private final DemoDataset dataset;
    public DemoApi(DemoDataset dataset) {
        this.dataset = dataset;
    }

    @Operation(operationId = "loadDemo",
            summary = "Replace all data with a fresh demo dataset",
            description = "Deletes current documents and settings, then loads default CloudMigration settings and demo records atomically.")
    @ApiResponse(responseCode = "200",
            description = "Dane demonstracyjne wczytane",
            useReturnTypeSchema = true)
    @PostMapping(value = "/load", consumes = "application/json")
    public DemoDataset.DemoResult load(@RequestBody Confirmation confirmation) {
        requireConfirmation(confirmation);
        return dataset.replace();
    }

    @Operation(operationId = "resetData",
            summary = "Delete applications, evaluations and decisions",
            description = "Keeps CloudMigration settings. This action cannot be undone.")
    @ApiResponse(responseCode = "204", description = "Dane usunięte")
    @DeleteMapping(consumes = "application/json")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void reset(@RequestBody Confirmation confirmation) {
        requireConfirmation(confirmation);
        dataset.clear();
    }

    private void requireConfirmation(Confirmation confirmation) {
        if (confirmation == null || !confirmation.confirmed())
            throw pl.cloudmigration.api.AppException
                    .invalid("Potwierdź usunięcie danych.");
    }
}
