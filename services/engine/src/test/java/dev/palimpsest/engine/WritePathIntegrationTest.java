package dev.palimpsest.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

/**
 * §8 "import → read → action" write-path coverage that did not exist before WP-R1
 * (finding F4: the sole integration test covered import→read only). Each test uses
 * a disjoint, uniquely-named id space and asserts DELTAS, so the methods are
 * order-independent and do not collide with the fixture or one another.
 */
class WritePathIntegrationTest extends IntegrationTestBase {

    // ---- JSON builders (schema-valid; modelled on fixtures/synthetic) ---------

    private String entity(String auth, String id) {
        return ("{\"schema_version\":\"0.1.0\",\"entity_type\":\"person\","
              + "\"ref\":{\"authority\":\"%s\",\"id\":\"%s\"},"
              + "\"external_ids\":[{\"authority\":\"%s\",\"id\":\"%s\"}]}").formatted(auth, id, auth, id);
    }

    private String nameClaim(String auth, String id, String name, String src) {
        return ("{\"schema_version\":\"0.1.0\",\"subject\":{\"authority\":\"%s\",\"id\":\"%s\"},"
              + "\"predicate\":\"has-name\",\"object\":{\"literal\":{\"kind\":\"string\",\"value\":\"%s\"}},"
              + "\"confidence\":{\"kind\":\"unscored\"},\"method\":\"imported\",\"asserted_by\":\"pipeline\","
              + "\"support\":[{\"source\":\"%s\",\"record_kind\":\"person\",\"external_id\":\"%s\","
              + "\"content_hash\":\"h-%s\",\"raw\":{\"id\":\"%s\"}}]}").formatted(auth, id, name, src, id, id, id);
    }

    private String relClaim(String auth, String a, String b, double point, String src) {
        int raw = (int) Math.round(point * 100);
        return ("{\"schema_version\":\"0.1.0\",\"subject\":{\"authority\":\"%s\",\"id\":\"%s\"},"
              + "\"predicate\":\"associated-with\",\"object\":{\"entity\":{\"authority\":\"%s\",\"id\":\"%s\"}},"
              + "\"confidence\":{\"kind\":\"source_native_scalar\",\"scale\":\"synth_0_100\",\"raw\":%d,"
              + "\"point\":%s,\"calibrated\":false},\"method\":\"imported\",\"asserted_by\":\"pipeline\","
              + "\"valid_time\":{\"start_earliest\":\"1590-01-01\",\"start_latest\":null,\"end_earliest\":null,"
              + "\"end_latest\":\"1626-12-31\",\"approximate\":false,\"original\":{\"start\":{\"type_code\":\"AF/IN\","
              + "\"year\":\"1590\"},\"end\":{\"type_code\":\"BF/IN\",\"year\":\"1626\"}}},"
              + "\"support\":[{\"source\":\"%s\",\"record_kind\":\"relationship\",\"external_id\":\"%s-%s\","
              + "\"content_hash\":\"h-%s-%s\",\"raw\":{\"person1\":\"%s\",\"person2\":\"%s\"}}]}")
                .formatted(auth, a, auth, b, raw, point, src, a, b, a, b, a, b);
    }

    private long lookup(String auth, String extId) throws Exception {
        return getJson("/api/v1/entities/lookup?authority=" + auth + "&externalId=" + extId)
                .get("data").get("id").asLong();
    }

    private String status(long claimId) throws Exception {
        // Read the DERIVED status from the projection via the API (never recomputed here).
        return getJson("/api/v1/claims/" + claimId).get("data").get("status").asText();
    }

    private int events(long claimId, String type) {
        Integer c = jdbc.queryForObject(
                "SELECT count(*) FROM claim_event WHERE claim_id=? AND event_type=?", Integer.class, claimId, type);
        return c == null ? 0 : c;
    }

    private int outbox(long claimId, String kind) {
        Integer c = jdbc.queryForObject(
                "SELECT count(*) FROM outbox WHERE aggregate='claim' AND aggregate_id=? AND kind=?",
                Integer.class, String.valueOf(claimId), kind);
        return c == null ? 0 : c;
    }

    // ---- lifecycle: assert -> dispute -> supersede (I1/I2/I3) -----------------

    @Test
    void claim_lifecycle_assert_dispute_supersede_folds_through_the_projection() throws Exception {
        String auth = "it-life";
        importBatch("entities", "it-life-src", entity(auth, "L1") + "\n" + entity(auth, "L2"));

        // assert (create) the claim through the invariant path
        ResponseEntity<String> asserted = action("assert-claim", relClaim(auth, "L1", "L2", 0.55, "it-life-src"));
        assertThat(asserted.getStatusCode().value()).isEqualTo(200);
        long claimId = om.readTree(asserted.getBody()).get("data").get("claimId").asLong();

        assertThat(status(claimId)).isEqualTo("asserted");
        assertThat(events(claimId, "assert")).isEqualTo(1);            // event row (I2)
        assertThat(outbox(claimId, "claim.asserted")).isEqualTo(1);    // outbox row (I2)

        // dispute -> status folds to disputed (I3), read from the projection
        ResponseEntity<String> disputed = action("dispute",
                "{\"claimId\":%d,\"ground\":\"existence\",\"reason\":\"contested by a second source\"}".formatted(claimId));
        assertThat(disputed.getStatusCode().value()).isEqualTo(200);
        assertThat(status(claimId)).isEqualTo("disputed");
        assertThat(events(claimId, "dispute")).isEqualTo(1);
        assertThat(outbox(claimId, "claim.dispute")).isEqualTo(1);

        // supersede -> status folds to superseded; a replacement claim is created
        String supersedeBody = "{\"claimId\":%d,\"replacement\":%s,\"reason\":\"corrected dating\"}"
                .formatted(claimId, relClaim(auth, "L1", "L2", 0.80, "it-life-src"));
        ResponseEntity<String> superseded = action("supersede", supersedeBody);
        assertThat(superseded.getStatusCode().value()).isEqualTo(200);
        long supersededBy = om.readTree(superseded.getBody()).get("data").get("supersededBy").asLong();
        assertThat(supersededBy).isNotEqualTo(claimId);

        assertThat(status(claimId)).isEqualTo("superseded");           // fold from projection
        assertThat(events(claimId, "supersede")).isEqualTo(1);
        assertThat(outbox(claimId, "claim.supersede")).isEqualTo(1);
        assertThat(status(supersededBy)).isEqualTo("asserted");        // replacement is live
    }

    // ---- ER (Flow F): merge two same-named entities, no silent merge (PP3) ----

    @Test
    void merge_entities_records_canonical_without_a_silent_merge() throws Exception {
        String auth = "it-er";
        importBatch("entities", "it-er-src", entity(auth, "E1") + "\n" + entity(auth, "E2"));
        importBatch("claims", "it-er-src",
                nameClaim(auth, "E1", "Ambiguous Person", "it-er-src") + "\n"
              + nameClaim(auth, "E2", "Ambiguous Person", "it-er-src"));

        long a = lookup(auth, "E1");
        long b = lookup(auth, "E2");
        int entitiesBefore = jdbc.queryForObject("SELECT count(*) FROM entity", Integer.class);

        ResponseEntity<String> merged = action("merge-entities",
                "{\"memberEntityIds\":[%d,%d],\"rationale\":\"same person, two records\"}".formatted(a, b));
        assertThat(merged.getStatusCode().value()).isEqualTo(200);
        JsonNode data = om.readTree(merged.getBody()).get("data");
        assertThat(data.get("canonical").asLong()).isEqualTo(Math.min(a, b));

        // PP3: NO silent merge — both source entities still exist as distinct rows,
        // and no entity was deleted or fabricated (count unchanged).
        int bothSurvive = jdbc.queryForObject(
                "SELECT count(*) FROM entity WHERE id IN (?,?)", Integer.class, a, b);
        assertThat(bothSurvive).as("both same-named entities must survive the merge (PP3)").isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM entity", Integer.class)).isEqualTo(entitiesBefore);

        // The merge is recorded as a canonical mapping (Flow F write-side; DEV-004).
        int mapped = jdbc.queryForObject(
                "SELECT count(*) FROM entity_canonical WHERE entity_id IN (?,?) AND canonical_entity_id=?",
                Integer.class, a, b, Math.min(a, b));
        assertThat(mapped).isEqualTo(2);
    }

    @Test
    void identity_dispute_routes_to_the_ER_queue_without_merging() throws Exception {
        String auth = "it-erq";
        importBatch("entities", "it-erq-src", entity(auth, "Q1") + "\n" + entity(auth, "Q2"));
        ResponseEntity<String> asserted = action("assert-claim", relClaim(auth, "Q1", "Q2", 0.4, "it-erq-src"));
        long claimId = om.readTree(asserted.getBody()).get("data").get("claimId").asLong();
        long a = lookup(auth, "Q1");
        long b = lookup(auth, "Q2");

        ResponseEntity<String> routed = action("dispute",
                "{\"claimId\":%d,\"ground\":\"identity\",\"reason\":\"might be the same person\"}".formatted(claimId));
        assertThat(routed.getStatusCode().value()).isEqualTo(200);
        assertThat(om.readTree(routed.getBody()).get("data").get("routedToEr").asBoolean()).isTrue();

        // W1: an identity dispute becomes an ER candidate; both entities still exist.
        int queued = jdbc.queryForObject(
                "SELECT count(*) FROM er_candidate WHERE entity_a=? AND entity_b=? AND state='queued'",
                Integer.class, Math.min(a, b), Math.max(a, b));
        assertThat(queued).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM entity WHERE id IN (?,?)", Integer.class, a, b))
                .isEqualTo(2);
    }

    // ---- the licence gate is a TEST, not a policy (HANDOFF rule 4, PP6) -------

    @Test
    void export_claims_is_refused_403_naming_the_unconfirmed_source() throws Exception {
        String src = "it-gate-src";   // upserted with license_confirmed=false (the safe default)
        importBatch("entities", src, entity("it-gate", "G1"));
        importBatch("claims", src, nameClaim("it-gate", "G1", "Gated Person", src));

        ResponseEntity<String> resp = rawGet("/api/v1/export/claims");
        assertThat(resp.getStatusCode().value()).isEqualTo(403);
        JsonNode pd = om.readTree(resp.getBody());
        assertThat(pd.get("detail").asText()).contains(src);
        boolean named = false;
        for (JsonNode s : pd.get("blockedSources")) {
            named = named || src.equals(s.asText());
        }
        assertThat(named).as("the 403 must name the unconfirmed source").isTrue();
    }

    // ---- I6: a dangling reference rejects its claim; no entity is fabricated --

    @Test
    void dangling_reference_is_rejected_and_no_entity_is_fabricated() throws Exception {
        String auth = "it-i6";
        importBatch("entities", "it-i6-src", entity(auth, "S"));   // only the subject exists
        int entitiesBefore = jdbc.queryForObject("SELECT count(*) FROM entity", Integer.class);

        // object endpoint it-i6:MISSING does not resolve.
        JsonNode report = importBatch("claims", "it-i6-src", relClaim(auth, "S", "MISSING", 0.5, "it-i6-src"));
        assertThat(report.get("inserted").asInt()).isEqualTo(0);
        assertThat(report.get("rejected")).isNotEmpty();
        assertThat(report.get("rejected").get(0).get("reason").asText().toLowerCase()).contains("unresolvable");

        // I6 — the assertion that matters: the entity count is IDENTICAL. The engine
        // never creates an entity as a side effect of an unresolved relation.
        int entitiesAfter = jdbc.queryForObject("SELECT count(*) FROM entity", Integer.class);
        assertThat(entitiesAfter).as("no entity may be fabricated for a dangling ref (I6)").isEqualTo(entitiesBefore);
    }
}
