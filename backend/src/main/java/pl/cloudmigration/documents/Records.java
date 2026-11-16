package pl.cloudmigration.documents;

import java.util.Map;

public final class Records {
    private Records() {
    }

    @SuppressWarnings("unchecked")
    public static <T> T with(T value, Map<String, Object> changes) {
        try {
            var fields = value.getClass().getRecordComponents();
            var types = new Class<?>[fields.length];
            var args = new Object[fields.length];
            for (int i = 0; i < fields.length; i++) {
                types[i] = fields[i].getType();
                args[i] = changes.containsKey(fields[i].getName())
                        ? changes.get(fields[i].getName())
                        : fields[i].getAccessor().invoke(value);
            }
            return (T) value.getClass().getDeclaredConstructor(types)
                    .newInstance(args);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String id(Object value) {
        try {
            return (String) value.getClass().getMethod("id").invoke(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static int revision(Object value) {
        try {
            return (Integer) value.getClass().getMethod("revision")
                    .invoke(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
