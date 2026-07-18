package dev.palimpsest.engine.importer;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

/** Parsed interchange records (post-validation). */
public final class ImportModels {

    private ImportModels() {
    }

    public record Ref(String authority, String id) {
    }

    public record ParsedEntity(Ref ref, String entityType, List<Ref> externalIds) {
        static ParsedEntity from(JsonNode n) {
            List<Ref> ext = new ArrayList<>();
            if (n.has("external_ids")) {
                n.get("external_ids").forEach(e -> ext.add(new Ref(e.get("authority").asText(), e.get("id").asText())));
            }
            JsonNode r = n.get("ref");
            return new ParsedEntity(new Ref(r.get("authority").asText(), r.get("id").asText()),
                    n.get("entity_type").asText(), ext);
        }
    }

    public record Support(String source, String recordKind, String externalId, String contentHash, JsonNode raw) {
    }

    public record ParsedClaim(Ref subject, String predicate, Ref objectEntity, JsonNode objectLiteral,
                              JsonNode validTime, String confidenceKind, Double confidencePoint,
                              String confidenceScale, JsonNode confidenceRaw, boolean confidenceCalibrated,
                              String method, JsonNode methodDetail, String assertedBy, List<Support> support) {

        static ParsedClaim from(JsonNode n) {
            JsonNode s = n.get("subject");
            Ref subject = new Ref(s.get("authority").asText(), s.get("id").asText());

            JsonNode obj = n.get("object");
            Ref objEntity = null;
            JsonNode objLiteral = null;
            if (obj.has("entity")) {
                JsonNode e = obj.get("entity");
                objEntity = new Ref(e.get("authority").asText(), e.get("id").asText());
            } else if (obj.has("literal")) {
                objLiteral = obj.get("literal");
            }

            JsonNode conf = n.get("confidence");
            String kind = conf.get("kind").asText();
            Double point = null;
            String scale = null;
            JsonNode raw = null;
            boolean calibrated = false;
            if ("source_native_scalar".equals(kind)) {
                point = conf.get("point").asDouble();
                scale = conf.get("scale").asText();
                raw = conf.get("raw");
                calibrated = conf.path("calibrated").asBoolean(false);
            }

            List<Support> support = new ArrayList<>();
            n.get("support").forEach(sp -> support.add(new Support(
                    sp.get("source").asText(), sp.get("record_kind").asText(),
                    sp.get("external_id").asText(), sp.get("content_hash").asText(), sp.get("raw"))));

            return new ParsedClaim(subject, n.get("predicate").asText(), objEntity, objLiteral,
                    n.get("valid_time"), kind, point, scale, raw, calibrated,
                    n.get("method").asText(), n.get("method_detail"), n.get("asserted_by").asText(), support);
        }
    }
}
