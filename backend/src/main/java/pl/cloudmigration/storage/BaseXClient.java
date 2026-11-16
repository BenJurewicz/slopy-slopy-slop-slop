package pl.cloudmigration.storage;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.cloudmigration.api.AppException;

@Component
public class BaseXClient {
    private final String url;
    private final String auth;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();
    public final String database;
    public BaseXClient(@Value("${basex.url}") String url,
            @Value("${basex.username}") String user,
            @Value("${basex.password}") String password,
            @Value("${basex.database}") String database) {
        this.url = url;
        this.database = database;
        this.auth = "Basic " + Base64.getEncoder().encodeToString(
                (user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    public String resource(String name) {
        try (var input = getClass()
                .getResourceAsStream("/xquery/" + name + ".xq")) {
            if (input == null)
                throw new IllegalArgumentException("Unknown query");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public String execute(String source, Map<String, String> values) {
        var body = queryBody(source, values);
        try {
            var request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", auth)
                    .header("Content-Type", "application/xml")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            var response = http.send(request,
                    HttpResponse.BodyHandlers.ofString());
            return responseBody(response);
        } catch (AppException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AppException(503, "DATABASE",
                    "Przerwano połączenie z bazą.");
        } catch (Exception e) {
            throw new AppException(503, "DATABASE",
                    "Nie można połączyć się z bazą danych.");
        }
    }

    private String queryBody(String source, Map<String, String> values) {
        var body = new StringBuilder(
                "<query xmlns=\"http://basex.org/rest\"><text>")
                .append(escape(source)).append("</text>");
        var bindings = new LinkedHashMap<>(values);
        bindings.put("db", database);
        String context = bindings.containsKey("xml")
                ? bindings.remove("xml")
                : bindings.remove("batch");
        if (context != null)
            body.append("<context>")
                    .append(context.replaceFirst("^\\s*<\\?xml[^?]*\\?>", ""))
                    .append("</context>");
        bindings.forEach((key, value) -> body.append("<variable name=\"")
                .append(escape(key)).append("\" value=\"").append(escape(value))
                .append("\"/>"));
        return body.append("</query>").toString();
    }

    private String responseBody(HttpResponse<String> response) {
        if (response.statusCode() == 200)
            return response.body();
        if (response.body().contains("CM_NOT_FOUND"))
            throw new AppException(404, "NOT_FOUND", "Dokument nie istnieje.");
        if (response.body().contains("CM_CONFLICT"))
            throw AppException.conflict(
                    "Dokument zmieniono, jest zablokowany lub narusza powiązania procesu. Odśwież dane.");
        if (response.body().contains("CM_RULE"))
            throw AppException.invalid(
                    "Operacja narusza zasady konkursu lub limit finansowania.");
        throw new AppException(503, "DATABASE",
                "Baza danych jest niedostępna lub nie została skonfigurowana.");
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace("\"", "&quot;").replace("\r", "&#13;")
                .replace("\n", "&#10;").replace("\t", "&#9;");
    }
}
