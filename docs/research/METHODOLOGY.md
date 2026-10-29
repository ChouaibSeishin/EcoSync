# Research methodology outline

Design dated 2026-10-29. No experiments or benchmark results have been produced. Implementation begins with synthetic fixtures in Phase 2, application execution in Phase 6, and the benchmark runner/reporting framework in Phase 9.

## Question, hypotheses, and systems

Question: to what extent does LLM orchestration through MCP reduce heterogeneous integration and maintenance effort, and what trade-offs arise in correctness, latency, reliability and security?

Pre-register these hypotheses before observing measured results: H1, adding a supported connection/schema change requires less core application modification under the MCP/registry approach; H2, grounded answer correctness and security enforcement can meet explicitly stated criteria despite dynamic tool selection; H3, model and protocol orchestration add measurable latency/resource overhead. Treat failure to support any hypothesis as a valid result. Do not assume MCP solves semantic interoperability.

Compare A: Ecosync's LLM + MCP + approved mappings with B: a conventional deterministic service with explicitly coded predefined operations and source adapters. Both use the same independent databases/API fixtures, scopes, time ranges, mapping versions and result schema. B accepts the benchmark's structured intent as its predefined operation; natural-language coverage is reported separately, since deterministic operation invocation and natural-language understanding are different capabilities. Do not count unsupported intents as successful answers.

An optional C: LLM orchestration with direct connector adapters and the same policy/mappings isolates some MCP transport/catalog overhead. Without C, observed A/B differences concern the combined architecture; they cannot be attributed causally to MCP alone. Record this confound in conclusions. Do not weaken safety policies or secretly optimize fixture access for one arm.

## Versioned fixtures and cases

Use independent sales PostgreSQL, inventory MySQL and supplier REST fixtures with synthetic records only. Document product_id, sku and supplier identifiers, one-to-many/duplicate/missing links, nonmatching IDs, date boundaries, nulls and explicit units/currency. Each fixture has an immutable version/hash, seed, schema contract and independently computed expected results. Full expected aggregates must not be computed from silently truncated API pages.

Initial target: at least 30 cases with at least three examples in each category below, multiple paraphrases kept separate from underlying intents, and a held-out group for changes to connector/schema configuration. This is a planned dataset size, not a completed dataset.

| Category | Expected evidence |
| --- | --- |
| Single-source retrieval | Exact permitted rows/filters and source IDs |
| Two-source questions | Correct explicit join and both source references |
| Three-or-more-source questions | Sales/inventory/supplier coverage with full lineage |
| Matching/mismatching identifiers | Correct mapping or explicit unresolved relationship, no invented equivalence |
| Ambiguous questions | Clarification or explicit bounded interpretation |
| Unauthorized requests | Backend/gateway deny with zero forbidden source operations; natural-language refusal alone is insufficient |
| Unavailable/invalid-credential source | Correct failure/partial status with no concealed source failure |
| Malformed/oversized responses | Enforced bounds and structured error rather than crashes or secret leakage |
| Partial results | Valid facts preserved with limitations and incomplete totals clearly identified |
| Complexity and result size | Controlled scaling, complete pagination where allowed, explicit limit/timeout outcomes |

Fault injection uses controlled infrastructure failures and synthetic hostile content, never real secret exfiltration targets. Keep model-tuning cases separate from held-out evaluation; freeze prompts/config before scored runs.

## Measurements and scoring

| Metric | Definition / denominator |
| --- | --- |
| Answer correctness | Exact factual/numeric/schema checks against independent expected output; report fully correct cases / all scored cases plus per-fact accuracy. Decimal/unit/time tolerances are specified per case before runs. |
| Tool selection | Precision/recall against required allowed operation sets, with declared equivalent plans; attempts to invoke forbidden operations counted separately. |
| Tool success | Successful valid tool calls / attempted calls, split by expected fault, infrastructure, authorization and model argument errors. |
| Mapping/calculation correctness | Correct expected joins, unmatched identifier reporting and deterministic arithmetic / relevant assertions; do not let fluent explanations override wrong totals. |
| Latency | Monotonic end-to-end and per-stage/call timings, p50/p95 and timeout counts; distinguish source, model and mapping latency; retain failures rather than censor slow cases. |
| Reliability/partial behavior | Correct classified outcome and preserved verified facts / fault cases, recovery time and retry counts. |
| Integration effort | Time and changed core/adaptor/config files for standardized onboarding/schema-change tasks; record developer familiarity, manual review, mapping/auth setup and debugging; config effort is not zero effort. |
| Resources/provider use | CPU/memory/time and tokens/cost only where measured/reported; unknown cost remains unknown; no guessed prices. |
| Policy enforcement | Forbidden source calls blocked / forbidden attempts; also count allowed attempts falsely denied; sentinel secrets absent from logs/model-visible evidence. |

Report exact case/run counts, not percentages without denominators. For correctness/denial proportions use Wilson intervals; for paired performance differences use an appropriate paired bootstrap with stated seed and interval. Low sample size, repeated paraphrases and correlated cases limit generalization. Predeclare any comparison margin before scored runs; avoid selecting one after seeing favorable results. Human review of ambiguity/explanation quality is rubric-based and separate from machine-checked factual correctness; record assessor disagreement.

## Reproducibility protocol

1. Freeze dataset, source images/schemas, mappings, access policy and application/baseline commits. Prepare ground truth independently and review it.
2. Record environment/tool versions, CPU/RAM, cluster/container resources, model provider/model artifact and digest or provider revision, sampling settings, prompt version, context/token budgets and all unavailable settings.
3. Run unscored setup/warm-up. Reset synthetic fixture state through an explicit test-only procedure, preserving real/persistent user data. Declare cold/warm cache conditions.
4. Run at least five paired repetitions per case/arm initially; randomize arm/case order with a stored seed. Increase repetitions based on variance using a predeclared rule. Real-model nondeterminism remains even with deterministic requested settings.
5. Save immutable run manifests and sanitized case observations: expected/actual structure, authorized tools, selected calls, outcomes, provenance and timings. Keep deterministic CI stub results distinct from real-model experiments.
6. Report every attempted scored case, including timeouts and failed model/service calls. Label reruns and exclusions with reasons; never replace failures silently. Publish summary plus enough artifacts to reproduce conclusions without releasing secrets or raw confidential source data.

Validity limits include synthetic dataset bias, shared connector reuse, model nondeterminism/version drift, developer learning effects, baseline natural-language differences, hardware/provider rate limits and limited tenant realism. Neither successful demonstrations nor lower line counts establish scientific benefit on their own.
