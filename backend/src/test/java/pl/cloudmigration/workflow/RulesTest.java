package pl.cloudmigration.workflow;

import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import pl.cloudmigration.demo.Examples;
import pl.cloudmigration.documents.*;
import pl.cloudmigration.documents.Models.*;
import pl.cloudmigration.api.AppException;
import static org.junit.jupiter.api.Assertions.*;

class RulesTest {
    @Test
    void exampleBudgetIsValid() {
        assertEquals(new BigDecimal("50000.00"),
                Rules.total(Examples.application()));
        Rules.application(Examples.application(), Examples.competition());
    }

    @Test
    void rejectsPrecisionNegativeValuesAndWrongTotals() {
        assertThrows(AppException.class, () -> Rules.money("1.001"));
        assertThrows(AppException.class, () -> Rules.money("-1"));
        Application bad = Records.with(Examples.application(),
                Map.of("budget", List.of(new BudgetLine("Test", "Usługi", "1.5",
                        "h", "10.01", "15.00"))));
        assertThrows(AppException.class, () -> Rules.total(bad));
    }

    @Test
    void usesDecimalRounding() {
        Application a = Records.with(Examples.application(),
                Map.of("budget", List.of(new BudgetLine("Test", "Usługi", "1.5",
                        "h", "10.01", "15.02"))));
        assertEquals(new BigDecimal("15.02"), Rules.total(a));
    }

    @Test
    void rejectsScheduleAndOverLimit() {
        Application a = Records.with(Examples.application(), Map.of("tasks",
                List.of(new Task("Test", "2026-12-01", "2026-11-01", "Test"))));
        assertThrows(AppException.class,
                () -> Rules.application(a, Examples.competition()));
    }

    @Test
    void requiresEveryCriterionAndCorrectTotals() {
        Application a = Records.with(Examples.application(), Map.of("state",
                "SUBMITTED", "submittedAt", "2026-10-12T00:00:00Z"));
        Rules.evaluation(Examples.evaluation(), a, Examples.competition());
        Evaluation bad = Records.with(Examples.evaluation(),
                Map.of("total", 100));
        assertThrows(AppException.class,
                () -> Rules.evaluation(bad, a, Examples.competition()));
    }

    @Test
    void rejectsNegativeOverMaximumAndDuplicateScores() {
        var a = Records.with(Examples.application(), Map.of("state",
                "SUBMITTED", "submittedAt", "2026-10-12T00:00:00Z"));
        for (int invalid : new int[]{-1, 26}) {
            var scores = new ArrayList<>(Examples.evaluation().scores());
            scores.set(0, new Score("technical", invalid, "Test"));
            var e = Records.with(Examples.evaluation(),
                    Map.of("scores", scores));
            assertThrows(AppException.class,
                    () -> Rules.evaluation(e, a, Examples.competition()));
        }
        var duplicate = Records.with(Examples.evaluation(),
                Map.of("scores", List.of(new Score("technical", 20, "Test"),
                        new Score("technical", 20, "Test"))));
        assertThrows(AppException.class,
                () -> Rules.evaluation(duplicate, a, Examples.competition()));
        var ineligible = Records.with(a,
                Map.of("company",
                        new Company("Firma", "123", "OTHER", "Anna",
                                "anna@example.org", "ul. Testowa 1, Gdańsk",
                                "123456785", "Anna Testowa")));
        assertThrows(AppException.class,
                () -> Rules.evaluation(Examples.evaluation(), ineligible,
                        Examples.competition()));
    }

    @Test
    void rejectsInvalidProjectPeriodAndInconsistentContribution() {
        for (var changes : List.of(Map.of("implementationEnd", "2026-10-01"),
                Map.of("implementationStart", "2026-12-01"),
                Map.of("projectCost", "1.00"),
                Map.of("ownContribution", "1.00"))) {
            var app = Records.with(Examples.application(),
                    new HashMap<String, Object>(changes));
            assertThrows(AppException.class,
                    () -> Rules.application(app, Examples.competition()));
        }
    }

    @Test
    void rejectsUnboundedExponentAndOversizedNumericInputBeforeCalculations() {
        for (var input : List.of("1E999999999", "1e999999999",
                "1".repeat(1000))) {
            assertThrows(AppException.class, () -> Rules.money(input));
            assertThrows(AppException.class, () -> Rules.quantity(input));
        }
    }

}
