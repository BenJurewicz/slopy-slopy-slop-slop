package pl.cloudmigration.docx;

import java.math.*;
import java.text.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import org.springframework.stereotype.Component;
import org.w3c.dom.*;
import pl.cloudmigration.api.AppException;
import pl.cloudmigration.documents.*;
import pl.cloudmigration.documents.Models.*;
import pl.cloudmigration.documents.DocumentCodec.Type;
import pl.cloudmigration.demo.Examples;
import pl.cloudmigration.workflow.Rules;
import static pl.cloudmigration.docx.WordFile.*;

@Component
public class DocxDocuments {
    public record Context(Competition competition,Application application,Evaluation evaluation) {}
    private final DocumentCodec codec;
    public DocxDocuments(DocumentCodec codec){this.codec=codec;}

    public byte[] template(Competition competition){return renderApplication(null,competition);}
    public byte[] example(Competition competition){return renderApplication(Examples.application(),competition);}
    public byte[] export(Type type,Object value,Context context){
        try{return switch(type){
            case application->renderApplication((Application)value,context.competition());
            case evaluation->evaluation((Evaluation)value,context);
            case decision->decision((Decision)value,context);
            case competition->competition((Competition)value);
        };}catch(AppException e){throw e;}catch(Exception e){throw new IllegalStateException("Nie można utworzyć dokumentu Word.",e);}
    }
    private void header(WordFile w,String title,Competition competition){
        w.heading("Title",title);w.heading("Subtitle","CloudMigration — Chmura dla Lokalnych Biznesów");
        w.p(competition==null?"Konkurs wybierzesz w aplikacji podczas importu.":"Konkurs: "+competition.name());
    }
    private static List<Cell> row(Cell... cells){return List.of(cells);}
    private static Cell a(String key,String value,String hint){return answer(key,value,"«"+hint+"»");}
    private static String money(String value){if(value==null)return null;var f=NumberFormat.getNumberInstance(Locale.forLanguageTag("pl-PL"));f.setMinimumFractionDigits(2);f.setMaximumFractionDigits(2);return f.format(new BigDecimal(value));}
    private static String qty(String value){return value==null?null:new BigDecimal(value).stripTrailingZeros().toPlainString().replace('.',',');}
    private static String size(String size){return switch(size){case "MICRO"->"Mikroprzedsiębiorstwo";case "SMALL"->"Małe przedsiębiorstwo";default->"Inna wielkość przedsiębiorstwa";};}
    private static String state(String state){return switch(state){case "DRAFT"->"Szkic";case "SUBMITTED"->"Złożony";case "FINAL"->"Ocena końcowa";case "ISSUED"->"Wydana";case "OPEN"->"Nabór otwarty";case "EVALUATION"->"Ocena";case "CLOSED"->"Zamknięty";default->state;};}

    private byte[] renderApplication(Application app,Competition competition){
        try{
            var w=new WordFile(FORM);header(w,"Wniosek o dofinansowanie migracji do chmury",competition);
            if(app==null){
                w.p("Wypełnij pola oznaczone jasnym tekstem. Kliknij podpowiedź i zastąp ją własną odpowiedzią. Nie zmieniaj nagłówków sekcji ani tabel.");
                w.p("Aby dodać zadanie, wydatek lub członka zespołu, skopiuj cały wiersz odpowiedniej tabeli. Kwoty netto w PLN zapisuj np. 1 000,00; daty: RRRR-MM-DD lub DD.MM.RRRR.");
                w.p("To pusty formularz, nie przykładowy wniosek. Aplikacja nada identyfikatory i przypisze wniosek do konkursu wybranego podczas importu.");
            }
            var company=app==null?null:app.company();w.heading("Heading1","1. Dane przedsiębiorstwa");
            w.table(null,List.of("Pole","Odpowiedź"),List.of(
                row(text("Nazwa firmy"),a("company.name",company==null?null:company.name(),"Wpisz pełną nazwę firmy")),
                row(text("NIP / identyfikator"),a("company.legalId",company==null?null:company.legalId(),"Wpisz NIP lub identyfikator firmy")),
                row(text("Wielkość przedsiębiorstwa"),a("company.size",company==null?null:size(company.size()),"Wpisz: Mikroprzedsiębiorstwo lub Małe przedsiębiorstwo")),
                row(text("Osoba kontaktowa"),a("company.contact",company==null?null:company.contact(),"Wpisz imię i nazwisko")),
                row(text("Adres e-mail"),a("company.email",company==null?null:company.email(),"Wpisz adres e-mail"))));
            w.heading("Heading1","2. Projekt");
            w.field("title","Tytuł projektu",app==null?null:app.title(),"«Wpisz krótki tytuł projektu»");
            w.field("description","Co firma chce zmienić i na czym będzie polegać migracja?",app==null?null:app.description(),"«Opisz obecne problemy oraz planowaną migrację do chmury»");
            w.field("objectives","Cele projektu",app==null?null:app.objectives(),"«Wpisz konkretne cele, które firma chce osiągnąć»");
            w.field("benefits","Oczekiwane korzyści",app==null?null:app.benefits(),"«Opisz korzyści dla firmy i pracowników»");
            w.heading("Heading1","3. Obecne i docelowe środowisko");
            infrastructure(w,"currentInfrastructure","Z czego firma korzysta obecnie?",app==null?null:app.currentInfrastructure());
            infrastructure(w,"targetInfrastructure","Co zostanie wykorzystane po migracji?",app==null?null:app.targetInfrastructure());
            w.heading("Heading1","4. Harmonogram");
            var tasks=new ArrayList<List<Cell>>();for(var task:app==null?Collections.<Task>singletonList(null):app.tasks())tasks.add(row(
                a("tasks.name",task==null?null:task.name(),"Wpisz zadanie"),a("tasks.start",task==null?null:task.start(),"Wpisz datę rozpoczęcia"),
                a("tasks.end",task==null?null:task.end(),"Wpisz datę zakończenia"),a("tasks.milestone",task==null?null:task.milestone(),"Wpisz oczekiwany rezultat")));
            w.table("tasks",List.of("Zadanie","Rozpoczęcie","Zakończenie","Rezultat"),tasks);
            w.heading("Heading1","5. Budżet");w.strong("Wszystkie kwoty są kwotami netto w PLN.");
            if(app==null)w.p("Wypełnij ilość i cenę jednostkową. Kolumnę Wartość możesz zostawić pustą — aplikacja ją wyliczy. Jeśli wpiszesz wartość, musi być zgodna z ilością i ceną.");
            var budget=new ArrayList<List<Cell>>();for(var line:app==null?Collections.<BudgetLine>singletonList(null):app.budget())budget.add(row(
                a("budget.description",line==null?null:line.description(),"Wpisz wydatek"),a("budget.category",line==null?null:line.category(),"Wpisz kategorię"),
                a("budget.quantity",line==null?null:qty(line.quantity()),"Wpisz ilość"),a("budget.unit",line==null?null:line.unit(),"Wpisz jednostkę"),
                a("budget.unitCost",line==null?null:money(line.unitCost()),"Wpisz cenę"),a("budget.total",line==null?null:money(line.total()),"Wyliczy aplikacja")));
            w.table("budget",List.of("Wydatek","Kategoria","Ilość","Jednostka","Cena jednostkowa","Wartość"),budget);
            w.strong(app==null?"Całkowity koszt projektu: aplikacja wyliczy go po imporcie.":"Całkowity koszt projektu: "+money(Rules.total(app).toPlainString())+" PLN");
            w.field("requestedAmount","Wnioskowane dofinansowanie (PLN)",app==null?null:money(app.requestedAmount()),"«Wpisz kwotę dofinansowania, o którą wnioskujesz»");
            w.heading("Heading1","6. Zespół");var members=new ArrayList<List<Cell>>();for(var member:app==null?Collections.<Member>singletonList(null):app.team())members.add(row(a("team.name",member==null?null:member.name(),"Wpisz imię i nazwisko"),a("team.role",member==null?null:member.role(),"Opisz rolę w projekcie")));
            w.table("team",List.of("Imię i nazwisko","Rola w projekcie"),members);
            return w.save();
        }catch(Exception e){throw new IllegalStateException("Nie można utworzyć formularza Word.",e);}
    }
    private void infrastructure(WordFile w,String key,String title,List<Infrastructure> values){
        w.heading("Heading2",title);var rows=new ArrayList<List<Cell>>();
        for(var item:values==null?Collections.<Infrastructure>singletonList(null):values)rows.add(row(a(key+".name",item==null?null:item.name(),"Wpisz system lub usługę"),a(key+".provider",item==null?null:item.provider(),"Wpisz dostawcę lub Infrastruktura własna"),a(key+".description",item==null?null:item.description(),"Opisz przeznaczenie")));
        w.table(key,List.of("System lub usługa","Dostawca","Sposób wykorzystania"),rows);
    }
    private byte[] evaluation(Evaluation e,Context context) throws Exception {
        var c=context.competition();var app=context.application();var w=new WordFile("cm:evaluation:v2");header(w,"Karta oceny wniosku o dofinansowanie",c);
        w.heading("Heading1","1. Oceniany projekt");w.table(null,List.of("Pole","Informacja"),List.of(
            row(text("Tytuł projektu"),text(app.title())),row(text("Wnioskodawca"),text(app.company().name())),row(text("NIP / identyfikator"),text(app.company().legalId())),
            row(text("Wnioskowane dofinansowanie"),text(money(app.requestedAmount())+" PLN netto")),row(text("Oceniający"),text(e.reviewer())),row(text("Data oceny"),text(e.date())),row(text("Status oceny"),text(state(e.state())))));
        w.heading("Heading1","2. Sprawdzenie warunków udziału");w.strong(e.eligible()?"Wniosek spełnia warunki kwalifikacji.":"Wniosek nie spełnia warunków kwalifikacji.");w.heading("Heading2","Uzasadnienie");w.p(e.reason());
        w.heading("Heading1","3. Ocena punktowa");var criteria=new HashMap<String,Criterion>();c.criteria().forEach(v->criteria.put(v.code(),v));var rows=new ArrayList<List<Cell>>();
        for(var score:e.scores()){var criterion=criteria.get(score.code());rows.add(row(text(criterion.name()),text(Integer.toString(score.points())),text(Integer.toString(criterion.maximum()))));}
        int maximum=c.criteria().stream().mapToInt(Criterion::maximum).sum();rows.add(row(text("Łącznie"),text(Integer.toString(e.total())),text(Integer.toString(maximum))));w.table(null,List.of("Kryterium","Przyznane punkty","Maksimum"),rows);
        for(var score:e.scores()){var criterion=criteria.get(score.code());w.heading("Heading2",criterion.name()+" — "+score.points()+" z "+criterion.maximum()+" punktów");w.p(score.comment());}
        w.heading("Heading1","4. Podsumowanie oceny");boolean qualified=e.eligible()&&e.total()>=c.threshold();
        w.table(null,List.of("Warunek","Wynik"),List.of(row(text("Spełnienie warunków kwalifikacji"),text(e.eligible()?"Tak":"Nie")),row(text("Uzyskany wynik"),text(e.total()+" punktów")),row(text("Próg kwalifikacji"),text(c.threshold()+" punktów")),row(text("Próg punktowy i warunki udziału"),text(qualified?"Spełnione":"Niespełnione"))));
        w.p("FINAL".equals(e.state())?(qualified?"Wniosek kwalifikuje się do rozpatrzenia w rankingu finansowania.":"Wniosek nie kwalifikuje się do przyznania dotacji."):"To szkic oceny. Wniosek może uczestniczyć w oficjalnym rankingu dopiero po zatwierdzeniu oceny.");
        w.p("Uzyskanie wymaganej liczby punktów nie oznacza automatycznego przyznania dotacji. O finansowaniu rozstrzygają kolejność w rankingu i dostępne środki; wynik jest utrwalany w odrębnej decyzji.");
        return w.save();
    }
    private byte[] decision(Decision d,Context context) throws Exception {
        var c=context.competition();var app=context.application();var e=context.evaluation();boolean award="AWARD".equals(d.outcome()),issued="ISSUED".equals(d.state());
        var w=new WordFile("cm:decision:v2");header(w,award?"Decyzja o przyznaniu dofinansowania":"Decyzja o odmowie dofinansowania",c);
        w.heading("Heading1","1. Dane decyzji");w.table(null,List.of("Pole","Informacja"),List.of(row(text(issued?"Data wydania":"Data projektu decyzji"),text(d.date())),row(text("Status decyzji"),text(state(d.state()))),row(text("Wnioskodawca"),text(app.company().name())),row(text("NIP / identyfikator"),text(app.company().legalId())),row(text("Tytuł projektu"),text(app.title())),row(text("Powiązana ocena końcowa"),text(e.reviewer()+", "+e.date()+", "+e.total()+" punktów"))));
        w.heading("Heading1","2. Rozstrzygnięcie");w.strong(award?(issued?"Przyznaje się":"Proponuje się przyznać")+" dofinansowanie w wysokości "+money(d.amount())+" PLN netto.":(issued?"Odmawia się":"Proponuje się odmówić")+" przyznania dofinansowania.");
        if(award)w.p("Dofinansowanie obejmuje całą kwotę wnioskowaną na realizację projektu „"+app.title()+"”.");
        w.heading("Heading1","3. Zestawienie finansowania");w.table(null,List.of("Pozycja","Kwota"),List.of(row(text("Całkowity koszt projektu"),text(money(Rules.total(app).toPlainString())+" PLN")),row(text("Wnioskowane dofinansowanie"),text(money(app.requestedAmount())+" PLN")),row(text("Przyznane dofinansowanie"),text(money(d.amount())+" PLN"))));w.p("Wszystkie kwoty są kwotami netto. Waluta finansowania: "+d.currency()+".");
        w.heading("Heading1","4. Uzasadnienie");w.p(d.reason());w.p("Ocena końcowa: "+e.total()+" punktów. Próg kwalifikacji: "+c.threshold()+" punktów. Warunki udziału: "+(e.eligible()?"spełnione.":"niespełnione."));
        w.heading("Heading1","5. Zakres projektu");for(var task:app.tasks())w.p("• "+task.name()+" — "+task.start()+" – "+task.end()+". Rezultat: "+task.milestone());
        w.heading("Heading1","6. Powiązane dokumenty");w.table(null,List.of("Dokument","Informacja pozwalająca go rozpoznać"),List.of(row(text("Wniosek o dofinansowanie"),text(app.title()+", "+app.company().name())),row(text("Końcowa karta oceny"),text(e.reviewer()+", "+e.date()+", "+e.total()+" punktów"))));
        w.p(issued?(award?"Decyzja została wydana. Przyznana kwota obciąża budżet konkursu. Wydana decyzja nie podlega dalszej edycji.":"Decyzja została wydana. Nie przyznano środków. Wydana decyzja nie podlega dalszej edycji."):"To szkic decyzji. Nie obciąża budżetu konkursu i nie stanowi jeszcze wydanego rozstrzygnięcia.");return w.save();
    }
    private byte[] competition(Competition c) throws Exception {
        var w=new WordFile("cm:competition:v2");header(w,"Informacja o konkursie",c);w.p(c.description());w.heading("Heading1","Warunki udziału");w.p(c.eligibilityRules());
        w.table(null,List.of("Parametr","Wartość"),List.of(row(text("Budżet konkursu"),text(money(c.budget())+" PLN")),row(text("Maksymalna dotacja"),text(money(c.maxGrant())+" PLN")),row(text("Próg kwalifikacji"),text(c.threshold()+" punktów")),row(text("Etap"),text(state(c.stage())))));
        w.heading("Heading1","Kryteria oceny");w.table(null,List.of("Kryterium","Maksymalna liczba punktów"),c.criteria().stream().map(v->row(text(v.name()),text(Integer.toString(v.maximum())))).toList());return w.save();
    }

    public Application importApplication(byte[] bytes,String competitionId){
        try{UUID.fromString(competitionId);}catch(Exception e){throw AppException.invalid("competitionId","Wybierz konkurs przed importem wniosku.");}
        var xml=WordFile.upload(bytes);var roots=controls(xml.getDocumentElement(),FORM);
        if(roots.size()!=1)throw AppException.invalid("Ten format wniosku nie jest obsługiwany. Pobierz aktualny, czytelny szablon CloudMigration. Stare formularze techniczne nie są obsługiwane.");
        var root=roots.getFirst();var company=new Company(required(root,"company.name","Nazwa firmy"),required(root,"company.legalId","NIP / identyfikator"),businessSize(required(root,"company.size","Wielkość przedsiębiorstwa")),required(root,"company.contact","Osoba kontaktowa"),required(root,"company.email","Adres e-mail"));
        var current=infrastructure(root,"currentInfrastructure","Obecne środowisko");var target=infrastructure(root,"targetInfrastructure","Docelowe środowisko");var tasks=new ArrayList<Task>();
        for(var row:rows(root,"tasks","Harmonogram"))tasks.add(new Task(required(row,"tasks.name","Nazwa zadania"),inputDate(required(row,"tasks.start","Data rozpoczęcia")),inputDate(required(row,"tasks.end","Data zakończenia")),required(row,"tasks.milestone","Rezultat zadania")));
        var budget=new ArrayList<BudgetLine>();
        for(var row:rows(root,"budget","Budżet")){
            String description=required(row,"budget.description","Opis wydatku"),category=required(row,"budget.category","Kategoria wydatku"),unit=required(row,"budget.unit","Jednostka");
            var quantity=decimal(required(row,"budget.quantity","Ilość"),"Ilość");var unitCost=Rules.money(decimal(required(row,"budget.unitCost","Cena jednostkowa"),"Cena jednostkowa").toPlainString());
            var computed=quantity.multiply(unitCost).setScale(2,RoundingMode.HALF_UP);var supplied=optional(row,"budget.total","Wartość wydatku");
            if(!supplied.isBlank()&&decimal(supplied,"Wartość wydatku").compareTo(computed)!=0)throw AppException.invalid("budget.total","Wartość wydatku „"+description+"” nie zgadza się z ilością i ceną jednostkową.");
            budget.add(new BudgetLine(description,category,quantity.toPlainString(),unit,unitCost.toPlainString(),computed.toPlainString()));
        }
        var members=new ArrayList<Member>();for(var row:rows(root,"team","Zespół"))members.add(new Member(required(row,"team.name","Imię i nazwisko członka zespołu"),required(row,"team.role","Rola w projekcie")));
        var app=new Application(UUID.randomUUID().toString(),1,competitionId,"DRAFT",null,company,required(root,"title","Tytuł projektu"),required(root,"description","Opis projektu"),required(root,"objectives","Cele projektu"),required(root,"benefits","Oczekiwane korzyści"),current,target,List.copyOf(tasks),List.copyOf(budget),List.copyOf(members),Rules.money(decimal(required(root,"requestedAmount","Wnioskowane dofinansowanie"),"Wnioskowane dofinansowanie").toPlainString()).toPlainString());
        codec.encode(Type.application,app);return app;
    }
    private List<Infrastructure> infrastructure(Element root,String key,String label){var out=new ArrayList<Infrastructure>();for(var row:rows(root,key,label))out.add(new Infrastructure(required(row,key+".name",label+": system lub usługa"),required(row,key+".provider",label+": dostawca"),required(row,key+".description",label+": przeznaczenie")));return List.copyOf(out);}
    private List<Element> rows(Element root,String key,String label){
        var wrappers=controls(root,"cm:"+key);if(wrappers.size()!=1)throw AppException.invalid(key,"Brak lub powtórzona tabela: "+label+". Pobierz aktualny szablon.");
        var table=wrappers.getFirst().getElementsByTagNameNS(W,"tbl");if(table.getLength()!=1)throw AppException.invalid(key,"Nieprawidłowa tabela: "+label);
        var rows=new ArrayList<Element>();for(var node=table.item(0).getFirstChild();node!=null;node=node.getNextSibling())if(node instanceof Element row&&W.equals(row.getNamespaceURI())&&"tr".equals(row.getLocalName())){
            var fields=row.getElementsByTagNameNS(W,"sdt");boolean filled=false;for(int i=0;i<fields.getLength();i++)if(!answerText((Element)fields.item(i)).isBlank())filled=true;if(filled)rows.add(row);
        }
        if(rows.isEmpty())throw AppException.invalid(key,"Uzupełnij co najmniej jedną pozycję w sekcji: "+label+".");return rows;
    }
    private String answerText(Element control){String value=visible(control);var properties=child(control,"sdtPr");var alias=properties==null?null:child(properties,"alias");return alias!=null&&value.equals(alias.getAttributeNS(W,"val"))?"":value;}
    private String optional(Element root,String key,String label){var values=controls(root,"cm:"+key);if(values.size()!=1)throw AppException.invalid(key,"Brak lub powtórzone pole „"+label+"”. Pobierz aktualny szablon.");return answerText(values.getFirst());}
    private String required(Element root,String key,String label){var value=optional(root,key,label);if(value.isBlank())throw AppException.invalid(key,"Uzupełnij pole: "+label+".");return value;}
    private BigDecimal decimal(String text,String label){try{return new BigDecimal(text.replaceAll("[\\s\\u00a0\\u202f]","").replace(',','.'));}catch(Exception e){throw AppException.invalid(label,"Nieprawidłowa liczba w polu „"+label+"”. Użyj np. 1 000,00.");}}
    private String businessSize(String value){String v=java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).strip();return switch(v){case "micro","mikroprzedsiebiorstwo","mikro"->"MICRO";case "small","male przedsiebiorstwo","male"->"SMALL";case "other","inna wielkosc przedsiebiorstwa","inne"->"OTHER";default->throw AppException.invalid("company.size","Wielkość przedsiębiorstwa: wpisz Mikroprzedsiębiorstwo lub Małe przedsiębiorstwo.");};}
    private String inputDate(String value){try{return LocalDate.parse(value).toString();}catch(DateTimeException ignored){try{return LocalDate.parse(value,DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT)).toString();}catch(DateTimeException e){throw AppException.invalid("tasks.date","Nieprawidłowa data: "+value+". Użyj RRRR-MM-DD lub DD.MM.RRRR.");}}}
}
