# scripts/ — Audit pipeline

Three scripts that turn raw findings into a rendered, validated Markdown report.

```
scripts/
├── parse_audit.py     # Extract findings from legacy MEJORAS_ECC.md → findings.yaml
├── validate.py        # Schema + ID uniqueness + reference integrity check
├── render.py          # findings.yaml + coverage.yaml + stacks.yaml → docs/MEJORAS_ECC.md
└── pipeline.sh        # All three in sequence (with REPARSE=1 to force re-extraction)
```

## Source of truth

The source of truth is **`audits/findings.yaml`**. Each record has:

```yaml
- id: BACK-SEC-001                # <PROJECT>-<DIMENSION_ABBREV>-<SEQ>
  project: backend                # backend | frontend | database | cross | devops | sec
  dimension: seguridad            # see full list in validate.py
  severity: CRITICAL              # CRITICAL | HIGH | MEDIUM | LOW
  confidence: HIGH                # HIGH | MEDIUM | LOW
  title: "short label"
  location: ["path/file.ext:range"]
  description: "what the issue is"
  action: "what to do about it"
  blast_radius: security          # security | financial_fraud | data_loss | perf | financial | regression | dx
  discovered_by: security-reviewer
  discovered_at: 2026-09-05
  status: open                    # open | in_progress | done | wontfix | deferred
  owner: null
  depends_on: []                  # list of finding IDs
```

`audits/coverage.yaml` declares which files each agent deep-reviewed vs shallow vs not reviewed.
`audits/stacks.yaml` is the canonical stack/version table (referenced from §3 in the rendered .md).

## Adding a finding

1. Edit `audits/findings.yaml`, append a record with a fresh `id`.
2. (Optionally) reference it from another record's `depends_on` or `related`.
3. Run `bash scripts/pipeline.sh` (it will validate and regenerate).

## Changing severity or blast_radius

Same as above — edit the field, re-run pipeline. Counts in §0 and the backlog in §4 update automatically.

## Adding a new project

1. Append a record with `project: <name>` and a `PROJECT_DIM_TO_PREFIX` entry in `parse_audit.py` and a `PROJECT_LABEL` entry in `render.py`.
2. Add the project to `audits/coverage.yaml` and `audits/stacks.yaml`.
3. Re-run pipeline.

## Adding a new dimension

1. Add the dimension to the `DIMENSION` set in `validate.py`.
2. Add a label to `DIMENSION_LABEL` in `render.py`.
3. Re-run pipeline.

## CI

Wire `python3 scripts/validate.py audits/findings.yaml` to fail the build on errors
(only warnings should not fail). Add `python3 scripts/render.py --output docs/MEJORAS_ECC.md`
as a build step that fails if `docs/MEJORAS_ECC.md` would change.

## Idempotency

`pipeline.sh` is idempotent. Re-running produces a byte-identical `docs/MEJORAS_ECC.md`
unless `findings.yaml` changed.

## Versioning

The `discovered_at` and `discovered_by` fields capture provenance. Add a `changelog.yaml`
in `audits/` if you need to track when findings were added/removed/changed across sprints.