package pl.cloudmigration.storage;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import pl.cloudmigration.documents.DocumentCodec;
import pl.cloudmigration.documents.DocumentCodec.Type;

@Component
public class DocumentRepository {
    private final BaseXClient client;
    private final DocumentCodec codec;
    public DocumentRepository(BaseXClient client, DocumentCodec codec) {
        this.client = client;
        this.codec = codec;
    }
    public Object get(Type type, String id) {
        return codec.decode(type, client.execute(client.resource("get"),
                Map.of("path", type.path(uuid(id)))), type.dto);
    }
    public <T> T get(Type type, String id, Class<T> clazz) {
        return clazz.cast(get(type, id));
    }
    public List<Object> list(Type type) {
        try {
            var root = DocumentCodec
                    .parse(client
                            .execute(client.resource("list"),
                                    Map.of("kind", type.name()))
                            .getBytes(StandardCharsets.UTF_8))
                    .getDocumentElement();
            var result = new ArrayList<Object>();
            for (var node = root.getFirstChild(); node != null; node = node
                    .getNextSibling())
                if (node instanceof Element e) {
                    var id = e.getAttribute("id");
                    result.add(get(type, id));
                }
            return result;
        } catch (pl.cloudmigration.api.AppException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
    public void save(Type type, String id, int expected, Object value) {
        client.execute(client.resource("save"),
                Map.of("kind", type.name(), "path", type.path(uuid(id)),
                        "expected", Integer.toString(expected), "xml",
                        codec.encode(type, value)));
    }
    public void delete(Type type, String id, int revision) {
        client.execute(client.resource("delete"), Map.of("path",
                type.path(uuid(id)), "expected", Integer.toString(revision)));
    }
    private String uuid(String id) {
        try {
            return UUID.fromString(id).toString();
        } catch (Exception e) {
            throw pl.cloudmigration.api.AppException
                    .invalid("Nieprawidłowy identyfikator.");
        }
    }
}
