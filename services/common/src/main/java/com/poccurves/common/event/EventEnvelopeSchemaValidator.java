package com.poccurves.common.event;

import tools.jackson.databind.JsonNode;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;

import java.io.InputStream;
import java.util.List;

public final class EventEnvelopeSchemaValidator {

    private static final String SCHEMA_RESOURCE = "/contracts/events/envelope.schema.json";

    private final Schema schema;

    public EventEnvelopeSchemaValidator() {
        SchemaRegistry registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
        try (InputStream schemaStream = EventEnvelopeSchemaValidator.class.getResourceAsStream(SCHEMA_RESOURCE)) {
            if (schemaStream == null) {
                throw new IllegalStateException("Schema não encontrado no classpath: " + SCHEMA_RESOURCE);
            }
            this.schema = registry.getSchema(schemaStream);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Falha ao carregar " + SCHEMA_RESOURCE, e);
        }
    }

    /** Retorna a lista de violações; vazia quando o envelope é válido contra o contrato. */
    public List<Error> validate(JsonNode envelopeJson) {
        return schema.validate(envelopeJson);
    }

    public boolean isValid(JsonNode envelopeJson) {
        return validate(envelopeJson).isEmpty();
    }
}
