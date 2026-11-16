package pl.cloudmigration.api;

import java.util.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import pl.cloudmigration.documents.*;
import pl.cloudmigration.documents.DocumentCodec.Type;
import pl.cloudmigration.documents.Models.*;
import pl.cloudmigration.docx.DocxDocuments;
import pl.cloudmigration.storage.DocumentRepository;
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
@RequestMapping("/api/documents")
public class DocumentsApi {
    public record ImportPreview(Application application,
            List<String> diagnostics) {
    }
    private final DocumentCodec codec;
    private final DocxDocuments docx;
    private final DocumentRepository repository;
    private final Workflow workflow;
    public DocumentsApi(DocumentCodec codec, DocxDocuments docx,
            DocumentRepository repository, Workflow workflow) {
        this.codec = codec;
        this.docx = docx;
        this.repository = repository;
        this.workflow = workflow;
    }

    @Operation(operationId = "downloadTemplate")
    @ApiResponse(responseCode = "200", description = "Plik do pobrania",
            content = @Content(
                    mediaType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    schema = @Schema(type = "string", format = "binary")))
    @GetMapping("/template")
    public ResponseEntity<byte[]> template() {
        var competition = workflow.competition();
        return download(docx.template(competition), "wniosek-formularz.docx",
                false);
    }

    @Operation(operationId = "downloadApplicationExample")
    @ApiResponse(responseCode = "200",
            description = "Wypełniony przykład, nie pusty formularz",
            content = @Content(
                    mediaType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    schema = @Schema(type = "string", format = "binary")))
    @GetMapping("/example")
    public ResponseEntity<byte[]> example() {
        var competition = workflow.competition();
        return download(docx.example(competition), "wniosek-przyklad.docx",
                false);
    }

    @PostMapping(value = "/import",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(operationId = "importApplication")
    @ApiResponse(responseCode = "200", description = "Operacja wykonana",
            useReturnTypeSchema = true)
    public ImportPreview preview(@RequestPart("file") MultipartFile file)
            throws java.io.IOException {
        var competition = workflow.competition();
        var application = docx.importApplication(file.getBytes(),
                competition.id());
        workflow.validate(Type.application, application);
        return new ImportPreview(application, List.of(
                "Dokument odczytany. Zapisz podgląd jako nowy szkic CloudMigration."));
    }

    @Operation(operationId = "downloadXml")
    @ApiResponse(responseCode = "200", description = "Plik do pobrania",
            content = @Content(mediaType = "application/xml",
                    schema = @Schema(type = "string", format = "binary")))
    @GetMapping("/{type}/{id}/xml")
    public ResponseEntity<byte[]> xml(@PathVariable Type type,
            @PathVariable String id) {
        return download(
                codec.encode(type, repository.get(type, id))
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8),
                type + "-" + id + ".xml", true);
    }
    public enum WordType {
        application, evaluation, decision
    }
    @Operation(operationId = "downloadDocx")
    @ApiResponse(responseCode = "200", description = "Plik do pobrania",
            content = @Content(
                    mediaType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    schema = @Schema(type = "string", format = "binary")))
    @GetMapping("/{type}/{id}/docx")
    public ResponseEntity<byte[]> word(@PathVariable WordType type,
            @PathVariable String id) {
        var kind = Type.valueOf(type.name());
        var document = repository.get(kind, id);
        return download(docx.export(kind, document, context(kind, document)),
                type + "-" + id + ".docx", false);
    }

    private DocxDocuments.Context context(Type type, Object document) {
        if (type == Type.competition)
            return new DocxDocuments.Context((Competition) document, null,
                    null);
        var application = applicationFor(type, document);
        var competition = repository.get(Type.competition,
                application.competitionId(), Competition.class);
        var evaluation = evaluationFor(type, document);
        return new DocxDocuments.Context(competition, application, evaluation);
    }

    private Application applicationFor(Type type, Object document) {
        if (type == Type.application)
            return (Application) document;
        var applicationId = type == Type.evaluation
                ? ((Evaluation) document).applicationId()
                : ((Decision) document).applicationId();
        return repository.get(Type.application, applicationId,
                Application.class);
    }

    private Evaluation evaluationFor(Type type, Object document) {
        if (type == Type.evaluation)
            return (Evaluation) document;
        if (type == Type.decision)
            return repository.get(Type.evaluation,
                    ((Decision) document).evaluationId(), Evaluation.class);
        return null;
    }

    private ResponseEntity<byte[]> download(byte[] value, String name,
            boolean xml) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(name).build()
                                .toString())
                .contentType(MediaType.parseMediaType(xml
                        ? "application/xml"
                        : "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(value);
    }
}
