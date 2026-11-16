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
        var formatter = NumberFormat
                .getNumberInstance(Locale.forLanguageTag("pl-PL"));
        formatter.setMinimumFractionDigits(2);
        formatter.setMaximumFractionDigits(2);
        return formatter.format(new BigDecimal(value));
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

    private byte[] application(Application application,
            Competition competition) {
        try {
            var word = new WordTemplates(Type.application);
            word.replace("Konkurs: CloudMigration 2026",
                    competition == null
                            ? "Konkurs: CloudMigration"
                            : "Konkurs: " + competition.name());
            if (application == null) {
                word.note(
                        "Wypełnij wykropkowane pola. Numer nowego wniosku nadaje aplikacja podczas zapisu; numer z importowanego dokumentu nie jest zachowywany. Numer projektu może pozostać pusty - wtedy również nada go aplikacja. Powielaj całe bloki oznaczonej infrastruktury, zadań, wydatków i zespołu. Kwoty netto w PLN; daty RRRR-MM-DD lub DD.MM.RRRR. Koszt projektu, wartości wydatków i wkład własny można pozostawić puste do przeliczenia przy imporcie. Wniosek zostanie automatycznie przypisany do CloudMigration.");
                return word.save();
            }
            word.notice("Status wniosku: " + state(application.state())
                    + (application.submittedAt() == null
                            ? ""
                            : ". Złożono: " + application.submittedAt()));
            word.fill(values("application_id", application.number(),
                    "project_title", application.title(), "project_id",
                    application.projectNumber(), "beneficiary",
                    application.company().name(), "ben_address",
                    application.company().address(), "ben_nip",
                    application.company().legalId(), "ben_regon",
                    application.company().regon(), "ben_rep",
                    application.company().representative(), "company_size",
                    size(application.company().size()), "company_contact",
                    application.company().contact(), "company_email",
                    application.company().email(), "description",
                    application.description(), "project_goal",
                    application.objectives(), "benefits",
                    application.benefits(), "impl_start",
                    application.implementationStart(), "impl_end",
                    application.implementationEnd(), "project_cost",
                    money(application.projectCost()), "req_fund",
                    money(application.requestedAmount()), "down_pay",
                    money(application.ownContribution())));
            infrastructure(word, "current_infrastructure",
                    application.currentInfrastructure());
            infrastructure(word, "target_infrastructure",
                    application.targetInfrastructure());
            word.repeat("tasks", application.tasks().stream()
                    .map(task -> values("tasks_name", task.name(),
                            "tasks_start", task.start(), "tasks_end",
                            task.end(), "tasks_milestone", task.milestone()))
                    .toList());
            word.repeat("budget", application.budget().stream()
                    .map(line -> values("budget_description",
                            line.description(), "budget_category",
                            line.category(), "budget_quantity",
                            line.quantity().replace('.', ','), "budget_unit",
                            line.unit(), "budget_unit_cost",
                            money(line.unitCost()), "budget_total",
                            money(line.total())))
                    .toList());
            word.repeat("team",
                    application
                            .team().stream().map(member -> values("team_name",
                                    member.name(), "team_role", member.role()))
                            .toList());
            return word.save();
        } catch (Exception exception) {
            throw new IllegalStateException("Nie można utworzyć wniosku Word.",
                    exception);
        }
    }

    private void infrastructure(WordTemplates word, String key,
            List<Infrastructure> values) {
        word.repeat(key, values.stream()
                .map(v -> values(key + "_name", v.name(), key + "_provider",
                        v.provider(), key + "_description", v.description()))
                .toList());
    }

    private byte[] evaluation(Evaluation evaluation, Context context)
            throws Exception {
        var application = context.application();
        var competition = context.competition();
        var word = new WordTemplates(Type.evaluation);
        boolean qualified = evaluation.eligible()
                && evaluation.total() >= competition.threshold();
        word.scores(competition.criteria(), evaluation.scores());
        word.fill(values("evaluation_number", evaluation.number(),
                "project_title", application.title(), "project_number",
                application.projectNumber(), "applicant_name",
                application.company().name(), "applicant_tax_id",
                application.company().legalId(), "applicant_regon",
                application.company().regon(), "requested_funding",
                money(application.requestedAmount()), "evaluator_name",
                evaluation.reviewer(), "evaluation_date", evaluation.date(),
                "evaluation_status", state(evaluation.state()),
                "eligibility_status",
                evaluation.eligible()
                        ? "Wniosek spełnia warunki udziału."
                        : "Wniosek nie spełnia warunków udziału.",
                "eligibility_justification", evaluation.reason(), "total_score",
                evaluation.total(), "qualification_status",
                evaluation.eligible() ? "Tak" : "Nie", "qualification_treshold",
                competition.threshold(), "final_status",
                qualified ? "Spełnione" : "Niespełnione"));
        if ("FINAL".equals(evaluation.state()))
            word.replace(
                    "To szkic oceny. Wniosek może uczestniczyć w oficjalnym rankingu dopiero po zatwierdzeniu oceny.",
                    qualified
                            ? "Ocena końcowa została zatwierdzona. Wniosek kwalifikuje się do rozpatrzenia w rankingu finansowania."
                            : "Ocena końcowa została zatwierdzona. Wniosek nie kwalifikuje się do finansowania.");
        return word.save();
    }

    private byte[] decision(Decision decision, Context context)
            throws Exception {
        var application = context.application();
        var evaluation = context.evaluation();
        var competition = context.competition();
        boolean award = "AWARD".equals(decision.outcome()),
                issued = "ISSUED".equals(decision.state());
        var word = new WordTemplates(Type.decision);
        word.fill(values("decision_id", decision.number(), "project_title",
                application.title(), "project_id", application.projectNumber(),
                "decision_date", decision.date(), "beneficiary",
                application.company().name(), "ben_address",
                application.company().address(), "ben_nip",
                application.company().legalId(), "ben_regon",
                application.company().regon(), "ben_rep",
                application.company().representative(), "app_id",
                application.number(), "project_goal", application.objectives(),
                "status", award ? "przyznaniu" : "odmowie", "project_cost",
                money(application.projectCost()), "req_fund",
                money(application.requestedAmount()), "accept_fund",
                money(decision.amount()), "down_pay",
                money(Rules.money(application.projectCost())
                        .subtract(Rules.money(decision.amount()))
                        .toPlainString()),
                "impl_start", application.implementationStart(), "impl_end",
                application.implementationEnd(), "app_points",
                evaluation.total(), "points_threshold", competition.threshold(),
                "points_amount",
                evaluation.total() >= competition.threshold() ? "Tak" : "Nie",
                "budget_allows", decision.budgetAllows() ? "Tak" : "Nie",
                "criteria", evaluation.eligible() ? "Tak" : "Nie"));
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
        word.note("Uzasadnienie: " + decision.reason());
        word.note("Powiązana ocena: " + evaluation.number() + ", "
                + evaluation.reviewer() + ", " + evaluation.date() + ".");
        word.notice(issued
                ? "Status: Wydana. Decyzja nie podlega dalszej edycji."
                : "Status: Szkic. Dokument nie jest wydanym rozstrzygnięciem i nie obciąża budżetu konkursu.");
        return word.save();
    }

    public Application importApplication(byte[] bytes, String competitionId) {
        try {
            UUID.fromString(competitionId);
        } catch (Exception exception) {
            throw AppException.invalid("Wybierz konkurs przed importem.");
        }
        var root = WordUpload.read(bytes).getDocumentElement();
        if (controls(root, "application_id").size() != 1
                || controls(root, "project_id").size() != 1
                || controls(root, "beneficiary").isEmpty())
            throw AppException.invalid(
                    "Nieobsługiwany formularz DOCX. Użyj aktualnego Wniosek_o_dofinansowanie.docx; wcześniejsze formaty zostały usunięte.");
        var company = readCompany(root);
        var current = infrastructure(root, "current_infrastructure",
                "Obecna infrastruktura");
        var target = infrastructure(root, "target_infrastructure",
                "Docelowa infrastruktura");
        var tasks = readTasks(root);
        var budget = readBudgetLines(root);
        var team = readTeam(root);
        var total = budget.stream().map(line -> new BigDecimal(line.total()))
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
        var application = new Application(id, 1, competitionId, "DRAFT", null,
                company, required(root, "project_title", "Tytuł projektu"),
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
        codec.encode(Type.application, application);
        return application;
    }

    private Company readCompany(Element root) {
        return new Company(required(root, "beneficiary", "Beneficjent"),
                required(root, "ben_nip", "NIP"),
                businessSize(required(root, "company_size",
                        "Wielkość przedsiębiorstwa")),
                required(root, "company_contact", "Osoba kontaktowa"),
                required(root, "company_email", "Adres e-mail"),
                required(root, "ben_address", "Adres beneficjenta"),
                required(root, "ben_regon", "REGON"),
                required(root, "ben_rep", "Reprezentowany przez"));
    }

    private List<Task> readTasks(Element root) {
        var tasks = new ArrayList<Task>();
        for (var block : groups(root, "tasks", "Harmonogram"))
            tasks.add(new Task(required(block, "tasks_name", "Zadanie"),
                    inputDate(required(block, "tasks_start",
                            "Rozpoczęcie zadania")),
                    inputDate(required(block, "tasks_end",
                            "Zakończenie zadania")),
                    required(block, "tasks_milestone", "Rezultat zadania")));
        return tasks;
    }

    private List<BudgetLine> readBudgetLines(Element root) {
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
        return budget;
    }

    private List<Member> readTeam(Element root) {
        var team = new ArrayList<Member>();
        for (var block : groups(root, "team", "Zespół"))
            team.add(new Member(required(block, "team_name", "Imię i nazwisko"),
                    required(block, "team_role", "Rola w projekcie")));
        return team;
    }

    private List<Infrastructure> infrastructure(Element root, String key,
            String label) {
        var infrastructure = new ArrayList<Infrastructure>();
        for (var block : groups(root, key, label))
            infrastructure.add(new Infrastructure(
                    required(block, key + "_name",
                            label + ": system lub usługa"),
                    required(block, key + "_provider", label + ": dostawca"),
                    required(block, key + "_description",
                            label + ": sposób wykorzystania")));
        return List.copyOf(infrastructure);
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
