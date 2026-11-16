package pl.cloudmigration.storage;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import pl.cloudmigration.demo.Examples;
import pl.cloudmigration.documents.DocumentCodec;
import pl.cloudmigration.documents.DocumentCodec.Type;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DocumentRepositoryTest {
    final BaseXClient client = mock(BaseXClient.class);
    final DocumentCodec codec = new DocumentCodec();
    final DocumentRepository repository = new DocumentRepository(client, codec);

    @ParameterizedTest
    @EnumSource(Type.class)
    void decodesListedDocumentsWithOneRequest(Type type) {
        Object document = switch (type) {
            case competition -> Examples.competition();
            case application -> Examples.application();
            case evaluation -> Examples.evaluation();
            case decision -> Examples.decision();
        };
        String xml = codec.encode(type, document);
        String fragment = xml.replaceFirst("<\\?xml[^>]*\\?>", "");
        when(client.resource("list")).thenReturn("list");
        when(client.resource("get")).thenReturn("get");
        when(client.execute(eq("list"), anyMap())).thenReturn(
                "<documents>" + fragment + fragment + "</documents>");
        when(client.execute(eq("get"), anyMap())).thenReturn(xml);

        assertEquals(List.of(document, document), repository.list(type));
        verify(client, times(1)).execute(anyString(), anyMap());
    }

    @Test
    void emptyListNeedsOnlyOneRequest() {
        when(client.resource("list")).thenReturn("list");
        when(client.execute(eq("list"), anyMap())).thenReturn("<documents/>");
        assertTrue(repository.list(Type.application).isEmpty());
        verify(client, times(1)).execute(anyString(), anyMap());
    }
}
