package br.com.liberdadefamilia.shared.internal;

import br.com.liberdadefamilia.shared.PartialRequest;
import com.fasterxml.jackson.databind.JavaType;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.media.Schema;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import org.springframework.stereotype.Component;

/**
 * Gives every {@link PartialRequest} schema an explicit empty {@code required} list. The swagger model drops an empty
 * list in its setter, so the list is set on the field; the serializer then writes {@code "required": []}.
 */
@Component
class PartialRequestConverter implements ModelConverter {

    private static final Field REQUIRED = requiredField();

    @Override
    @SuppressWarnings("rawtypes")
    public Schema resolve(AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
        Schema schema = chain.hasNext() ? chain.next().resolve(type, context, chain) : null;
        if (schema != null && isPartial(type)) {
            // With the schema referenced, the model itself lives in the context.
            Schema model = schema;
            if (schema.get$ref() != null) {
                String name = schema.get$ref().substring(schema.get$ref().lastIndexOf('/') + 1);
                model = context.getDefinedModels().get(name);
            }
            if (model != null) {
                makeAllOptional(model);
            }
        }
        return schema;
    }

    @SuppressWarnings("rawtypes")
    private static void makeAllOptional(Schema model) {
        try {
            REQUIRED.set(model, new ArrayList<String>());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean isPartial(AnnotatedType type) {
        JavaType java = Json.mapper().constructType(type.getType());
        return java != null && java.getRawClass().isAnnotationPresent(PartialRequest.class);
    }

    private static Field requiredField() {
        try {
            Field field = Schema.class.getDeclaredField("required");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
