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
    public final DocumentRepository repository;
    private final DocumentCodec codec;
    public Workflow(DocumentRepository repository,DocumentCodec codec){this.repository=repository;this.codec=codec;}
    public Object create(Type type,Object value){
        var changes=new HashMap<String,Object>();changes.put("id",UUID.randomUUID().toString());changes.put("revision",1);
        if(type==Type.competition && ((Competition)value).eligibilityRules()==null)changes.put("eligibilityRules",Models.ELIGIBILITY_RULES);
        if(type!=Type.competition) changes.put("state","DRAFT");
        if(type==Type.application)changes.put("submittedAt",null);
        if(type!=Type.competition)changes.put("number",number(type==Type.application?"WN":type==Type.evaluation?"OC":"DEC",(String)changes.get("id")));
        var document=normalize(type,Records.with(value,changes));validate(type,document);repository.save(type,Records.id(document),0,document);return document;
    }
    public Object update(Type type,String id,Object value){
        if(!id.equals(Records.id(value)))throw AppException.invalid("Identyfikator dokumentu nie odpowiada adresowi.");
        var old=repository.get(type,id);
        if(type!=Type.competition) {
            try {
                if(!"DRAFT".equals(old.getClass().getMethod("state").invoke(old)) || !"DRAFT".equals(value.getClass().getMethod("state").invoke(value))) throw AppException.conflict("Dokument jest zablokowany.");
            } catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
        }
        return replace(type,id,value);
    }
    private Object replace(Type type,String id,Object value){
        int expected=Records.revision(value);var next=normalize(type,Records.with(value,Map.of("revision",expected+1)));validate(type,next);repository.save(type,id,expected,next);return next;
    }
    public Application submit(String id,int revision){
        var current=repository.get(Type.application,id,Application.class);requireRevision(current,revision);
        if(!"DRAFT".equals(current.state()))throw AppException.conflict("Wniosek jest już złożony.");
        return (Application)replace(Type.application,id,Records.with(current,Map.of("state","SUBMITTED","submittedAt",Instant.now().toString())));
    }
    public Evaluation finalizeEvaluation(String id,int revision){
        var current=repository.get(Type.evaluation,id,Evaluation.class);requireRevision(current,revision);
        if(!"DRAFT".equals(current.state()))throw AppException.conflict("Ocena jest już końcowa.");
        return (Evaluation)replace(Type.evaluation,id,Records.with(current,Map.of("state","FINAL")));
    }
    public static String number(String prefix,String id){return prefix+"/"+LocalDate.now().getYear()+"/"+id.substring(0,8).toUpperCase(Locale.ROOT);}
    private Object normalize(Type type,Object value){
        if(type!=Type.application)return value;
        var a=(Application)value;
        var total=Rules.total(a);var requested=Rules.money(a.requestedAmount());
        Rules.require(requested.compareTo(total)<=0,"requestedAmount","Dotacja przekracza koszty projektu.");
        var changes=new HashMap<String,Object>();changes.put("projectCost",total.setScale(2).toPlainString());changes.put("ownContribution",total.subtract(requested).setScale(2).toPlainString());
        if(a.projectNumber()==null||a.projectNumber().isBlank())changes.put("projectNumber",number("PR",a.id()));
        return Records.with(a,changes);
    }
    public void validate(Type type,Object value){
        codec.encode(type,value);
        switch(type){
            case competition -> Rules.competition((Competition)value);
            case application -> {var a=(Application)value;Rules.application(a,repository.get(Type.competition,a.competitionId(),Competition.class));}
            case evaluation -> {var e=(Evaluation)value;var a=repository.get(Type.application,e.applicationId(),Application.class);Rules.evaluation(e,a,repository.get(Type.competition,a.competitionId(),Competition.class));}
            case decision -> {var d=(Decision)value;var a=repository.get(Type.application,d.applicationId(),Application.class);Rules.decision(d,a,repository.get(Type.evaluation,d.evaluationId(),Evaluation.class),repository.get(Type.competition,a.competitionId(),Competition.class));}
        }
    }
    private void requireRevision(Object value,int revision){if(Records.revision(value)!=revision)throw AppException.conflict("Dokument zmieniono. Odśwież dane.");}
}
