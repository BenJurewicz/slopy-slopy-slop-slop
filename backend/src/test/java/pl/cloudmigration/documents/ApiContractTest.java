package pl.cloudmigration.documents;

import java.net.*;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "basex.url=http://127.0.0.1:9/rest")
class ApiContractTest {
    @LocalServerPort
    int port;
    final HttpClient client = HttpClient.newHttpClient();
    HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest
                .newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET()
                .build(), HttpResponse.BodyHandlers.ofString());
    }
    @Test
    void databaseUnavailableIsSanitized503() throws Exception {
        var response = get("/api/applications");
        assertEquals(503, response.statusCode());
        assertTrue(response.body().contains("DATABASE"));
        assertFalse(response.body().contains("BASEX_PASSWORD"));
    }
    @Test
    void unknownApiAndAssetAre404NotSpaOr500() throws Exception {
        for (String path : new String[]{"/api/not-an-endpoint",
                "/_app/immutable/missing.js"}) {
            var response = get(path);
            assertEquals(404, response.statusCode());
            assertTrue(response.body().contains("NOT_FOUND"));
            assertFalse(response.body().contains("<!doctype"));
        }
    }
    @Test
    void competitionCollectionAndCreationAreNotAvailable() throws Exception {
        assertEquals(404, get("/api/competitions").statusCode());
        for (String method : new String[]{"POST", "DELETE"}) {
            var response = client.send(HttpRequest
                    .newBuilder(URI.create(
                            "http://127.0.0.1:" + port + "/api/competition"))
                    .method(method, HttpRequest.BodyPublishers.noBody())
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(405, response.statusCode());
        }
        assertEquals(404, get("/competitions").statusCode());
    }
    @Test
    void malformedJsonIs400() throws Exception {
        var response = client.send(
                HttpRequest
                        .newBuilder(URI.create("http://127.0.0.1:" + port
                                + "/api/competition"))
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString("{oops"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("REQUEST"));
    }
    @Test
    void budgetCalculationUsesDecimalArithmetic() throws Exception {
        var body = "[{\"description\":\"Test\",\"category\":\"Usługi\",\"quantity\":\"1.5\",\"unit\":\"h\",\"unitCost\":\"10.01\",\"total\":\"0.00\"}]";
        var response = client.send(HttpRequest
                .newBuilder(URI.create(
                        "http://127.0.0.1:" + port + "/api/budget/calculate"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("15.02"));
    }
    @Test
    void projectContributionIsCalculatedOnServerAndOverRequestedFundingRejected()
            throws Exception {
        var body = "[{\"description\":\"Test\",\"category\":\"Usługi\",\"quantity\":\"1.5\",\"unit\":\"h\",\"unitCost\":\"10.01\",\"total\":\"0.00\"}]";
        var response = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                        + "/api/budget/calculate?requestedAmount=10.00"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"ownContribution\":\"5.02\""));
        var invalid = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                        + "/api/budget/calculate?requestedAmount=20.00"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(422, invalid.statusCode());
    }
    @Test
    void destructiveDemoActionsRequireJsonConfirmationBeforeAccessingDatabase()
            throws Exception {
        for (String path : new String[]{"/api/demo/load", "/api/demo"}) {
            String method = path.endsWith("load") ? "POST" : "DELETE";
            var unconfirmed = client.send(HttpRequest
                    .newBuilder(URI.create("http://127.0.0.1:" + port + path))
                    .header("Content-Type", "application/json")
                    .method(method,
                            HttpRequest.BodyPublishers
                                    .ofString("{\"confirmed\":false}"))
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(422, unconfirmed.statusCode());
            assertTrue(unconfirmed.body().contains("Potwierdź"));
            var form = client.send(HttpRequest
                    .newBuilder(URI.create("http://127.0.0.1:" + port + path))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .method(method,
                            HttpRequest.BodyPublishers
                                    .ofString("confirmed=true"))
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(415, form.statusCode());
        }
    }
    @Test
    void reportCatalogueExplainsEveryReportWithoutDatabaseAccess()
            throws Exception {
        var response = get("/api/queries");
        assertEquals(200, response.statusCode());
        var queries = new tools.jackson.databind.ObjectMapper()
                .readTree(response.body());
        assertEquals(9, queries.size());
        for (var query : queries) {
            assertFalse(query.get("description").asString().isBlank());
            assertFalse(query.get("description").asString()
                    .startsWith("Parametry:"));
            assertFalse(query.get("source").asString().isBlank());
        }
        assertTrue(response.body()
                .contains("Wnioski o dokładnie tę kwotę są pomijane"));
        assertTrue(response.body()
                .contains("Raport nie tworzy ani nie wydaje decyzji"));
    }
    @Test
    void openApiDocumentsSuccessAndErrorResponseSchemas() throws Exception {
        var response = get("/v3/api-docs");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("downloadApplicationExample"));
        assertTrue(response.body().contains("getCompetition"));
        assertTrue(
                response.body().contains("#/components/schemas/Competition"));
        assertTrue(response.body().contains("\"503\""));
        assertTrue(response.body().contains("currency"));
    }

    @Test
    void missingApplicationBudgetIs422AndCompetitionWordIsUnsupported()
            throws Exception {
        var response = client.send(HttpRequest
                .newBuilder(URI.create(
                        "http://127.0.0.1:" + port + "/api/applications"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"revision\":0}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(422, response.statusCode());
        assertEquals(400, get(
                "/api/documents/competition/00000000-0000-0000-0000-000000000001/docx")
                .statusCode());
    }
    @Test
    void documentDownloadsUseCloudMigrationWithoutASelector() throws Exception {
        assertEquals(503, get("/api/documents/template").statusCode());
        assertEquals(503, get("/api/documents/example").statusCode());
    }
    @Test
    void importResolvesCloudMigrationWithoutACompetitionParameter()
            throws Exception {
        String body = "--docx-boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"wniosek.docx\"\r\nContent-Type: application/vnd.openxmlformats-officedocument.wordprocessingml.document\r\n\r\nnot-a-docx\r\n--docx-boundary--\r\n";
        var response = client.send(
                HttpRequest
                        .newBuilder(URI.create("http://127.0.0.1:" + port
                                + "/api/documents/import"))
                        .header("Content-Type",
                                "multipart/form-data; boundary=docx-boundary")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(503, response.statusCode());
        assertTrue(response.body().contains("DATABASE"));
    }

}
