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
import pl.cloudmigration.workflow.*;
import static pl.cloudmigration.docx.WordTemplates.*;

@Component
public class DocxDocuments {
    public record Context(Competition competition, Application application,
            Evaluation evaluation) {
    }
    private final DocumentCodec codec;
    public DocxDocuments(DocumentCodec codec) {
        this.codec = codec;
    }

    private static Map<String, String> values(Object... pairs) {
        var map = new LinkedHashMap<String, String>();
        for (int i = 0; i < pairs.length; i += 2)
            map.put((String) pairs[i], Objects.toString(pairs[i + 1], ""));
        return map;
    }

    private static String money(String value) {
        var f = NumberFormat.getNumberInstance(Locale.forLanguageTag("pl-PL"));
        f.setMinimumFractionDigits(2);
        f.setMaximumFractionDigits(2);
        return f.format(new BigDecimal(value));
    }

    private static String size(String size) {
        return switch (size) {
            case "MICRO" -> "Mikroprzedsiębiorstwo";
            case "SMALL" -> "Małe przedsiębiorstwo";
            default -> "Inna wielkość przedsiębiorstwa";
        };
    }

    private static String state(String state) {
        return switch (state) {
            case "DRAFT" -> "Szkic";
            case "SUBMITTED" -> "Złożony";
            case "FINAL" -> "Ocena końcowa";
            case "ISSUED" -> "Wydana";
            default -> state;
        };
    }

    public byte[] template(Competition competition) {
        return application(null, competition);
    }

    public byte[] example(Competition competition) {
        return application(Examples.application(), competition);
    }

    public byte[] export(Type type, Object value, Context context) {
        try {
            return switch (type) {
                case application ->
                    application((Application) value, context.competition());
                case evaluation -> evaluation((Evaluation) value, context);
                case decision -> decision((Decision) value, context);
                case competition -> throw AppException.invalid(
                        "DOCX jest dostępny wyłącznie dla wniosku, oceny i decyzji.");
            };
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Nie można wypełnić dostarczonego dokumentu Word.", e);
        }
    }

    private byte[] application(Application a, Competition competition) {
        try {
            var word = new WordTemplates(Type.application);
            word.replace("Konkurs: CloudMigration 2026",
                    competition == null
                            ? "Konkurs: wybierz w aplikacji podczas importu"
                            : "Konkurs: " + competition.name());
            if (a == null) {
                word.note(
                        "Wypełnij wykropkowane pola. Numer nowego wniosku nadaje aplikacja podczas zapisu; numer z importowanego dokumentu nie jest zachowywany. Numer projektu może pozostać pusty - wtedy również nada go aplikacja. Powielaj całe bloki oznaczonej infrastruktury, zadań, wydatków i zespołu. Kwoty netto w PLN; daty RRRR-MM-DD lub DD.MM.RRRR. Koszt projektu, wartości wydatków i wkład własny można pozostawić puste do przeliczenia przy imporcie. Wniosek zostanie przypisany do konkursu wybranego w aplikacji.");
                return word.save();
            }
            word.notice("Status wniosku: " + state(a.state())
                    + (a.submittedAt() == null
                            ? ""
                            : ". Złożono: " + a.submittedAt()));
            word.fill(values("application_id", a.number(), "project_title",
                    a.title(), "project_id", a.projectNumber(), "beneficiary",
                    a.company().name(), "ben_address", a.company().address(),
                    "ben_nip", a.company().legalId(), "ben_regon",
                    a.company().regon(), "ben_rep",
                    a.company().representative(), "company_size",
                    size(a.company().size()), "company_contact",
                    a.company().contact(), "company_email", a.company().email(),
                    "description", a.description(), "project_goal",
                    a.objectives(), "benefits", a.benefits(), "impl_start",
                    a.implementationStart(), "impl_end", a.implementationEnd(),
                    "project_cost", money(a.projectCost()), "req_fund",
                    money(a.requestedAmount()), "down_pay",
                    money(a.ownContribution())));
            infrastructure(word, "current_infrastructure",
                    a.currentInfrastructure());
            infrastructure(word, "target_infrastructure",
                    a.targetInfrastructure());
            word.repeat("tasks", a.tasks().stream()
                    .map(t -> values("tasks_name", t.name(), "tasks_start",
                            t.start(), "tasks_end", t.end(), "tasks_milestone",
                            t.milestone()))
                    .toList());
            word.repeat("budget", a.budget().stream()
                    .map(b -> values("budget_description", b.description(),
                            "budget_category", b.category(), "budget_quantity",
                            b.quantity().replace('.', ','), "budget_unit",
                            b.unit(), "budget_unit_cost", money(b.unitCost()),
                            "budget_total", money(b.total())))
                    .toList());
            word.repeat("team", a.team().stream().map(
                    m -> values("team_name", m.name(), "team_role", m.role()))
                    .toList());
            return word.save();
        } catch (Exception e) {
            throw new IllegalStateException("Nie można utworzyć wniosku Word.",
                    e);
        }
    }

    private void infrastructure(WordTemplates word, String key,
            List<Infrastructure> values) {
        word.repeat(key, values.stream()
                .map(v -> values(key + "_name", v.name(), key + "_provider",
                        v.provider(), key + "_description", v.description()))
                .toList());
    }

    private byte[] evaluation(Evaluation e, Context context) throws Exception {
        var a = context.application();
        var c = context.competition();
        var word = new WordTemplates(Type.evaluation);
        boolean qualified = e.eligible() && e.total() >= c.threshold();
        word.scores(c.criteria(), e.scores());
        word.fill(values("evaluation_number", e.number(), "project_title",
                a.title(), "project_number", a.projectNumber(),
                "applicant_name", a.company().name(), "applicant_tax_id",
                a.company().legalId(), "applicant_regon", a.company().regon(),
                "requested_funding", money(a.requestedAmount()),
                "evaluator_name", e.reviewer(), "evaluation_date", e.date(),
                "evaluation_status", state(e.state()), "eligibility_status",
                e.eligible()
                        ? "Wniosek spełnia warunki udziału."
                        : "Wniosek nie spełnia warunków udziału.",
                "eligibility_justification", e.reason(), "total_score",
                e.total(), "qualification_status", e.eligible() ? "Tak" : "Nie",
                "qualification_treshold", c.threshold(), "final_status",
                qualified ? "Spełnione" : "Niespełnione"));
        if ("FINAL".equals(e.state()))
            word.replace(
                    "To szkic oceny. Wniosek może uczestniczyć w oficjalnym rankingu dopiero po zatwierdzeniu oceny.",
                    qualified
                            ? "Ocena końcowa została zatwierdzona. Wniosek kwalifikuje się do rozpatrzenia w rankingu finansowania."
                            : "Ocena końcowa została zatwierdzona. Wniosek nie kwalifikuje się do finansowania.");
        return word.save();
    }

    private byte[] decision(Decision d, Context context) throws Exception {
        var a = context.application();
        var e = context.evaluation();
        var c = context.competition();
        boolean award = "AWARD".equals(d.outcome()),
                issued = "ISSUED".equals(d.state());
        var word = new WordTemplates(Type.decision);
        word.fill(values("decision_id", d.number(), "project_title", a.title(),
                "project_id", a.projectNumber(), "decision_date", d.date(),
                "beneficiary", a.company().name(), "ben_address",
                a.company().address(), "ben_nip", a.company().legalId(),
                "ben_regon", a.company().regon(), "ben_rep",
                a.company().representative(), "app_id", a.number(),
                "project_goal", a.objectives(), "status",
                award ? "przyznaniu" : "odmowie", "project_cost",
                money(a.projectCost()), "req_fund", money(a.requestedAmount()),
                "accept_fund", money(d.amount()), "down_pay",
                money(Rules.money(a.projectCost())
                        .subtract(Rules.money(d.amount())).toPlainString()),
                "impl_start", a.implementationStart(), "impl_end",
                a.implementationEnd(), "app_points", e.total(),
                "points_threshold", c.threshold(), "points_amount",
                e.total() >= c.threshold() ? "Tak" : "Nie", "budget_allows",
                d.budgetAllows() ? "Tak" : "Nie", "criteria",
                e.eligible() ? "Tak" : "Nie"));
        word.replace(
                "Kwota stanowi część kwoty / pełną kwotę wnioskowanego dofinansowania.",
                award
                        ? "Kwota stanowi pełną kwotę wnioskowanego dofinansowania."
                        : "Nie przyznano dofinansowania.");
        word.replace("przekracza próg kwalifikacji?",
                "osiąga lub przekracza próg kwalifikacji?");
        word.replace("pełne / częściowe dofinansowanie",
                "pełne dofinansowanie");
        if (!award) {
            word.replace("Decyzja o przyznaniu dofinansowania projektu",
                    "Decyzja o odmowie dofinansowania projektu");
            word.omitFrom("6. Obowiązki Beneficjenta");
            word.note(
                    "Odmowa nie przyznaje środków ani nie nakłada obowiązków związanych z wypłatą dotacji.");
        }
        word.note("Uzasadnienie: " + d.reason());
        word.note("Powiązana ocena: " + e.number() + ", " + e.reviewer() + ", "
                + e.date() + ".");
        word.notice(issued
                ? "Status: Wydana. Decyzja nie podlega dalszej edycji."
                : "Status: Szkic. Dokument nie jest wydanym rozstrzygnięciem i nie obciąża budżetu konkursu.");
        return word.save();
    }

    public Application importApplication(byte[] bytes, String competitionId) {
        try {
            UUID.fromString(competitionId);
        } catch (Exception e) {
            throw AppException.invalid("Wybierz konkurs przed importem.");
        }
        var root = WordTemplates.upload(bytes).getDocumentElement();
        if (controls(root, "application_id").size() != 1
                || controls(root, "project_id").size() != 1
                || controls(root, "beneficiary").isEmpty())
            throw AppException.invalid(
                    "Nieobsługiwany formularz DOCX. Użyj aktualnego Wniosek_o_dofinansowanie.docx; wcześniejsze formaty zostały usunięte.");
        var company = new Company(required(root, "beneficiary", "Beneficjent"),
                required(root, "ben_nip", "NIP"),
                businessSize(required(root, "company_size",
                        "Wielkość przedsiębiorstwa")),
                required(root, "company_contact", "Osoba kontaktowa"),
                required(root, "company_email", "Adres e-mail"),
                required(root, "ben_address", "Adres beneficjenta"),
                required(root, "ben_regon", "REGON"),
                required(root, "ben_rep", "Reprezentowany przez"));
        var current = infrastructure(root, "current_infrastructure",
                "Obecna infrastruktura");
        var target = infrastructure(root, "target_infrastructure",
                "Docelowa infrastruktura");
        var tasks = new ArrayList<Task>();
        for (var block : groups(root, "tasks", "Harmonogram"))
            tasks.add(new Task(required(block, "tasks_name", "Zadanie"),
                    inputDate(required(block, "tasks_start",
                            "Rozpoczęcie zadania")),
                    inputDate(required(block, "tasks_end",
                            "Zakończenie zadania")),
                    required(block, "tasks_milestone", "Rezultat zadania")));
        var budget = new ArrayList<BudgetLine>();
        for (var block : groups(root, "budget", "Budżet")) {
            var description = required(block, "budget_description", "Wydatek");
            var quantity = decimal(required(block, "budget_quantity", "Ilość"),
                    "Ilość");
            var cost = Rules.money(decimal(
                    required(block, "budget_unit_cost", "Cena jednostkowa"),
                    "Cena jednostkowa").toPlainString());
            var total = quantity.multiply(cost).setScale(2,
                    RoundingMode.HALF_UP);
            checkAmount(block, "budget_total",
                    "Wartość wydatku \"" + description + "\"", total);
            budget.add(new BudgetLine(description,
                    required(block, "budget_category", "Kategoria"),
                    quantity.toPlainString(),
                    required(block, "budget_unit", "Jednostka"),
                    cost.toPlainString(), total.toPlainString()));
        }
        var team = new ArrayList<Member>();
        for (var block : groups(root, "team", "Zespół"))
            team.add(new Member(required(block, "team_name", "Imię i nazwisko"),
                    required(block, "team_role", "Rola w projekcie")));
        var total = budget.stream().map(b -> new BigDecimal(b.total()))
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
        var requested = Rules.money(decimal(
                required(root, "req_fund", "Wnioskowane dofinansowanie"),
                "Wnioskowane dofinansowanie").toPlainString());
        Rules.require(requested.compareTo(total) <= 0,
                "Dotacja przekracza koszt projektu.");
        checkAmount(root, "project_cost", "Całkowity koszt projektu", total);
        checkAmount(root, "down_pay", "Wkład własny Beneficjenta",
                total.subtract(requested));
        String id = UUID.randomUUID().toString(),
                number = Workflow.number("WN", id),
                projectNumber = optional(root, "project_id", "Numer projektu");
        if (projectNumber.isBlank())
            projectNumber = Workflow.number("PR", id);
        var app = new Application(id, 1, competitionId, "DRAFT", null, company,
                required(root, "project_title", "Tytuł projektu"),
                required(root, "description", "Opis projektu"),
                required(root, "project_goal", "Cel projektu"),
                required(root, "benefits", "Oczekiwane korzyści"), current,
                target, List.copyOf(tasks), List.copyOf(budget),
                List.copyOf(team), requested.toPlainString(), number,
                projectNumber,
                inputDate(required(root, "impl_start",
                        "Początek realizacji projektu")),
                inputDate(required(root, "impl_end",
                        "Koniec realizacji projektu")),
                total.toPlainString(),
                total.subtract(requested).toPlainString());
        codec.encode(Type.application, app);
        return app;
    }

    private List<Infrastructure> infrastructure(Element root, String key,
            String label) {
        var list = new ArrayList<Infrastructure>();
        for (var b : groups(root, key, label))
            list.add(new Infrastructure(
                    required(b, key + "_name", label + ": system lub usługa"),
                    required(b, key + "_provider", label + ": dostawca"),
                    required(b, key + "_description",
                            label + ": sposób wykorzystania")));
        return List.copyOf(list);
    }

    private List<Element> groups(Element root, String tag, String label) {
        var blocks = controls(root, tag);
        if (blocks.isEmpty())
            throw AppException.invalid(
                    "Brak bloku: " + label + ". Użyj aktualnego formularza.");
        var filled = new ArrayList<Element>();
        for (var block : blocks) {
            var all = block.getElementsByTagNameNS(W, "sdt");
            boolean present = false;
            for (int i = 0; i < all.getLength(); i++)
                if (!answer((Element) all.item(i)).isBlank())
                    present = true;
            if (present)
                filled.add(block);
        }
        if (filled.isEmpty())
            throw AppException.invalid(
                    "Uzupełnij co najmniej jedną pozycję: " + label + ".");
        return filled;
    }

    private String answer(Element control) {
        String value = text(control).strip();
        return value.isBlank() || value.matches("[.\\s\\u00a0\\u2026]*")
                ? ""
                : value;
    }

    private String optional(Element root, String key, String label) {
        var fields = controls(root, key);
        if (fields.isEmpty())
            throw AppException.invalid(
                    "Brak pola: " + label + ". Użyj aktualnego formularza.");
        String result = "";
        for (var field : fields) {
            String value = answer(field);
            if (value.isBlank())
                continue;
            if (!result.isBlank() && !result.equals(value))
                throw AppException.invalid("Powtórzone pole \"" + label
                        + "\" zawiera różne odpowiedzi. Wpisz jednakowy tekst w obu polach.");
            result = value;
        }
        if (fields.size() > 1 && !key.equals("project_title"))
            throw AppException.invalid("Powtórzone pole: " + label + ".");
        return result;
    }

    private String required(Element root, String key, String label) {
        String value = optional(root, key, label);
        if (value.isBlank())
            throw AppException.invalid("Uzupełnij pole: " + label + ".");
        return value;
    }

    private BigDecimal decimal(String value, String label) {
        try {
            var normalized = value.replaceAll("[\\s\\u00a0\\u202f]", "")
                    .replace(',', '.');
            if (normalized.length() > 64 || !normalized
                    .matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)"))
                throw new NumberFormatException();
            return new BigDecimal(normalized);
        } catch (Exception e) {
            throw AppException
                    .invalid("Nieprawidłowa liczba w polu: " + label + ".");
        }
    }

    private void checkAmount(Element root, String tag, String label,
            BigDecimal expected) {
        String supplied = optional(root, tag, label);
        if (!supplied.isBlank()
                && decimal(supplied, label).compareTo(expected) != 0)
            throw AppException.invalid(
                    label + " nie zgadza się z przeliczonym budżetem.");
    }

    private String businessSize(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).strip();
        return switch (normalized) {
            case "mikroprzedsiebiorstwo", "mikro", "micro" -> "MICRO";
            case "male przedsiebiorstwo", "male", "small" -> "SMALL";
            case "inna wielkosc przedsiebiorstwa", "inne", "other" -> "OTHER";
            default -> throw AppException.invalid(
                    "Wpisz wielkość przedsiębiorstwa: Mikroprzedsiębiorstwo lub Małe przedsiębiorstwo.");
        };
    }

    private String inputDate(String value) {
        for (var format : List.of(DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("dd.MM.uuuu"),
                DateTimeFormatter.ofPattern("dd MM uuuu"))) {
            try {
                return LocalDate
                        .parse(value,
                                format.withResolverStyle(ResolverStyle.STRICT))
                        .toString();
            } catch (DateTimeException ignored) {
            }
        }
        throw AppException.invalid("Nieprawidłowa data: " + value
                + ". Użyj RRRR-MM-DD lub DD.MM.RRRR.");
    }
}
