package dev.palimpsest.engine.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * API DTOs (camelCase JSON), matching docs/BUILD-CONTRACT.md §3. Nested public
 * records keep the wire contract in one readable place. The generated OpenAPI +
 * TypeScript SDK are the downstream source of truth for the explorer.
 */
public final class Dtos {

    private Dtos() {
    }

    // ---- envelope ----------------------------------------------------------
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Envelope<T>(T data, Meta meta) {
        public static <T> Envelope<T> of(T data, Meta meta) {
            return new Envelope<>(data, meta);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Meta(String requestId, Integer count, Boolean truncated,
                       NetworkCounts counts, Page page, String asOfSystem,
                       String statusFilter) {
        public static Meta req(String requestId) {
            return new Meta(requestId, null, null, null, null, null, null);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Page(String cursor, int limit, String nextCursor) {
    }

    /** Q-3: possibly / certainly / undated counts in one response. */
    public record NetworkCounts(int possibly, int certainly, int undated) {
    }

    // ---- core shapes -------------------------------------------------------
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LifeDates(String bornEarliest, String bornLatest,
                            String diedEarliest, String diedLatest) {
    }

    public record EntitySummaryDto(long id, String displayName, String entityType,
                                   String description, String gender, LifeDates lifeDates,
                                   int degreeScored, int degreeUnscored) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FuzzyIntervalDto(String startEarliest, String startLatest,
                                   String endEarliest, String endLatest,
                                   boolean approximate, Object original) {
    }

    /** Effective confidence resolved by I7. effective=null ⇒ unscored (never 0). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ConfidenceDto(Double effective, String origin, Object raw,
                                boolean calibrated, boolean scored, String band) {
    }

    public record EdgeDto(long claimId, EntitySummaryDto counterpart, String predicate,
                          ConfidenceDto confidence, FuzzyIntervalDto validTime,
                          boolean scored, boolean certainlyActive, boolean undated,
                          String status) {
    }

    public record NetworkDto(EntitySummaryDto focus, List<EdgeDto> edges) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LiteralDto(String kind, Object value, String authority) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ObjectDto(EntitySummaryDto entity, LiteralDto literal) {
    }

    public record AgentDto(String slug, String kind, String displayName) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ClaimDetailDto(long id, EntitySummaryDto subject, String predicate,
                                 ObjectDto object, FuzzyIntervalDto validTime,
                                 ConfidenceDto confidence, String method, Object methodDetail,
                                 String status, AgentDto assertedBy, String recordedAt,
                                 String importRunId) {
    }

    public record SourceDto(String slug, String title, String version, String retrievalUri,
                            String license, boolean licenseConfirmed) {
    }

    public record SourceRecordDto(String recordKind, String externalId, String contentHash, Object raw) {
    }

    public record SupportDto(SourceDto source, SourceRecordDto record) {
    }

    public record EvidenceDto(ClaimDetailDto claim, List<SupportDto> support) {
    }

    // ---- entity view -------------------------------------------------------
    public record ExternalIdDto(String authority, String externalId) {
    }

    public record AttributeGroupDto(String predicate, List<ClaimDetailDto> claims) {
    }

    public record CoverageSourceDto(String slug, int relationshipClaims, int attributeClaims) {
    }

    public record CoverageDto(List<CoverageSourceDto> bySource, int scored, int unscored, String calibration) {
    }

    public record EntityViewDto(EntitySummaryDto summary, List<ExternalIdDto> externalIds,
                                List<AttributeGroupDto> attributeClaims, CoverageDto coverage) {
    }

    public record PairDossierDto(EntitySummaryDto a, EntitySummaryDto b, List<ClaimDetailDto> claims) {
    }

    // ---- history / events --------------------------------------------------
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EventDto(long id, long claimId, String eventType, AgentDto actor,
                           String occurredAt, Object payload) {
    }

    public record HistoryDto(List<EventDto> events) {
    }

    // ---- meta / stats ------------------------------------------------------
    public record RelationTypeDto(String slug, String label, String category,
                                  String rangeKind, boolean symmetric) {
    }

    public record RunDto(String runId, String sourceSlug, String startedAt, String finishedAt,
                         int batches, int received, int inserted, int duplicates,
                         int superseded, int rejected, Object manifest) {
    }

    public record HistogramBinDto(String band, int count) {
    }

    public record StatsDto(Map<String, Object> entities, Map<String, Object> claims,
                           int sourceRecords, List<SourceDto> sources,
                           List<HistogramBinDto> confidenceHistogram,
                           Map<String, Object> anomalyCounters,
                           Map<String, Integer> gender,
                           Map<String, Object> temporalCodeShare,
                           Double noRelationshipPct) {
    }

    // ---- import ------------------------------------------------------------
    public record RejectDto(int line, String reason) {
    }

    public record ImportReportDto(String run, int batch, int received, int inserted,
                                  int duplicates, int superseded, List<RejectDto> rejected) {
    }
}
