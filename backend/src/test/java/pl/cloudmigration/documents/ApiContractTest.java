package pl.cloudmigration.documents;

import java.net.*;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="basex.url=http://127.0.0.1:9/rest")
class ApiContractTest {
    @LocalServerPort int port;
    final HttpClient client=HttpClient.newHttpClient();
    HttpResponse<String> get(String path) throws Exception {return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).GET().build(),HttpResponse.BodyHandlers.ofString());}
    @Test void databaseUnavailableIsSanitized503() throws Exception {
        var response=get("/api/applications");assertEquals(503,response.statusCode());assertTrue(response.body().contains("DATABASE"));assertFalse(response.body().contains("BASEX_PASSWORD"));
    }
    @Test void unknownApiAndAssetAre404NotSpaOr500() throws Exception {
        for(String path:new String[]{"/api/not-an-endpoint","/_app/immutable/missing.js"}){
            var response=get(path);assertEquals(404,response.statusCode());assertTrue(response.body().contains("NOT_FOUND"));assertFalse(response.body().contains("<!doctype"));
        }
    }
    @Test void malformedJsonIs400() throws Exception {
        var response=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/competitions")).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{oops")).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(400,response.statusCode());assertTrue(response.body().contains("REQUEST"));
    }
    @Test void budgetCalculationUsesDecimalArithmetic() throws Exception {
        var body="[{\"description\":\"Test\",\"category\":\"Usługi\",\"quantity\":\"1.5\",\"unit\":\"h\",\"unitCost\":\"10.01\",\"total\":\"0.00\"}]";
        var response=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/budget/calculate")).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(200,response.statusCode());assertTrue(response.body().contains("15.02"));
    }
    @Test void openApiDocumentsSuccessAndErrorResponseSchemas() throws Exception {
        var response=get("/v3/api-docs");assertEquals(200,response.statusCode());
        assertTrue(response.body().contains("downloadApplicationExample"));assertTrue(response.body().contains("listCompetitions"));assertTrue(response.body().contains("#/components/schemas/Competition"));
        assertTrue(response.body().contains("\"503\""));assertTrue(response.body().contains("currency"));
    }

    @Test void importRequiresExplicitCompetitionBeforeAccessingDatabase() throws Exception {
        String body="--docx-boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"wniosek.docx\"\r\nContent-Type: application/vnd.openxmlformats-officedocument.wordprocessingml.document\r\n\r\nnot-a-docx\r\n--docx-boundary--\r\n";
        var response=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/documents/import")).header("Content-Type","multipart/form-data; boundary=docx-boundary").POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(400,response.statusCode());assertTrue(response.body().contains("REQUEST"));
        assertTrue(response.body().contains("Odśwież całą stronę"));
        assertTrue(response.body().contains("wybierz konkurs"));
    }

}
