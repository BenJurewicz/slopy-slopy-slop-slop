package pl.cloudmigration.documents;

import java.util.List;

public final class Models {
    private Models() {}
    public static final String ELIGIBILITY_RULES="Mikro- i małe przedsiębiorstwa; projekt migracji istniejących systemów do chmury. Kwoty netto w PLN.";
    public record Criterion(String code, String name, int maximum) {}
    public record Company(String name, String legalId, String size, String contact, String email) {}
    public record Infrastructure(String name, String provider, String description) {}
    public record Task(String name, String start, String end, String milestone) {}
    public record BudgetLine(String description, String category, String quantity, String unit, String unitCost, String total) {}
    public record Member(String name, String role) {}
    public record Score(String code, int points, String comment) {}
    public record Competition(String id, int revision, String name, String description, String eligibilityRules, String currency,
        String budget, String maxGrant, int threshold, String stage, List<Criterion> criteria) {}
    public record Application(String id, int revision, String competitionId, String state, String submittedAt,
        Company company, String title, String description, String objectives, String benefits,
        List<Infrastructure> currentInfrastructure, List<Infrastructure> targetInfrastructure,
        List<Task> tasks, List<BudgetLine> budget, List<Member> team, String requestedAmount) {}
    public record Evaluation(String id, int revision, String applicationId, String state, String reviewer,
        String date, boolean eligible, String reason, List<Score> scores, int total) {}
    public record Decision(String id, int revision, String applicationId, String evaluationId, String state,
        String date, String outcome, String amount, String currency, String reason) {}
}
