package pl.cloudmigration.demo;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import pl.cloudmigration.documents.*;
import pl.cloudmigration.documents.Models.*;
import pl.cloudmigration.documents.DocumentCodec.Type;
import pl.cloudmigration.storage.BaseXClient;
import pl.cloudmigration.workflow.*;

/**
 * Prepare and validate the entire demo before one atomic database replacement.
 */
@Service
public class DemoDataset {
    public record DemoResult(String competitionId, int applications) {
    }
    private final BaseXClient client;
    private final DocumentCodec codec;
    public DemoDataset(BaseXClient client, DocumentCodec codec) {
        this.client = client;
        this.codec = codec;
    }
    private record Example(String amount, Integer score, boolean eligible,
            boolean submitted, boolean finalized) {
        Example(String amount, Integer score, boolean eligible,
                boolean submitted) {
            this(amount, score, eligible, submitted, true);
        }
    }
    private static final List<Example> EXAMPLES = List.of(
            new Example("20000.00", 98, true, true),
            new Example("25000.00", 96, true, true),
            new Example("15000.00", 96, true, true),
            new Example("30000.00", 92, true, true),
            new Example("20000.00", 90, true, true),
            new Example("25000.00", 88, true, true),
            new Example("10000.00", 86, true, true),
            new Example("15000.00", 84, true, true),
            new Example("20000.00", 82, true, true),
            new Example("15000.00", 80, true, true),
            new Example("60000.00", 78, true, true),
            new Example("10000.00", 76, true, true),
            new Example("15000.00", 74, true, true),
            new Example("50000.00", 72, true, true),
            new Example("25000.00", 70, true, true),
            new Example("10000.00", 55, true, true),
            new Example("10000.00", 80, false, true),
            new Example("20000.00", 80, true, true, false),
            new Example("10000.00", null, true, true),
            new Example("5000.00", null, true, false));
    public DemoResult replace() {
        var competition = Records.with(Examples.competition(), Map.of("id",
                UUID.randomUUID().toString(), "name", "CloudMigration"));
        Rules.competition(competition);
        var batch = new StringBuilder("<replacement>");
        append(batch, Type.competition, competition);
        var submittedAt = Instant.now();
        for (int index = 0; index < EXAMPLES.size(); index++) {
            var example = EXAMPLES.get(index);
            var application = exampleApplication(competition, example, index,
                    submittedAt);
            Rules.application(application, competition);
            append(batch, Type.application, application);
            if (example.score() == null)
                continue;
            var evaluation = exampleEvaluation(competition, application,
                    example);
            Rules.evaluation(evaluation, application, competition);
            append(batch, Type.evaluation, evaluation);
        }
        batch.append("</replacement>");
        client.execute(client.resource("replace-demo"),
                Map.of("batch", batch.toString()));
        return new DemoResult(competition.id(), EXAMPLES.size());
    }

    private Application exampleApplication(Competition competition,
            Example example, int index, Instant submittedAt) {
        String id = UUID.randomUUID().toString();
        var changes = new HashMap<String, Object>();
        changes.put("id", id);
        changes.put("revision", example.submitted() ? 2 : 1);
        changes.put("competitionId", competition.id());
        changes.put("state", example.submitted() ? "SUBMITTED" : "DRAFT");
        changes.put("submittedAt",
                example.submitted()
                        ? submittedAt.plusMillis(index).toString()
                        : null);
        changes.put("title", String.format("Projekt testowy %02d", index + 1));
        changes.put("requestedAmount", example.amount());
        changes.put("number", Workflow.number("WN", id));
        changes.put("projectNumber", Workflow.number("PR", id));
        changes.put("projectCost", example.amount());
        changes.put("ownContribution", "0.00");
        changes.put("company", new Company("Firma " + (index + 1),
                String.format("%010d", 1000000000L + index),
                example.eligible() ? "MICRO" : "OTHER", "Anna Kowalska",
                "firma" + index + "@example.org",
                "ul. Testowa " + (index + 1) + ", 80-001 Gdańsk",
                String.format("%09d", 100000000 + index), "Anna Kowalska"));
        changes.put("budget", List.of(new BudgetLine("Migracja i konfiguracja",
                "Usługi", "1", "projekt", example.amount(), example.amount())));
        return Records.with(Examples.application(), changes);
    }

    private Evaluation exampleEvaluation(Competition competition,
            Application application, Example example) {
        var points = new ArrayList<Score>();
        int remaining = example.score();
        for (var criterion : competition.criteria()) {
            int score = Math.min(criterion.maximum(), remaining);
            remaining -= score;
            points.add(
                    new Score(criterion.code(), score, "Ocena demonstracyjna"));
        }
        String evaluationId = UUID.randomUUID().toString();
        return new Evaluation(evaluationId, example.finalized() ? 2 : 1,
                application.id(), example.finalized() ? "FINAL" : "DRAFT",
                "Jan Nowak", LocalDate.now().toString(), example.eligible(),
                example.eligible()
                        ? "Warunki spełnione."
                        : "Firma nie jest mikro ani małym przedsiębiorstwem.",
                points, example.score(), Workflow.number("OC", evaluationId));
    }

    public void clear() {
        client.execute(client.resource("reset-data"), Map.of());
    }

    private void append(StringBuilder batch, Type type, Object value) {
        String xml = codec.encode(type, value);
        int declaration = xml.indexOf("?>");
        if (xml.startsWith("<?xml") && declaration >= 0)
            xml = xml.substring(declaration + 2);
        batch.append("<document path=\"").append(type.path(Records.id(value)))
                .append("\">").append(xml).append("</document>");
    }
}
