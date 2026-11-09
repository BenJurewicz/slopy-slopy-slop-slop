package pl.cloudmigration.demo;

import java.util.List;
import pl.cloudmigration.documents.Models.*;

public final class Examples {
    private Examples() {}
    public static final String COMPETITION="00000000-0000-0000-0000-000000000001";
    public static final String APPLICATION="00000000-0000-0000-0000-000000000002";
    public static final String EVALUATION="00000000-0000-0000-0000-000000000003";
    public static Competition competition() {
        return new Competition(COMPETITION,1,"CloudMigration 2026","Chmura dla Lokalnych Biznesów",pl.cloudmigration.documents.Models.ELIGIBILITY_RULES,"PLN","250000.00","150000.00",60,"OPEN",List.of(
            new Criterion("technical","Wykonalność techniczna",25),new Criterion("security","Bezpieczeństwo",25),
            new Criterion("benefits","Korzyści biznesowe",20),new Criterion("team","Harmonogram i zespół",20),new Criterion("budget","Uzasadnienie budżetu",10)));
    }
    public static Application application() {
        return new Application(APPLICATION,1,COMPETITION,"DRAFT",null,
            new Company("Biuro Rachunkowe Żółw","1234567890","MICRO","Anna Kowalska","anna@example.org","ul. Przykładowa 12, 80-001 Gdańsk","123456785","Anna Kowalska – właścicielka"),
            "Bezpieczna księgowość w chmurze","Migracja bazy danych i oprogramowania.\nAutomatyczne kopie zapasowe.",
            "Ograniczenie przestojów","Bezpieczny dostęp zdalny",
            List.of(new Infrastructure("Serwer księgowy","Lokalny","Serwer legacy")),
            List.of(new Infrastructure("Baza danych","Azure","Zarządzana baza i backup")),
            List.of(new Task("Migracja","2026-11-01","2026-12-31","Odbiór systemu")),
            List.of(new BudgetLine("Migracja i konfiguracja","Usługi","1","projekt","50000.00","50000.00")),
            List.of(new Member("Anna Kowalska","Koordynator")),"50000.00","WN/2026/DEMO01","PR/2026/DEMO01","2026-11-01","2026-12-31","50000.00","0.00");
    }
    public static Evaluation evaluation() {
        return new Evaluation(EVALUATION,1,APPLICATION,"DRAFT","Jan Nowak","2026-10-12",true,"Spełnia kryteria",List.of(
            new Score("technical",20,"Wykonalny"),new Score("security",20,"Bezpieczny"),new Score("benefits",15,"Korzyści"),
            new Score("team",15,"Realny harmonogram"),new Score("budget",10,"Uzasadniony")),80,"OC/2026/DEMO01");
    }
    public static Decision decision() {
        return new Decision("00000000-0000-0000-0000-000000000004",1,APPLICATION,EVALUATION,"DRAFT","2026-10-12","AWARD","50000.00","PLN","Projekt zakwalifikowany do finansowania","DEC/2026/DEMO01",true);
    }
}
