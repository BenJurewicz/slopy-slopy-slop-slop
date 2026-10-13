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
    public record Parameters(String competitionId,String threshold,int offset,int limit) {}
    private final BaseXClient client;private final ObjectMapper mapper;
    private static final Map<String,String> QUERIES=Map.of(
        "applications","Wnioski w konkursie","over-budget","Wnioski powyżej kwoty","missing-evaluations","Brak końcowej oceny",
        "ranking","Ranking projektów","qualified","Projekty zakwalifikowane","summary","Średnia ocen i finansowanie",
        "decisions","Wydane decyzje","providers","Dostawcy chmury","allocation","Propozycja finansowania");
    public Reports(BaseXClient client,ObjectMapper mapper){this.client=client;this.mapper=mapper;}
    public List<Query> catalogue(){return QUERIES.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e->new Query(e.getKey(),e.getValue(),"Parametry: competitionId, threshold, offset, limit",client.resource(e.getKey().equals("allocation")?"allocation":"report"))).toList();}
    public Result run(String id,Parameters p){
        if(!QUERIES.containsKey(id))throw new AppException(404,"NOT_FOUND","Zapytanie nie istnieje.");
        try{UUID.fromString(p.competitionId());}catch(Exception e){throw AppException.invalid("Wybierz konkurs.");}
        Rules.require(p.offset()>=0&&p.limit()>0&&p.limit()<=500,"Nieprawidłowe stronicowanie.");
        var result=client.execute(client.resource(id.equals("allocation")?"allocation":"report"),Map.of("report",id,"competition",p.competitionId(),"threshold",Rules.money(p.threshold()==null?"0":p.threshold()).toPlainString(),"offset",Integer.toString(p.offset()),"limit",Integer.toString(p.limit())));
        return mapper.readValue(result,Result.class);
    }
}
