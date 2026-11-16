package pl.cloudmigration.workflow;

import java.math.*;
import java.time.*;
import java.util.*;
import pl.cloudmigration.documents.Models.*;
import pl.cloudmigration.api.AppException;

public final class Rules {
    private Rules() {
    }
    public static BigDecimal money(String value) {
        try {
            if (value == null || value.length() > 64 || value.contains("e")
                    || value.contains("E"))
                throw new NumberFormatException();
            var amount = new BigDecimal(value);
            if (amount.signum() < 0 || amount.scale() > 2)
                throw new NumberFormatException();
            return amount.setScale(2);
        } catch (Exception e) {
            throw AppException.invalid(
                    "Kwota musi być nieujemną liczbą z maksymalnie dwoma miejscami po przecinku.");
        }
    }
    public static BigDecimal quantity(String value) {
        try {
            if (value == null || value.length() > 64 || value.contains("e")
                    || value.contains("E"))
                throw new NumberFormatException();
            var number = new BigDecimal(value);
            if (number.signum() <= 0 || number.scale() > 3)
                throw new NumberFormatException();
            return number;
        } catch (Exception e) {
            throw AppException.invalid(
                    "Ilość musi być dodatnią liczbą z maksymalnie trzema miejscami po przecinku.");
        }
    }
    public static BigDecimal total(Application application) {
        require(application.budget() != null && !application.budget().isEmpty(),
                "Dodaj pozycje budżetu.");
        return application.budget().stream().map(line -> {
            try {
                var quantity = quantity(line.quantity());
                var computed = quantity.multiply(money(line.unitCost()))
                        .setScale(2, RoundingMode.HALF_UP);
                require(computed.compareTo(money(line.total())) == 0,
                        "Nieprawidłowa suma pozycji budżetu: "
                                + line.description());
                return computed;
            } catch (AppException e) {
                throw e;
            } catch (Exception e) {
                throw AppException.invalid("Nieprawidłowa ilość w budżecie.");
            }
        }).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    public static void competition(Competition c) {
        require(pl.cloudmigration.documents.Models.ELIGIBILITY_RULES
                .equals(c.eligibilityRules()), "eligibilityRules",
                "Warunki kwalifikacji programu są stałe: mikro- i małe firmy, migracja do chmury, kwoty netto w PLN.");
        require(money(c.budget()).signum() > 0,
                "Budżet konkursu musi być dodatni.");
        require(money(c.maxGrant()).signum() > 0
                && money(c.maxGrant()).compareTo(money(c.budget())) <= 0,
                "Nieprawidłowy limit dotacji.");
        var codes = new HashSet<String>();
        long maximum = 0;
        for (var criterion : c.criteria()) {
            require(codes.add(criterion.code()) && criterion.maximum() > 0,
                    "Powtórzone lub nieprawidłowe kryterium.");
            maximum += criterion.maximum();
        }
        require(c.threshold() >= 0 && c.threshold() <= maximum,
                "Nieprawidłowy próg kwalifikacji.");
    }
    public static void application(Application a, Competition c) {
        require(a.company().legalId().matches("[0-9]{10}"),
                "NIP musi zawierać 10 cyfr.");
        require(a.company().regon().matches("(?:[0-9]{9}|[0-9]{14})"),
                "company.regon", "REGON musi składać się z 9 lub 14 cyfr.");
        var start = date(a.implementationStart());
        var end = date(a.implementationEnd());
        require(!end.isBefore(start), "implementationEnd",
                "Koniec realizacji projektu jest wcześniejszy niż początek.");
        for (var task : a.tasks())
            require(!date(task.start()).isBefore(start)
                    && !date(task.end()).isAfter(end), "tasks",
                    "Zadania muszą mieścić się w okresie realizacji projektu.");
        require(money(a.requestedAmount()).signum() > 0, "requestedAmount",
                "Wnioskowana kwota musi być dodatnia.");
        require(money(a.requestedAmount()).compareTo(total(a)) <= 0,
                "requestedAmount", "Dotacja przekracza koszty projektu.");
        require(money(a.requestedAmount()).compareTo(money(c.maxGrant())) <= 0,
                "requestedAmount", "Dotacja przekracza limit konkursu.");
        require(money(a.projectCost()).compareTo(total(a)) == 0, "projectCost",
                "Całkowity koszt projektu nie odpowiada budżetowi.");
        require(money(a.ownContribution())
                .compareTo(total(a).subtract(money(a.requestedAmount()))) == 0,
                "ownContribution",
                "Wkład własny nie odpowiada kosztom projektu i wnioskowanej dotacji.");
        require(a.company().email().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"),
                "company.email", "Nieprawidłowy adres e-mail.");
        for (var task : a.tasks()) {
            try {
                require(!date(task.end()).isBefore(date(task.start())),
                        "tasks.end",
                        "Termin końcowy zadania jest wcześniejszy niż początkowy.");
            } catch (java.time.format.DateTimeParseException e) {
                throw AppException.invalid("Nieprawidłowa data zadania.");
            }
        }
        if ("SUBMITTED".equals(a.state()))
            require(a.submittedAt() != null, "Brak daty złożenia wniosku.");
    }
    public static void evaluation(Evaluation e, Application a, Competition c) {
        require("SUBMITTED".equals(a.state()),
                "Oceniać można tylko złożony wniosek.");
        var maxima = new HashMap<String, Integer>();
        for (var criterion : c.criteria())
            maxima.put(criterion.code(), criterion.maximum());
        var codes = new HashSet<String>();
        long total = 0;
        for (var score : e.scores()) {
            require(codes.add(score.code()) && maxima.containsKey(score.code())
                    && score.points() >= 0
                    && score.points() <= maxima.get(score.code()),
                    "scores.points",
                    "Nieprawidłowe punkty lub kryterium oceny.");
            total += score.points();
        }
        require(codes.equals(maxima.keySet()),
                "Ocena musi obejmować wszystkie kryteria.");
        require(total == e.total(), "total",
                "Suma punktów nie odpowiada ocenom kryteriów.");
        require(!e.eligible()
                || Set.of("MICRO", "SMALL").contains(a.company().size()),
                "eligible",
                "Firma nie spełnia warunku wielkości przedsiębiorstwa.");
    }
    public static void decision(Decision d, Application a, Evaluation e,
            Competition c) {
        require(e.applicationId().equals(a.id()) && "FINAL".equals(e.state()),
                "Decyzja wymaga końcowej oceny tego wniosku.");
        if ("AWARD".equals(d.outcome())) {
            require(d.budgetAllows(),
                    "Przyznanie dotacji wymaga potwierdzenia dostępnego budżetu.");
            require(e.eligible() && e.total() >= c.threshold(),
                    "Projekt nie kwalifikuje się do finansowania.");
            require(money(d.amount())
                    .compareTo(money(a.requestedAmount())) == 0,
                    "Dotacja musi odpowiadać całej wnioskowanej kwocie.");
        } else
            require(money(d.amount()).signum() == 0,
                    "Odmowa finansowania musi mieć kwotę zero.");
    }
    private static LocalDate date(String value) {
        try {
            return LocalDate.parse(value);
        } catch (java.time.DateTimeException e) {
            throw AppException.invalid(
                    "Nieprawidłowa data projektu lub zadania. Użyj RRRR-MM-DD.");
        }
    }
    public static void require(boolean condition, String field,
            String message) {
        if (!condition)
            throw AppException.invalid(field, message);
    }
    public static void require(boolean condition, String message) {
        if (!condition)
            throw AppException.invalid(message);
    }
}
