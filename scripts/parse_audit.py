#!/usr/bin/env python3
"""
parse_audit.py — Extract findings from MEJORAS_ECC.md into findings.yaml.

Reads the legacy markdown audit and produces a structured YAML store where every
finding has a stable ID, a blast_radius, and discoverable provenance.

Usage:
    python3 scripts/parse_audit.py MEJORAS_ECC.md audits/findings.yaml

Output schema (per record):
    id:              <PROJECT>-<DIM>-<SEQ>      (e.g. BACK-SEC-001)
    project:         backend | frontend | database | cross | devops | sec
    dimension:       rendimiento | seguridad | diseno | estabilidad |
                     escalabilidad | accesibilidad | ui_ux | mantenibilidad
    severity:        CRITICAL | HIGH | MEDIUM | LOW
    confidence:      HIGH | MEDIUM | LOW
    title:           short label
    location:        list of file:line references
    description:     one-line summary
    action:          recommended fix (best-effort extraction)
    blast_radius:    security | data_loss | financial | financial_fraud |
                    perf | regression | dx
    discovered_by:   agent name
    discovered_at:   ISO date
    status:          open
    owner:           null
    depends_on:      list of finding IDs
    cwe:             (optional) CWE id
    owasp:           (optional) OWASP id
    related:         (optional) list of related finding IDs
"""
from __future__ import annotations

import argparse
import re
import sys
from collections import OrderedDict, defaultdict
from datetime import date
from pathlib import Path

try:
    import yaml
except ImportError:
    sys.exit("PyYAML required: pip install pyyaml")


# ----------------------------------------------------------------------------
# Section mapping: which (project, dimension) a §X.Y heading corresponds to.
# ----------------------------------------------------------------------------
SECTION_MAP: dict[str, tuple[str, str]] = {
    # §2 Backend
    "2.1": ("backend", "rendimiento"),
    "2.2": ("backend", "seguridad"),
    "2.3": ("backend", "diseno"),
    "2.4": ("backend", "estabilidad"),
    "2.5": ("backend", "escalabilidad"),
    "2.6": ("backend", "accesibilidad"),
    "2.7": ("backend", "ui_ux"),
    "2.8": ("backend", "mantenibilidad"),
    # §3 Frontend
    "3.1": ("frontend", "rendimiento"),
    "3.2": ("frontend", "seguridad"),
    "3.3": ("frontend", "diseno"),
    "3.4": ("frontend", "estabilidad"),
    "3.5": ("frontend", "escalabilidad"),
    "3.6": ("frontend", "accesibilidad"),
    "3.7": ("frontend", "ui_ux"),
    "3.8": ("frontend", "mantenibilidad"),
    # §4 Database
    "4.1": ("database", "rendimiento"),
    "4.2": ("database", "seguridad"),
    "4.3": ("database", "diseno"),
    "4.4": ("database", "estabilidad"),
    "4.5": ("database", "escalabilidad"),
    "4.6": ("database", "accesibilidad"),
    "4.7": ("database", "ui_ux"),
    "4.8": ("database", "mantenibilidad"),
}

PROJECT_DIM_TO_PREFIX = {
    ("backend", "rendimiento"): "BACK-REND",
    ("backend", "seguridad"): "BACK-SEC",
    ("backend", "diseno"): "BACK-DIS",
    ("backend", "estabilidad"): "BACK-EST",
    ("backend", "escalabilidad"): "BACK-ESC",
    ("backend", "accesibilidad"): "BACK-ACC",
    ("backend", "ui_ux"): "BACK-UI",
    ("backend", "mantenibilidad"): "BACK-MAN",
    ("frontend", "rendimiento"): "FRONT-REND",
    ("frontend", "seguridad"): "FRONT-SEC",
    ("frontend", "diseno"): "FRONT-DIS",
    ("frontend", "estabilidad"): "FRONT-EST",
    ("frontend", "escalabilidad"): "FRONT-ESC",
    ("frontend", "accesibilidad"): "FRONT-ACC",
    ("frontend", "ui_ux"): "FRONT-UI",
    ("frontend", "mantenibilidad"): "FRONT-MAN",
    ("database", "rendimiento"): "DB-REND",
    ("database", "seguridad"): "DB-SEC",
    ("database", "diseno"): "DB-DIS",
    ("database", "estabilidad"): "DB-EST",
    ("database", "escalabilidad"): "DB-ESC",
    ("database", "accesibilidad"): "DB-ACC",
    ("database", "ui_ux"): "DB-UI",
    ("database", "mantenibilidad"): "DB-MAN",
    ("cross", "seguridad"): "CROSS-SEC",
    ("cross", "rendimiento"): "CROSS-REND",
}


# ----------------------------------------------------------------------------
# Severity tag patterns.
# ----------------------------------------------------------------------------
SEV_TAG_RE = re.compile(r"\[(CRITICAL|HIGH|MEDIUM|LOW)\]")
SEV_HEADING_RE = re.compile(r"^####\s+(CRITICAL|HIGH|MEDIUM|LOW)(?:\s*/\s*\w+)?\s*$")
# `path:line` or `path:start-end` location patterns
LOC_RE = re.compile(r"`?([\w./\-]+(?:\.(?:java|sql|yml|yaml|ts|tsx|js|jsx|kt|kts|gradle|json|md|conf|ini|properties|sh|ps1))):([\d\-,\s]+)`?")
# Heading like `### 2.1 Rendimiento`
HEADING_RE = re.compile(r"^###\s+(\d+\.\d+)\s+(.+?)\s*$")
# Section divider like `## 2. ferreteriaBackend`
TOP_HEADING_RE = re.compile(r"^##\s+(\d+)\.\s+(.+?)\s*$")


def blast_radius_for(project: str, dimension: str, severity: str, title: str, description: str) -> str:
    """Heuristic blast_radius assignment."""
    blob = (title + " " + description).lower()
    if dimension == "seguridad":
        if "secr" in blob or "password" in blob or "jwt" in blob or "default" in blob and "admin" in blob:
            return "security"
        if "@preauthorize" in blob or "broken access" in blob or "idor" in blob or "authz" in blob:
            return "security"
        if "csrf" in blob or "xss" in blob or "injection" in blob or "ssrf" in blob:
            return "security"
        if "sat" in blob or "factur" in blob or "folio" in blob or "configuracion" in blob:
            return "financial_fraud"
        return "security"
    if "partition" in blob or "auditoria" in blob and "sin" in blob:
        return "data_loss"
    if "n+1" in blob or "batch_fetch" in blob or "indice" in blob or "idx_" in blob:
        return "perf"
    if "test" in blob and severity == "CRITICAL":
        return "regression"
    if "otel" in blob or "prometheus" in blob or "sampling" in blob or "cardinality" in blob:
        return "perf"
    if "dockerfile" in blob or "k8s" in blob or "deployment" in blob:
        return "dx"
    return "perf" if dimension in ("rendimiento", "escalabilidad") else "dx"


def discovered_by_for(project: str, section_id: str) -> str:
    if project == "backend":
        if "2.2" == section_id:
            return "security-reviewer"
        return "java-reviewer"
    if project == "frontend":
        return "general-react-expert"
    if project == "database":
        return "database-reviewer"
    return "manual"


def normalize_description(text: str, max_len: int = 240) -> str:
    text = re.sub(r"\s+", " ", text).strip()
    if len(text) > max_len:
        text = text[: max_len - 1].rstrip() + "…"
    return text


def extract_action(text: str) -> tuple[str, str]:
    """Try to split out 'Acción: ...' from a bullet; return (rest, action)."""
    m = re.search(r"\*\*Acción\*\*:?\s*(.+?)(?:\.\s|$)", text)
    if m:
        action = m.group(1).strip().rstrip(".")
        rest = (text[: m.start()] + text[m.end() :]).strip(" .")
        return rest, action
    return text, ""


def parse_markdown(md_path: Path) -> list[dict]:
    """Walk the markdown, track section context, extract findings."""
    findings: list[dict] = []
    counters: dict[tuple[str, str], int] = defaultdict(int)
    current_top_section: str | None = None  # "1" .. "9" — the "## X." heading
    current_project: str | None = None
    current_dimension: str | None = None
    current_severity: str | None = None  # last `#### CRITICAL|HIGH|MEDIUM|LOW` heading

    # Top-level section to project mapping
    TOP_TO_PROJECT = {
        "2": "backend",
        "3": "frontend",
        "4": "database",
        "5": "cross",  # cross-cutting
    }

    for raw_line in md_path.read_text(encoding="utf-8").splitlines():
        # First check `## X. ...` (top-level)
        top = TOP_HEADING_RE.match(raw_line)
        if top:
            current_top_section = top.group(1)
            current_project = TOP_TO_PROJECT.get(current_top_section)
            current_dimension = None
            current_severity = None
            continue
        # Then check `### X.Y ...` (subsection)
        h = HEADING_RE.match(raw_line)
        if h:
            sub_id = h.group(1)
            if sub_id in SECTION_MAP:
                current_project, current_dimension = SECTION_MAP[sub_id]
            current_severity = None  # reset severity when changing sub-section
            continue
        # Then check `#### CRITICAL|HIGH|MEDIUM|LOW` (severity grouping)
        sh = SEV_HEADING_RE.match(raw_line)
        if sh:
            current_severity = sh.group(1)
            continue
        # Section divider like `## 6. Backlog priorizado` etc.
        if raw_line.startswith("## ") or raw_line.startswith("# "):
            continue
        if not raw_line.startswith("- ") and not raw_line.startswith("* "):
            continue
        if not current_project or not current_dimension:
            continue

        # Severity: prefer inline tag, else fall back to current #### heading
        sev_match = SEV_TAG_RE.search(raw_line)
        severity = sev_match.group(1) if sev_match else current_severity
        if not severity:
            continue

        body = raw_line[2:].strip()
        if sev_match:
            body = SEV_TAG_RE.sub("", body, count=1).strip()
        # Strip leading/trailing backticks and any leading legacy cross-refs.
        # Legacy cross-refs look like: `[Backend §1 #6]`, `[CR §1 #1]`, `[S-04]`, `[S-05/S-29]`.
        for _ in range(4):
            new_body = body.lstrip(" `")
            new_body = re.sub(r"^\[[^\]]*§[^\]]*\]\s*", "", new_body)
            new_body = re.sub(r"^\[[A-Z][\w/-]*\]\s*", "", new_body)
            if new_body == body:
                break
            body = new_body
        locs: list[str] = []
        for m in LOC_RE.finditer(body):
            locs.append(f"{m.group(1)}:{m.group(2).strip()}")
        if not locs:
            continue

        clean = LOC_RE.sub("", body)
        clean = clean.replace("`", "").strip(" —-")

        rest, action = extract_action(clean)
        description = normalize_description(rest)
        if not description:
            continue

        title = description.split(".")[0][:80].strip()
        counters[(current_project, current_dimension)] += 1
        seq = counters[(current_project, current_dimension)]
        prefix = PROJECT_DIM_TO_PREFIX.get((current_project, current_dimension))
        fid = f"{prefix}-{seq:03d}" if prefix else f"{current_project.upper()}-{current_dimension.upper()}-{seq:03d}"

        blast = blast_radius_for(
            current_project, current_dimension, severity, title, description
        )

        rec = OrderedDict()
        rec["id"] = fid
        rec["project"] = current_project
        rec["dimension"] = current_dimension
        rec["severity"] = severity
        rec["confidence"] = "HIGH"
        rec["title"] = title or description[:60]
        rec["location"] = locs[:3]
        rec["description"] = description
        rec["action"] = action or "(definir acción concreta)"
        rec["blast_radius"] = blast
        rec["discovered_by"] = discovered_by_for(current_project, current_top_section or "")
        rec["discovered_at"] = str(date(2026, 9, 5))
        rec["status"] = "open"
        rec["owner"] = None
        rec["depends_on"] = []
        findings.append(rec)

    return findings


def write_yaml(records: list[dict], out: Path) -> None:
    out.parent.mkdir(parents=True, exist_ok=True)
    header = (
        "# findings.yaml — Single source of truth for MEJORAS_ECC.md\n"
        "# Generated by scripts/parse_audit.py from MEJORAS_ECC.md.\n"
        "# DO NOT hand-edit unless you also re-run the parser/validator/renderer.\n"
        f"# Total findings: {len(records)}\n"
        "# Schema: see scripts/parse_audit.py docstring.\n"
        "---\n"
    )
    body = yaml.safe_dump(
        [dict(r) for r in records],
        allow_unicode=True,
        sort_keys=False,
        default_flow_style=False,
        width=120,
    )
    out.write_text(header + body, encoding="utf-8")


def main() -> None:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("input_md", type=Path)
    p.add_argument("output_yaml", type=Path)
    args = p.parse_args()
    records = parse_markdown(args.input_md)
    write_yaml(records, args.output_yaml)
    print(f"Parsed {len(records)} findings → {args.output_yaml}")
    by_sev: dict[str, int] = defaultdict(int)
    by_proj: dict[str, int] = defaultdict(int)
    for r in records:
        by_sev[r["severity"]] += 1
        by_proj[r["project"]] += 1
    print(f"  by severity: {dict(by_sev)}")
    print(f"  by project:  {dict(by_proj)}")


if __name__ == "__main__":
    main()