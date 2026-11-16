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
        var changes = new HashMap<String, Object>();
        changes.put("id",
                type == Type.competition
                        ? DEFAULT_COMPETITION_ID
                        : UUID.randomUUID().toString());
        changes.put("revision", 1);
        if (type == Type.competition)
            changes.put("name", "CloudMigration");
        if (type == Type.competition
                && ((Competition) value).eligibilityRules() == null)
            changes.put("eligibilityRules", Models.ELIGIBILITY_RULES);
        if (type != Type.competition)
            changes.put("state", "DRAFT");
        if (type == Type.application)
            changes.put("submittedAt", null);
        if (type != Type.competition)
            changes.put("number",
                    number(type == Type.application
                            ? "WN"
                            : type == Type.evaluation ? "OC" : "DEC",
                            (String) changes.get("id")));
        var document = normalize(type, Records.with(value, changes));
        validate(type, document);
        repository.save(type, Records.id(document), 0, document);
        return document;
    }
    public Object update(Type type, String id, Object value) {
        if (!id.equals(Records.id(value)))
            throw AppException
                    .invalid("Identyfikator dokumentu nie odpowiada adresowi.");
        var old = repository.get(type, id);
        if (type == Type.competition) {
            Rules.require(id.equals(competition().id()),
                    "Nieprawidłowa konfiguracja CloudMigration.");
            Rules.require(
                    Objects.equals(((Competition) value).name(),
                            ((Competition) old).name()),
                    "name", "Nazwa konkursu nie podlega zmianie.");
        }
        if (type != Type.competition) {
            try {
                if (!"DRAFT"
                        .equals(old.getClass().getMethod("state").invoke(old))
                        || !"DRAFT".equals(value.getClass().getMethod("state")
                                .invoke(value)))
                    throw AppException.conflict("Dokument jest zablokowany.");
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
        return replace(type, id, value);
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
        var a = (Application) value;
        var total = Rules.total(a);
        var requested = Rules.money(a.requestedAmount());
        Rules.require(requested.compareTo(total) <= 0, "requestedAmount",
                "Dotacja przekracza koszty projektu.");
        var changes = new HashMap<String, Object>();
        changes.put("projectCost", total.setScale(2).toPlainString());
        changes.put("ownContribution",
                total.subtract(requested).setScale(2).toPlainString());
        var competition = competition();
        Rules.require(
                a.competitionId() == null || a.competitionId().isBlank()
                        || a.competitionId().equals(competition.id()),
                "competitionId", "Wniosek musi należeć do CloudMigration.");
        changes.put("competitionId", competition.id());
        if (a.projectNumber() == null || a.projectNumber().isBlank())
            changes.put("projectNumber", number("PR", a.id()));
        return Records.with(a, changes);
    }
    public void validate(Type type, Object value) {
        codec.encode(type, value);
        switch (type) {
            case competition -> Rules.competition((Competition) value);
            case application -> {
                var a = (Application) value;
                Rules.application(a, configurationFor(a));
            }
            case evaluation -> {
                var e = (Evaluation) value;
                var a = repository.get(Type.application, e.applicationId(),
                        Application.class);
                Rules.evaluation(e, a, configurationFor(a));
            }
            case decision -> {
                var d = (Decision) value;
                var a = repository.get(Type.application, d.applicationId(),
                        Application.class);
                Rules.decision(d, a, repository.get(Type.evaluation,
                        d.evaluationId(), Evaluation.class),
                        configurationFor(a));
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
