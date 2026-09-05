#!/usr/bin/env python3
"""
validate.py — Validate audits/findings.yaml schema and cross-references.

Checks:
  1. Required fields present on every record.
  2. `id` unique across all records.
  3. `severity` ∈ {CRITICAL, HIGH, MEDIUM, LOW}.
  4. `project` ∈ {backend, frontend, database, cross, devops, sec}.
  5. `dimension` ∈ known set.
  6. `blast_radius` ∈ known set.
  7. `status` ∈ known set.
  8. `depends_on` IDs all exist in the dataset.
  9. `action` field is non-empty (warns if placeholder).
 10. `location` non-empty.
 11. No duplicate findings (same id).

Usage:
  python3 scripts/validate.py audits/findings.yaml
Exit code: 0 if clean, 1 if any ERROR.
"""
from __future__ import annotations

import argparse
import sys
from collections import Counter
from pathlib import Path

import yaml


SEVERITY = {"CRITICAL", "HIGH", "MEDIUM", "LOW"}
PROJECT = {"backend", "frontend", "database", "cross", "devops", "sec"}
DIMENSION = {
    "rendimiento", "seguridad", "diseno", "estabilidad",
    "escalabilidad", "accesibilidad", "ui_ux", "mantenibilidad",
}
BLAST = {
    "security", "financial_fraud", "data_loss", "perf",
    "financial", "regression", "dx",
}
STATUS = {"open", "in_progress", "done", "wontfix", "deferred"}
CONFIDENCE = {"HIGH", "MEDIUM", "LOW"}

REQUIRED = ["id", "project", "dimension", "severity", "title", "location", "description", "action", "blast_radius", "discovered_by", "discovered_at", "status"]


def load(path: Path) -> list[dict]:
    text = path.read_text(encoding="utf-8")
    lines = text.splitlines()
    lines = [ln for ln in lines if not ln.startswith("#")]
    if lines and lines[0].strip() == "---":
        lines = lines[1:]
    return yaml.safe_load("\n".join(lines))


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("findings", type=Path)
    args = p.parse_args()

    records = load(args.findings)
    errors: list[str] = []
    warnings: list[str] = []

    # 1. Required fields + enums
    for i, rec in enumerate(records):
        loc = f"record[{i}] (id={rec.get('id', '?')})"
        for f in REQUIRED:
            if f not in rec or rec[f] in (None, "", [], {}):
                errors.append(f"{loc}: missing required field `{f}`")
        if rec.get("severity") not in SEVERITY:
            errors.append(f"{loc}: bad severity `{rec.get('severity')}`")
        if rec.get("project") not in PROJECT:
            errors.append(f"{loc}: bad project `{rec.get('project')}`")
        if rec.get("dimension") not in DIMENSION:
            errors.append(f"{loc}: bad dimension `{rec.get('dimension')}`")
        if rec.get("blast_radius") not in BLAST:
            errors.append(f"{loc}: bad blast_radius `{rec.get('blast_radius')}`")
        if rec.get("status") not in STATUS:
            errors.append(f"{loc}: bad status `{rec.get('status')}`")
        if rec.get("confidence") and rec["confidence"] not in CONFIDENCE:
            errors.append(f"{loc}: bad confidence `{rec['confidence']}`")
        if rec.get("location") and not isinstance(rec["location"], list):
            errors.append(f"{loc}: location must be a list")

    # 2. Unique IDs
    ids = Counter(r.get("id", "?") for r in records)
    dups = [k for k, v in ids.items() if v > 1]
    if dups:
        errors.append(f"duplicate IDs: {dups}")

    # 3. depends_on resolves
    id_set = set(ids.keys())
    for rec in records:
        for dep in rec.get("depends_on", []) or []:
            if dep not in id_set:
                errors.append(f"record id={rec.get('id')}: depends_on `{dep}` not found")

    # 4. Action placeholders
    for rec in records:
        action = (rec.get("action") or "").strip()
        if action == "(definir acción concreta)" or not action:
            warnings.append(f"record id={rec.get('id')}: action is empty/placeholder")

    # 5. Summary
    by_sev = Counter(r["severity"] for r in records)
    by_proj = Counter(r["project"] for r in records)
    print(f"Validated {len(records)} findings in {args.findings}")
    print(f"  by severity: {dict(by_sev)}")
    print(f"  by project:  {dict(by_proj)}")
    print(f"  errors:   {len(errors)}")
    print(f"  warnings: {len(warnings)}")
    if errors:
        print("\nERRORS:")
        for e in errors[:50]:
            print(f"  - {e}")
        if len(errors) > 50:
            print(f"  ... and {len(errors) - 50} more")
    if warnings:
        print("\nWARNINGS (first 30):")
        for w in warnings[:30]:
            print(f"  - {w}")
        if len(warnings) > 30:
            print(f"  ... and {len(warnings) - 30} more")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())