package dev.palimpsest.engine.importer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.palimpsest.engine.api.Dtos;
import dev.palimpsest.engine.importer.ImportModels.ParsedClaim;
import dev.palimpsest.engine.importer.ImportModels.ParsedEntity;
import dev.palimpsest.engine.importer.ImportModels.Support;
import dev.palimpsest.engine.store.ImportStore;
import dev.palimpsest.engine.store.ImportStore.RecordResult;
import dev.palimpsest.engine.store.ImportStore.RecordStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The bulk import path (Flow A). The engine is the sole write authority (ADR-004):
 * every line is re-validated server-side against the frozen schema, entity refs
 * are resolved (never fabricated — I6), and each claim is written with its support,
 * assert event, status row, effective-confidence row and outbox row in ONE
 * transaction per batch (I2/I3/I4). Per-line rejects are collected without
 * aborting the batch; a genuine DB error rolls the whole batch back (atomicity).
 */
@Service
public class ImportService {

    public enum Kind { ENTITIES, CLAIMS }

    private final InterchangeValidator validator;
    private final ImportStore store;
    private final ObjectMapper om;

    public ImportService(InterchangeValidator validator, ImportStore store, ObjectMapper om) {
        this.validator = validator;
        this.store = store;
        this.om = om;
    }

    @Transactional
    public Dtos.ImportReportDto importBatch(Kind kind, List<String> lines, String runId,
                                            String sourceSlug, long agentId, int batchNo) {
        store.ensureImportRun(runId, sourceSlug);
        List<Dtos.RejectDto> rejects = new ArrayList<>();
        int inserted = 0, duplicates = 0, superseded = 0, received = 0;

        for (int idx = 0; idx < lines.size(); idx++) {
            String line = lines.get(idx).trim();
            if (line.isEmpty()) {
                continue;
            }
            received++;
            int lineNo = idx + 1;
            JsonNode node;
            try {
                node = om.readTree(line);
            } catch (Exception e) {
                rejects.add(new Dtos.RejectDto(lineNo, "invalid JSON: " + e.getMessage()));
                continue;
            }
            try {
                if (kind == Kind.ENTITIES) {
                    int r = importEntity(node, lineNo, rejects);
                    if (r == 1) inserted++;
                    else if (r == 0) duplicates++;
                } else {
                    int[] r = importClaim(node, lineNo, agentId, runId, rejects);
                    inserted += r[0];
                    duplicates += r[1];
                    superseded += r[2];
                }
            } catch (RejectException re) {
                rejects.add(new Dtos.RejectDto(lineNo, re.getMessage()));
            }
        }

        store.tallyImportRun(runId, received, inserted, duplicates, superseded, rejects.size());
        return new Dtos.ImportReportDto(runId, batchNo, received, inserted, duplicates, superseded, rejects);
    }

    /** @return 1 inserted, 0 duplicate. */
    private int importEntity(JsonNode node, int lineNo, List<Dtos.RejectDto> rejects) {
        InterchangeValidator.Result v = validator.validateEntity(node);
        if (!v.valid()) {
            rejects.add(new Dtos.RejectDto(lineNo, "schema: " + v.message()));
            return -1;
        }
        ParsedEntity e = ParsedEntity.from(node);
        if (store.resolveEntity(e.ref()).isPresent()) {
            return 0;
        }
        store.createEntity(e.entityType(), e.ref(), e.externalIds());
        return 1;
    }

    /** @return [inserted, duplicates, superseded]. */
    private int[] importClaim(JsonNode node, int lineNo, long agentId, String runId, List<Dtos.RejectDto> rejects) {
        InterchangeValidator.Result v = validator.validateClaim(node);
        if (!v.valid()) {
            rejects.add(new Dtos.RejectDto(lineNo, "schema: " + v.message()));
            return new int[]{0, 0, 0};
        }
        ParsedClaim c = ParsedClaim.from(node);

        // Predicate must be a registered relation type (no silent vocabulary creation).
        if (!store.relationTypeExists(c.predicate())) {
            rejects.add(new Dtos.RejectDto(lineNo, "unknown predicate '" + c.predicate() + "'"));
            return new int[]{0, 0, 0};
        }

        // Resolve refs — an unresolvable ref rejects the claim (I6); no fabrication.
        Optional<Long> subjectId = store.resolveEntity(c.subject());
        if (subjectId.isEmpty()) {
            rejects.add(new Dtos.RejectDto(lineNo, "unresolvable subject ref "
                    + c.subject().authority() + ":" + c.subject().id()));
            return new int[]{0, 0, 0};
        }
        Long objectEntityId = null;
        if (c.objectEntity() != null) {
            Optional<Long> oid = store.resolveEntity(c.objectEntity());
            if (oid.isEmpty()) {
                rejects.add(new Dtos.RejectDto(lineNo, "unresolvable object ref "
                        + c.objectEntity().authority() + ":" + c.objectEntity().id()));
                return new int[]{0, 0, 0};
            }
            objectEntityId = oid.get();
        }

        // Upsert support source records (I4: at least one).
        List<Long> recordIds = new ArrayList<>();
        int superseded = 0;
        for (Support sp : c.support()) {
            long sourceId = store.upsertSource(sp.source());
            RecordResult rr = store.upsertSourceRecord(sourceId, sp);
            recordIds.add(rr.id());
            if (rr.status() == RecordStatus.SUPERSEDED) {
                superseded++;
            }
        }

        // Idempotency keyed on the primary support record + claim identity.
        long primaryRecord = recordIds.get(0);
        if (store.findExistingClaim(primaryRecord, c, subjectId.get(), objectEntityId).isPresent()) {
            return new int[]{0, 1, superseded};
        }

        long claimId = store.insertClaim(c, subjectId.get(), objectEntityId, agentId, runId);
        for (Long rid : recordIds) {
            store.addSupport(claimId, rid);
        }
        store.writeConfidenceCurrent(claimId, c);
        store.assertClaim(claimId, agentId);   // event + status + outbox (I2/I3)
        return new int[]{1, 0, superseded};
    }

    /**
     * Create a single claim through the same invariant path as import (assert-claim,
     * supersede replacement — WP6). Resolves refs (I6), upserts support (I4), writes
     * claim + support + confidence + assert event + status + outbox (I2/I3).
     * Throws ApiException on an unresolvable ref or unknown predicate.
     */
    @Transactional
    public long createClaim(ParsedClaim c, long agentId, String runId) {
        if (!store.relationTypeExists(c.predicate())) {
            throw dev.palimpsest.engine.api.ApiException.badRequest("unknown predicate '" + c.predicate() + "'");
        }
        Long subjectId = store.resolveEntity(c.subject())
                .orElseThrow(() -> dev.palimpsest.engine.api.ApiException.badRequest(
                        "unresolvable subject ref " + c.subject().authority() + ":" + c.subject().id()));
        Long objectEntityId = null;
        if (c.objectEntity() != null) {
            objectEntityId = store.resolveEntity(c.objectEntity())
                    .orElseThrow(() -> dev.palimpsest.engine.api.ApiException.badRequest(
                            "unresolvable object ref " + c.objectEntity().authority() + ":" + c.objectEntity().id()));
        }
        List<Long> recordIds = new ArrayList<>();
        for (Support sp : c.support()) {
            long sourceId = store.upsertSource(sp.source());
            recordIds.add(store.upsertSourceRecord(sourceId, sp).id());
        }
        long claimId = store.insertClaim(c, subjectId, objectEntityId, agentId, runId);
        for (Long rid : recordIds) {
            store.addSupport(claimId, rid);
        }
        store.writeConfidenceCurrent(claimId, c);
        store.assertClaim(claimId, agentId);
        return claimId;
    }

    public ParsedClaim parseClaim(JsonNode node) {
        InterchangeValidator.Result v = validator.validateClaim(node);
        if (!v.valid()) {
            throw dev.palimpsest.engine.api.ApiException.badRequest("schema: " + v.message());
        }
        return ParsedClaim.from(node);
    }

    private static final class RejectException extends RuntimeException {
        RejectException(String m) {
            super(m);
        }
    }
}
