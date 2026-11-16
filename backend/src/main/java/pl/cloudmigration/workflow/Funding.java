package pl.cloudmigration.workflow;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;
import pl.cloudmigration.documents.*;
import pl.cloudmigration.documents.Models.*;
import pl.cloudmigration.documents.DocumentCodec.Type;
import pl.cloudmigration.storage.*;
import pl.cloudmigration.api.AppException;

@Service
public class Funding {
    public record Allocation(String applicationId,String evaluationId,String title,int score,boolean eligible,boolean qualified,
        String requestedAmount,String outcome,String reason,String existingDecisionId,boolean budgetAllows) {}
    public record Preview(String available,List<Allocation> rows) {}
    public record IssueEntry(String id,int revision) {}
    private final DocumentRepository repo;
    private final BaseXClient client;
    private final Workflow workflow;
    public Funding(DocumentRepository repo,BaseXClient client,Workflow workflow){this.repo=repo;this.client=client;this.workflow=workflow;}
    public Preview preview() {
        var c=workflow.competition();var competitionId=c.id();
        var applications=repo.list(Type.application).stream().map(Application.class::cast).filter(a->a.competitionId().equals(competitionId)).toList();
        var appById=new HashMap<String,Application>();applications.forEach(a->appById.put(a.id(),a));
        var evaluations=repo.list(Type.evaluation).stream().map(Evaluation.class::cast).filter(e->"FINAL".equals(e.state())&&appById.containsKey(e.applicationId())).sorted(
            Comparator.comparingInt(Evaluation::total).reversed().thenComparing(e->java.time.Instant.parse(appById.get(e.applicationId()).submittedAt())).thenComparing(Evaluation::applicationId)).toList();
        var decisions=repo.list(Type.decision).stream().map(Decision.class::cast).filter(d->appById.containsKey(d.applicationId())).toList();
        var remaining=Rules.money(c.budget()).subtract(decisions.stream().filter(d->"ISSUED".equals(d.state())&&"AWARD".equals(d.outcome())).map(d->Rules.money(d.amount())).reduce(BigDecimal.ZERO,BigDecimal::add));
        var initial=remaining;var rows=new ArrayList<Allocation>();
        for(var e:evaluations){
            var a=appById.get(e.applicationId());var existing=decisions.stream().filter(d->d.applicationId().equals(a.id())).findFirst().orElse(null);
            if(existing!=null&&"ISSUED".equals(existing.state()))continue;
            boolean qualified=e.eligible()&&e.total()>=c.threshold();
            boolean budgetAllows=Rules.money(a.requestedAmount()).compareTo(remaining)<=0;
            boolean award=qualified&&budgetAllows;
            String reason=!e.eligible()?"Wniosek nie spełnia warunków kwalifikacji.":!qualified?"Wynik poniżej progu kwalifikacji.":!award?"Brak wystarczających środków.":"Projekt zakwalifikowany w kolejności rankingu.";
            if(award)remaining=remaining.subtract(Rules.money(a.requestedAmount()));
            rows.add(new Allocation(a.id(),e.id(),a.title(),e.total(),e.eligible(),qualified,a.requestedAmount(),award?"AWARD":"REJECT",reason,existing==null?null:existing.id(),budgetAllows));
        }
        return new Preview(initial.toPlainString(),List.copyOf(rows));
    }
    public List<Decision> prepare(){
        var rows=preview().rows();var results=new ArrayList<Decision>();
        for(var row:rows) {
            if(row.existingDecisionId()!=null){results.add(repo.get(Type.decision,row.existingDecisionId(),Decision.class));continue;}
            results.add((Decision)workflow.create(Type.decision,new Decision(null,0,row.applicationId(),row.evaluationId(),"DRAFT",java.time.LocalDate.now().toString(),row.outcome(),"AWARD".equals(row.outcome())?row.requestedAmount():"0.00", "PLN",row.reason(),null,row.budgetAllows())));
        }
        return results;
    }
    public List<Decision> issue(List<IssueEntry> entries){
        if(entries==null||entries.isEmpty())throw AppException.invalid("Wybierz decyzje do wydania.");
        var xml=new StringBuilder("<batch>");
        for(var entry:entries){var d=repo.get(Type.decision,entry.id(),Decision.class);workflow.validate(Type.decision,d);xml.append("<entry id=\"").append(d.id()).append("\" revision=\"").append(entry.revision()).append("\"/>");}
        xml.append("</batch>");client.execute(client.resource("issue"),Map.of("batch",xml.toString()));
        return entries.stream().map(e->repo.get(Type.decision,e.id(),Decision.class)).toList();
    }
}
