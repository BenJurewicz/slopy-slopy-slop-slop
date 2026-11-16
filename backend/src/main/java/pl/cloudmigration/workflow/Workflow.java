package pl.cloudmigration.workflow;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import pl.cloudmigration.api.AppException;
import pl.cloudmigration.documents.*;
import pl.cloudmigration.documents.DocumentCodec.Type;
import pl.cloudmigration.documents.Models.*;
import pl.cloudmigration.storage.*;

@Service
public class Workflow {
    private static final String DEFAULT_COMPETITION_ID = "00000000-0000-0000-0000-000000000001";
    public final DocumentRepository repository;
    private final DocumentCodec codec;
    public Workflow(DocumentRepository repository, DocumentCodec codec) {
        this.repository = repository;
        this.codec = codec;
    }

    /**
     * Preserve the existing XML identity; initialize settings only for an empty
     * database.
     */
    public Competition competition() {
        var existing = repository.list(Type.competition);
        if (existing.size() > 1)
            throw AppException.conflict(
                    "Baza zawiera wiele konkursów. Wymagana jest jedna konfiguracja CloudMigration; żadne dane nie zostały usunięte.");
        if (!existing.isEmpty())
            return (Competition) existing.getFirst();
        return initializeCompetition();
    }

    private Competition initializeCompetition() {
        var defaults = new Competition(DEFAULT_COMPETITION_ID, 0,
                "CloudMigration",
                "Migracja mikro- i małych przedsiębiorstw do chmury.",
                Models.ELIGIBILITY_RULES, "PLN", "250000.00", "150000.00", 60,
                "OPEN",
                List.of(new Criterion("technical", "Wykonalność techniczna",
                        25), new Criterion("security", "Bezpieczeństwo", 25),
                        new Criterion("benefits", "Korzyści biznesowe", 20),
                        new Criterion("team", "Harmonogram i zespół", 20),
                        new Criterion("budget", "Uzasadnienie budżetu", 10)));
        try {
            return (Competition) create(Type.competition, defaults);
        } catch (AppException conflict) {
            if (conflict.status != 409)
                throw conflict;
            var concurrent = repository.list(Type.competition);
            if (concurrent.size() == 1)
                return (Competition) concurrent.getFirst();
            throw conflict;
        }
    }

    public Object create(Type type, Object value) {
        if (type == Type.competition
                && !repository.list(Type.competition).isEmpty())
            throw AppException.conflict(
                    "CloudMigration jest jedynym konkursem. Zmień jego ustawienia.");

        var document = normalize(type,
                Records.with(value, initialMetadata(type, value)));
        validate(type, document);
        repository.save(type, Records.id(document), 0, document);
        return document;
    }

    private Map<String, Object> initialMetadata(Type type, Object value) {
        var changes = new HashMap<String, Object>();
        var id = type == Type.competition
                ? DEFAULT_COMPETITION_ID
                : UUID.randomUUID().toString();
        changes.put("id", id);
        changes.put("revision", 1);

        if (type == Type.competition) {
            changes.put("name", "CloudMigration");
            if (((Competition) value).eligibilityRules() == null)
                changes.put("eligibilityRules", Models.ELIGIBILITY_RULES);
            return changes;
        }

        changes.put("state", "DRAFT");
        if (type == Type.application)
            changes.put("submittedAt", null);
        changes.put("number", number(numberPrefix(type), id));
        return changes;
    }

    private String numberPrefix(Type type) {
        if (type == Type.application)
            return "WN";
        if (type == Type.evaluation)
            return "OC";
        return "DEC";
    }

    public Object update(Type type, String id, Object value) {
        if (!id.equals(Records.id(value)))
            throw AppException
                    .invalid("Identyfikator dokumentu nie odpowiada adresowi.");

        var current = repository.get(type, id);
        validateUpdate(type, id, current, value);
        return replace(type, id, value);
    }

    private void validateUpdate(Type type, String id, Object current,
            Object replacement) {
        if (type == Type.competition) {
            Rules.require(id.equals(competition().id()),
                    "Nieprawidłowa konfiguracja CloudMigration.");
            Rules.require(
                    Objects.equals(((Competition) replacement).name(),
                            ((Competition) current).name()),
                    "name", "Nazwa konkursu nie podlega zmianie.");
            return;
        }

        requireDraft(current);
        requireDraft(replacement);
    }

    private void requireDraft(Object document) {
        try {
            var state = document.getClass().getMethod("state").invoke(document);
            if (!"DRAFT".equals(state))
                throw AppException.conflict("Dokument jest zablokowany.");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private Object replace(Type type, String id, Object value) {
        int expected = Records.revision(value);
        var next = normalize(type,
                Records.with(value, Map.of("revision", expected + 1)));
        validate(type, next);
        repository.save(type, id, expected, next);
        return next;
    }

    public Application submit(String id, int revision) {
        var current = repository.get(Type.application, id, Application.class);
        requireRevision(current, revision);
        if (!"DRAFT".equals(current.state()))
            throw AppException.conflict("Wniosek jest już złożony.");
        return (Application) replace(Type.application, id,
                Records.with(current, Map.of("state", "SUBMITTED",
                        "submittedAt", Instant.now().toString())));
    }

    public Evaluation finalizeEvaluation(String id, int revision) {
        var current = repository.get(Type.evaluation, id, Evaluation.class);
        requireRevision(current, revision);
        if (!"DRAFT".equals(current.state()))
            throw AppException.conflict("Ocena jest już końcowa.");
        return (Evaluation) replace(Type.evaluation, id,
                Records.with(current, Map.of("state", "FINAL")));
    }

    public static String number(String prefix, String id) {
        return prefix + "/" + LocalDate.now().getYear() + "/"
                + id.substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private Object normalize(Type type, Object value) {
        if (type != Type.application)
            return value;
        var application = (Application) value;
        var total = Rules.total(application);
        var requested = Rules.money(application.requestedAmount());
        Rules.require(requested.compareTo(total) <= 0, "requestedAmount",
                "Dotacja przekracza koszty projektu.");
        var changes = new HashMap<String, Object>();
        changes.put("projectCost", total.setScale(2).toPlainString());
        changes.put("ownContribution",
                total.subtract(requested).setScale(2).toPlainString());
        var competition = competition();
        Rules.require(
                application.competitionId() == null
                        || application.competitionId().isBlank()
                        || application.competitionId().equals(competition.id()),
                "competitionId", "Wniosek musi należeć do CloudMigration.");
        changes.put("competitionId", competition.id());
        if (application.projectNumber() == null
                || application.projectNumber().isBlank())
            changes.put("projectNumber", number("PR", application.id()));
        return Records.with(application, changes);
    }

    public void validate(Type type, Object value) {
        codec.encode(type, value);
        switch (type) {
            case competition -> Rules.competition((Competition) value);
            case application -> {
                var application = (Application) value;
                Rules.application(application, configurationFor(application));
            }
            case evaluation -> {
                var evaluation = (Evaluation) value;
                var application = repository.get(Type.application,
                        evaluation.applicationId(), Application.class);
                Rules.evaluation(evaluation, application,
                        configurationFor(application));
            }
            case decision -> {
                var decision = (Decision) value;
                var application = repository.get(Type.application,
                        decision.applicationId(), Application.class);
                Rules.decision(decision, application,
                        repository.get(Type.evaluation, decision.evaluationId(),
                                Evaluation.class),
                        configurationFor(application));
            }
        }
    }

    private Competition configurationFor(Application application) {
        var current = competition();
        Rules.require(current.id().equals(application.competitionId()),
                "competitionId", "Wniosek musi należeć do CloudMigration.");
        return current;
    }

    private void requireRevision(Object value, int revision) {
        if (Records.revision(value) != revision)
            throw AppException.conflict("Dokument zmieniono. Odśwież dane.");
    }
}
