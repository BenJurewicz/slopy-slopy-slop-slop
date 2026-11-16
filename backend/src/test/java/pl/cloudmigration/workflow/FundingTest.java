package pl.cloudmigration.workflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pl.cloudmigration.demo.Examples;
import pl.cloudmigration.documents.DocumentCodec.Type;
import pl.cloudmigration.documents.Models.*;
import pl.cloudmigration.documents.Records;
import pl.cloudmigration.storage.BaseXClient;
import pl.cloudmigration.storage.DocumentRepository;

class FundingTest {
    private final DocumentRepository repository = mock(
            DocumentRepository.class);
    private final BaseXClient client = mock(BaseXClient.class);
    private final Workflow workflow = mock(Workflow.class);
    private final Funding funding = new Funding(repository, client, workflow);

    @Test
    void previewKeepsReasonPrecedenceAndDoesNotSpendRejectedAmounts() {
        when(workflow.competition()).thenReturn(Records
                .with(Examples.competition(), Map.of("budget", "50000.00")));
        when(repository.list(Type.application))
                .thenReturn(List.of(application("ineligible", "1000.00"),
                        application("winner", "40000.00"),
                        application("too-expensive", "20000.00"),
                        application("affordable", "10000.00"),
                        application("below-threshold", "1000.00")));
        when(repository.list(Type.evaluation))
                .thenReturn(List.of(evaluation("below-threshold", 59, true),
                        evaluation("affordable", 70, true),
                        evaluation("too-expensive", 80, true),
                        evaluation("winner", 90, true),
                        evaluation("ineligible", 100, false)));
        when(repository.list(Type.decision)).thenReturn(List.of());

        var preview = funding.preview();

        assertEquals("50000.00", preview.available());
        assertEquals(
                List.of("ineligible", "winner", "too-expensive", "affordable",
                        "below-threshold"),
                preview.rows().stream().map(Funding.Allocation::applicationId)
                        .toList());
        assertEquals(List.of("REJECT", "AWARD", "REJECT", "AWARD", "REJECT"),
                preview.rows().stream().map(Funding.Allocation::outcome)
                        .toList());
        assertEquals(List.of(true, true, false, true, false), preview.rows()
                .stream().map(Funding.Allocation::budgetAllows).toList());
        assertEquals(
                List.of("Wniosek nie spełnia warunków kwalifikacji.",
                        "Projekt zakwalifikowany w kolejności rankingu.",
                        "Brak wystarczających środków.",
                        "Projekt zakwalifikowany w kolejności rankingu.",
                        "Wynik poniżej progu kwalifikacji."),
                preview.rows().stream().map(Funding.Allocation::reason)
                        .toList());
    }

    @Test
    void preparingKeepsAnExistingDraftInsteadOfReplacingItsEditedOutcome() {
        var application = application(Examples.APPLICATION, "50000.00");
        var draft = Records.with(Examples.decision(),
                Map.of("outcome", "REJECT", "amount", "0.00"));
        when(workflow.competition()).thenReturn(Examples.competition());
        when(repository.list(Type.application))
                .thenReturn(List.of(application));
        when(repository.list(Type.evaluation))
                .thenReturn(List.of(evaluation(application.id(), 80, true)));
        when(repository.list(Type.decision)).thenReturn(List.of(draft));
        when(repository.get(Type.decision, draft.id(), Decision.class))
                .thenReturn(draft);

        assertEquals(List.of(draft), funding.prepare());
        verify(workflow, never()).create(any(), any());
    }

    @Test
    void issuanceValidatesSavedDocumentsAndSubmitsTheRequestedRevisions() {
        var draft = Examples.decision();
        var issued = Records.with(draft, Map.of("state", "ISSUED"));
        when(repository.get(Type.decision, draft.id(), Decision.class))
                .thenReturn(draft, issued);
        when(client.resource("issue")).thenReturn("issue-query");
        var entries = List.of(new Funding.IssueEntry(draft.id(), 7));

        assertEquals(List.of(issued), funding.issue(entries));

        var order = inOrder(workflow, client);
        order.verify(workflow).validate(Type.decision, draft);
        order.verify(client).resource("issue");
        order.verify(client).execute("issue-query",
                Map.of("batch", "<batch><entry id=\"" + draft.id()
                        + "\" revision=\"7\"/></batch>"));
    }

    private Application application(String id, String amount) {
        return Records.with(Examples.application(),
                Map.of("id", id, "state", "SUBMITTED", "submittedAt",
                        "2026-01-01T00:00:00Z", "requestedAmount", amount));
    }

    private Evaluation evaluation(String applicationId, int points,
            boolean eligible) {
        return Records.with(Examples.evaluation(),
                Map.of("applicationId", applicationId, "state", "FINAL",
                        "total", points, "eligible", eligible));
    }
}
