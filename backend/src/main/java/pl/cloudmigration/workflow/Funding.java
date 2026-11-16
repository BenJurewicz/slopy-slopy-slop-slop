package pl.cloudmigration.workflow;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import pl.cloudmigration.api.AppException;
import pl.cloudmigration.documents.DocumentCodec.Type;
import pl.cloudmigration.documents.Models.Application;
import pl.cloudmigration.documents.Models.Competition;
import pl.cloudmigration.documents.Models.Decision;
import pl.cloudmigration.documents.Models.Evaluation;
import pl.cloudmigration.storage.BaseXClient;
import pl.cloudmigration.storage.DocumentRepository;

@Service
public class Funding {
    public record Allocation(String applicationId, String evaluationId,
            String title, int score, boolean eligible, boolean qualified,
            String requestedAmount, String outcome, String reason,
            String existingDecisionId, boolean budgetAllows) {
    }

    public record Preview(String available, List<Allocation> rows) {
    }

    public record IssueEntry(String id, int revision) {
    }

    private final DocumentRepository repository;
    private final BaseXClient client;
    private final Workflow workflow;

    public Funding(DocumentRepository repository, BaseXClient client,
            Workflow workflow) {
        this.repository = repository;
        this.client = client;
        this.workflow = workflow;
    }

    public Preview preview() {
        var competition = workflow.competition();
        var applications = applicationsById(competition.id());
        var evaluations = rankedEvaluations(applications);
        var decisions = competitionDecisions(applications);
        var available = availableBudget(competition, decisions);
        var remaining = available;
        var rows = new ArrayList<Allocation>();

        for (var evaluation : evaluations) {
            var application = applications.get(evaluation.applicationId());
            var existing = decisions.stream().filter(decision -> decision
                    .applicationId().equals(application.id())).findFirst()
                    .orElse(null);
            if (existing != null && "ISSUED".equals(existing.state()))
                continue;

            boolean qualified = evaluation.eligible()
                    && evaluation.total() >= competition.threshold();
            boolean budgetAllows = Rules.money(application.requestedAmount())
                    .compareTo(remaining) <= 0;
            boolean award = qualified && budgetAllows;
            String reason = allocationReason(evaluation.eligible(), qualified,
                    award);

            if (award)
                remaining = remaining
                        .subtract(Rules.money(application.requestedAmount()));

            rows.add(new Allocation(application.id(), evaluation.id(),
                    application.title(), evaluation.total(),
                    evaluation.eligible(), qualified,
                    application.requestedAmount(), award ? "AWARD" : "REJECT",
                    reason, existing == null ? null : existing.id(),
                    budgetAllows));
        }

        return new Preview(available.toPlainString(), List.copyOf(rows));
    }

    private Map<String, Application> applicationsById(String competitionId) {
        var applications = repository.list(Type.application).stream()
                .map(Application.class::cast).filter(application -> application
                        .competitionId().equals(competitionId))
                .toList();
        var byId = new HashMap<String, Application>();
        applications.forEach(
                application -> byId.put(application.id(), application));
        return byId;
    }

    private List<Evaluation> rankedEvaluations(
            Map<String, Application> applications) {
        var ranking = Comparator.comparingInt(Evaluation::total).reversed()
                .thenComparing(evaluation -> Instant.parse(applications
                        .get(evaluation.applicationId()).submittedAt()))
                .thenComparing(Evaluation::applicationId);

        return repository.list(Type.evaluation).stream()
                .map(Evaluation.class::cast)
                .filter(evaluation -> "FINAL".equals(evaluation.state())
                        && applications.containsKey(evaluation.applicationId()))
                .sorted(ranking).toList();
    }

    private List<Decision> competitionDecisions(
            Map<String, Application> applications) {
        return repository.list(Type.decision).stream().map(Decision.class::cast)
                .filter(decision -> applications
                        .containsKey(decision.applicationId()))
                .toList();
    }

    private BigDecimal availableBudget(Competition competition,
            List<Decision> decisions) {
        var budget = Rules.money(competition.budget());
        var awarded = decisions.stream()
                .filter(decision -> "ISSUED".equals(decision.state())
                        && "AWARD".equals(decision.outcome()))
                .map(decision -> Rules.money(decision.amount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return budget.subtract(awarded);
    }

    private String allocationReason(boolean eligible, boolean qualified,
            boolean award) {
        if (!eligible)
            return "Wniosek nie spełnia warunków kwalifikacji.";
        if (!qualified)
            return "Wynik poniżej progu kwalifikacji.";
        if (!award)
            return "Brak wystarczających środków.";
        return "Projekt zakwalifikowany w kolejności rankingu.";
    }

    public List<Decision> prepare() {
        var results = new ArrayList<Decision>();
        for (var allocation : preview().rows())
            results.add(prepareDecision(allocation));
        return results;
    }

    private Decision prepareDecision(Allocation allocation) {
        if (allocation.existingDecisionId() != null)
            return repository.get(Type.decision,
                    allocation.existingDecisionId(), Decision.class);

        var draft = new Decision(null, 0, allocation.applicationId(),
                allocation.evaluationId(), "DRAFT", LocalDate.now().toString(),
                allocation.outcome(),
                "AWARD".equals(allocation.outcome())
                        ? allocation.requestedAmount()
                        : "0.00",
                "PLN", allocation.reason(), null, allocation.budgetAllows());
        return (Decision) workflow.create(Type.decision, draft);
    }

    public List<Decision> issue(List<IssueEntry> entries) {
        if (entries == null || entries.isEmpty())
            throw AppException.invalid("Wybierz decyzje do wydania.");

        var batch = issuanceBatch(entries);
        client.execute(client.resource("issue"), Map.of("batch", batch));
        return entries.stream().map(entry -> repository.get(Type.decision,
                entry.id(), Decision.class)).toList();
    }

    private String issuanceBatch(List<IssueEntry> entries) {
        var xml = new StringBuilder("<batch>");
        for (var entry : entries) {
            var decision = repository.get(Type.decision, entry.id(),
                    Decision.class);
            workflow.validate(Type.decision, decision);
            xml.append("<entry id=\"").append(decision.id())
                    .append("\" revision=\"").append(entry.revision())
                    .append("\"/>");
        }
        return xml.append("</batch>").toString();
    }
}
