package dev.palimpsest.engine.importer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.InputStream;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Server-side validation against the FROZEN interchange schema (ARCHITECTURE §2,
 * Flow A step 6a: "the server trusts no client"). The schema is packaged from
 * repo-root contracts/ at build time; a test asserts byte-identity so the engine
 * validates against the same bytes the pipeline emits against.
 */
@Component
public class InterchangeValidator {

    private final JsonSchema claimSchema;
    private final JsonSchema entitySchema;
    private final ObjectMapper om;

    public InterchangeValidator(ObjectMapper om) throws Exception {
        this.om = om;
        JsonNode schemaNode;
        try (InputStream in = getClass().getResourceAsStream("/contracts/claim.schema.json")) {
            if (in == null) {
                throw new IllegalStateException("frozen contract /contracts/claim.schema.json not on classpath");
            }
            schemaNode = om.readTree(in);
        }
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        this.claimSchema = factory.getSchema(schemaNode);

        // Entity records validate against ONLY the $defs/entityRecord subschema
        // (a root+$ref combination would double-apply the claim constraints).
        ObjectNode entityNode = om.createObjectNode();
        entityNode.set("$schema", schemaNode.get("$schema"));
        entityNode.set("$defs", schemaNode.get("$defs"));
        entityNode.put("$ref", "#/$defs/entityRecord");
        this.entitySchema = factory.getSchema(entityNode);
    }

    public record Result(boolean valid, String message) {
    }

    public Result validateClaim(JsonNode node) {
        return toResult(claimSchema.validate(node));
    }

    public Result validateEntity(JsonNode node) {
        return toResult(entitySchema.validate(node));
    }

    private Result toResult(Set<ValidationMessage> messages) {
        if (messages.isEmpty()) {
            return new Result(true, null);
        }
        String msg = messages.stream().map(ValidationMessage::getMessage)
                .collect(Collectors.joining("; "));
        return new Result(false, msg);
    }

    public ObjectMapper mapper() {
        return om;
    }
}
