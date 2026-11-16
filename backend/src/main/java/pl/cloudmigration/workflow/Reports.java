package pl.cloudmigration.workflow;

import java.util.*;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import pl.cloudmigration.storage.BaseXClient;
import pl.cloudmigration.api.AppException;

@Service
public class Reports {
    public record Query(String id,String name,String description,String source) {}
    public record Result(int total,List<Map<String,Object>> rows) {}
    public record Parameters(String threshold,int offset,int limit) {}
    private final BaseXClient client;private final ObjectMapper mapper;private final Workflow workflow;
    private record Definition(String name,String description) {}
    private static final Map<String,Definition> QUERIES=Map.of(
        "applications",new Definition("Wszystkie wnioski","Lista wniosków z nazwą firmy, statusem i wnioskowaną kwotą."),
        "over-budget",new Definition("Wnioski powyżej podanej kwoty","Wnioski o dotację większą niż podana kwota. Wnioski o dokładnie tę kwotę są pomijane."),
        "missing-evaluations",new Definition("Wnioski bez końcowej oceny","Złożone wnioski, które nie mają jeszcze zatwierdzonej oceny."),
        "ranking",new Definition("Ranking projektów","Projekty z zatwierdzoną oceną, od najwyższej punktacji. Przy remisie decyduje czas złożenia, potem ID."),
        "qualified",new Definition("Projekty zakwalifikowane","Projekty spełniające warunki i próg punktowy. Nie oznacza to przyznania dotacji."),
        "summary",new Definition("Podsumowanie konkursu","Liczba wniosków i ocen końcowych, średnia punktów oraz przyznane i pozostałe środki. Szkice decyzji nie obciążają budżetu."),
        "decisions",new Definition("Wydane decyzje","Wydane decyzje wraz z projektem, firmą, wynikiem i przyznaną kwotą. Bez szkiców."),
        "providers",new Definition("Dostawcy chmury","Liczba projektów korzystających z każdego dostawcy docelowej chmury. Projekt może korzystać z kilku dostawców."),
        "allocation",new Definition("Propozycja finansowania","Proponowane pełne dotacje i odmowy według rankingu i pozostałego budżetu. Raport nie tworzy ani nie wydaje decyzji."));
    public Reports(BaseXClient client,ObjectMapper mapper,Workflow workflow){this.client=client;this.mapper=mapper;this.workflow=workflow;}
    public List<Query> catalogue(){return QUERIES.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e->new Query(e.getKey(),e.getValue().name(),e.getValue().description(),client.resource(e.getKey().equals("allocation")?"allocation":"report"))).toList();}
    public Result run(String id,Parameters p){
        if(!QUERIES.containsKey(id))throw new AppException(404,"NOT_FOUND","Zapytanie nie istnieje.");
        Rules.require(p.offset()>=0&&p.limit()>0&&p.limit()<=500,"Nieprawidłowe stronicowanie.");
        var result=client.execute(client.resource(id.equals("allocation")?"allocation":"report"),Map.of("report",id,"competition",workflow.competition().id(),"threshold",Rules.money(p.threshold()==null?"0":p.threshold()).toPlainString(),"offset",Integer.toString(p.offset()),"limit",Integer.toString(p.limit())));
        return mapper.readValue(result,Result.class);
    }
}
