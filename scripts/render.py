#!/usr/bin/env python3
"""
render.py — Generate docs/MEJORAS_ECC.md from audits/findings.yaml + coverage + stacks.

This is a *renderer*, not the source of truth. The source is findings.yaml.
To add/change findings, edit findings.yaml (or re-run parse_audit.py) and re-run
this script.

Usage:
    python3 scripts/render.py \
        --findings audits/findings.yaml \
        --coverage audits/coverage.yaml \
        --stacks audits/stacks.yaml \
        --output docs/MEJORAS_ECC.md
"""
from __future__ import annotations

import argparse
import re
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable

import yaml


# ----------------------------------------------------------------------------
# Constants
# ----------------------------------------------------------------------------
SEVERITY_ORDER = ["CRITICAL", "HIGH", "MEDIUM", "LOW"]
SEVERITY_RANK = {s: i for i, s in enumerate(SEVERITY_ORDER)}

PROJECT_LABEL = {
    "backend": "ferreteriaBackend",
    "frontend": "ferreteriaFront",
    "database": "ferreteriaDB",
}
PROJECT_INDEX = {p: i for i, p in enumerate(PROJECT_LABEL.keys())}

DIMENSION_LABEL = {
    "rendimiento": "Rendimiento",
    "seguridad": "Seguridad",
    "diseno": "Diseño",
    "estabilidad": "Estabilidad",
    "escalabilidad": "Escalabilidad",
    "accesibilidad": "Accesibilidad",
    "ui_ux": "UI/UX",
    "mantenibilidad": "Mantenibilidad",
}
DIMENSION_INDEX = {d: i for i, d in enumerate(DIMENSION_LABEL.keys())}

BLAST_LABEL = {
    "security": "Seguridad",
    "financial_fraud": "Fraude financiero",
    "data_loss": "Pérdida de datos",
    "perf": "Rendimiento",
    "financial": "Financiero",
    "regression": "Regresión",
    "dx": "DX / Operación",
}

EFFORT_DEFAULT = {
    "CRITICAL": "M",
    "HIGH": "M",
    "MEDIUM": "S",
    "LOW": "S",
}


# ----------------------------------------------------------------------------
# Loaders
# ----------------------------------------------------------------------------
def load_yaml(path: Path):
    text = path.read_text(encoding="utf-8")
    return yaml.safe_load(text)


def load_findings(path: Path) -> list[dict]:
    text = path.read_text(encoding="utf-8")
    # Strip the leading `# ...` comment lines and the `---` doc separator
    lines = text.splitlines()
    if lines and lines[0].startswith("#"):
        lines = [ln for ln in lines if not ln.startswith("#")]
    if lines and lines[0].strip() == "---":
        lines = lines[1:]
    return yaml.safe_load("\n".join(lines))


# ----------------------------------------------------------------------------
# Markdown helpers
# ----------------------------------------------------------------------------
def md_escape(text: str) -> str:
    if text is None:
        return ""
    return str(text).replace("|", "\\|").replace("\n", " ").strip()


def fmt_locations(locs: list[str]) -> str:
    if not locs:
        return ""
    return " · ".join(f"`{l}`" for l in locs)


def finding_bullet(rec: dict, *, include_action: bool = True) -> str:
    sev = rec["severity"]
    locs = fmt_locations(rec.get("location", []))
    title = md_escape(rec.get("title", ""))
    desc = md_escape(rec.get("description", ""))
    action = md_escape(rec.get("action", ""))
    bullet = f"- **{sev}** `{rec['id']}` {title}"
    if locs:
        bullet += f" — {locs}"
    if desc and desc != title:
        bullet += f" — {desc}"
    if include_action and action:
        bullet += f"\n  - **Acción**: {action}"
    bullet += f" _(blast_radius: `{rec.get('blast_radius', 'dx')}`; confidence: `{rec.get('confidence', 'HIGH')}`)_"
    return bullet


def render_header(findings: list[dict]) -> str:
    n = len(findings)
    now = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
    by_sev = Counter(f["severity"] for f in findings)
    by_proj = Counter(f["project"] for f in findings)
    return (
        f"# MEJORAS ECC — Auditoría Integral del Monorepo Ferretería\n\n"
        f"> **Generado:** {now}  \n"
        f"> **Fuente de verdad:** `audits/findings.yaml`  \n"
        f"> **Total de hallazgos:** {n}  \n"
        f"> **Por severidad:** "
        + " · ".join(f"`{s}`={by_sev.get(s, 0)}" for s in SEVERITY_ORDER)
        + "  \n"
        f"> **Por proyecto:** "
        + " · ".join(f"`{p}`={by_proj.get(p, 0)}" for p in PROJECT_LABEL.keys())
        + "  \n"
        f"> **Modo:** READ-ONLY · No se modificó código. Este archivo es un render derivado.\n\n"
        f"Para añadir/cambiar hallazgos, editar `audits/findings.yaml` y re-ejecutar "
        f"`python3 scripts/render.py` (ver `scripts/README.md`).\n\n---\n\n"
    )


def render_severity_convention() -> str:
    return (
        "## §0.1 Convención de severidad\n\n"
        "| Tag | Criterio (umbral cuantitativo) |\n"
        "|---|---|\n"
        "| **CRITICAL** | Bypass de autorización · credenciales/secrets en el repo · "
        "corrupción/pérdida de datos sin backup · impacto monetario directo · "
        "crash sistémico |\n"
        "| **HIGH** | Degradación >50 % performance · info disclosure sensible · "
        "N+1 en hot path · authz rota en >1 endpoint · compliance failure |\n"
        "| **MEDIUM** | Deuda técnica con workaround · <50 % perf · "
        "tests ausentes en lógica crítica · DX pobre |\n"
        "| **LOW** | Cosmético · inconsistencia estilística · doc ausente |\n\n"
        "Para re-clasificar, editar el campo `severity` en `findings.yaml` y re-renderizar.\n\n---\n\n"
    )


def render_counts_table(findings: list[dict]) -> str:
    by_proj_sev: dict[str, Counter] = defaultdict(Counter)
    for f in findings:
        by_proj_sev[f["project"]][f["severity"]] += 1
    out = ["## §0 TL;DR — Conteos canónicos\n\n",
           "| Proyecto | CRITICAL | HIGH | MEDIUM | LOW | Total |\n",
           "|---|---:|---:|---:|---:|---:|\n"]
    grand = Counter()
    for proj in PROJECT_LABEL.keys():
        sev = by_proj_sev.get(proj, Counter())
        row_total = sum(sev.values())
        out.append(
            f"| `{proj}` ({PROJECT_LABEL[proj]}) "
            f"| {sev.get('CRITICAL', 0)} | {sev.get('HIGH', 0)} "
            f"| {sev.get('MEDIUM', 0)} | {sev.get('LOW', 0)} | **{row_total}** |\n"
        )
        grand += sev
    out.append(
        f"| **TOTAL** | **{grand['CRITICAL']}** | **{grand['HIGH']}** "
        f"| **{grand['MEDIUM']}** | **{grand['LOW']}** | **{sum(grand.values())}** |\n\n"
        f"Estos conteos son la única verdad de severidad. Cualquier otra tabla en este "
        f"documento es un subset.\n\n---\n\n"
    )
    return "".join(out)


def render_top_critical(findings: list[dict], top_n: int = 10) -> str:
    crit = [f for f in findings if f["severity"] == "CRITICAL"]
    crit.sort(key=lambda f: (
        SEVERITY_RANK[f["severity"]],
        {"data_loss": 0, "financial_fraud": 1, "security": 2, "financial": 3, "perf": 4, "regression": 5, "dx": 6}.get(f.get("blast_radius", "dx"), 9),
        f["project"],
        f["dimension"],
        f["id"],
    ))
    out = [f"## §1 Top {top_n} CRITICAL (orden de remediación)\n\n"]
    for i, rec in enumerate(crit[:top_n], 1):
        out.append(finding_bullet(rec, include_action=True) + "\n\n")
    if len(crit) > top_n:
        out.append(f"### Otros CRITICAL ({len(crit) - top_n})\n\n")
        for rec in crit[top_n:]:
            out.append(finding_bullet(rec, include_action=True) + "\n\n")
    out.append("---\n\n")
    return "".join(out)


def render_cross_cutting(findings: list[dict]) -> str:
    by_blast: dict[str, list[dict]] = defaultdict(list)
    for f in findings:
        if f["severity"] in ("CRITICAL", "HIGH"):
            by_blast[f.get("blast_radius", "dx")].append(f)
    out = ["## §2 Hallazgos transversales (cross-cutting)\n\n"]
    out.append("Agrupados por `blast_radius`, severidad >= HIGH.\n\n")
    for blast in sorted(by_blast.keys()):
        out.append(f"### {BLAST_LABEL.get(blast, blast)}\n\n")
        items = sorted(by_blast[blast], key=lambda r: (SEVERITY_RANK[r["severity"]], r["id"]))
        for rec in items:
            out.append(finding_bullet(rec) + "\n\n")
    out.append("---\n\n")
    return "".join(out)


def render_per_project(findings: list[dict], stacks: dict | list) -> str:
    out = ["## §3 Por proyecto × dimensión\n\n"]
    # Accept either {"projects": [...]} wrapper or a bare list
    stack_list = stacks["projects"] if isinstance(stacks, dict) and "projects" in stacks else stacks
    stacks_by_proj = {s["id"]: s for s in stack_list}
    for proj in PROJECT_LABEL.keys():
        proj_findings = [f for f in findings if f["project"] == proj]
        if not proj_findings:
            continue
        out.append(f"### 3.{PROJECT_INDEX[proj] + 1} {PROJECT_LABEL[proj]}\n\n")
        s = stacks_by_proj.get(proj, {})
        if s:
            out.append("**Stack canónico** (de `audits/stacks.yaml`):\n\n")
            for key in ("runtime", "language", "build"):
                if key in s:
                    out.append(f"- **{key.capitalize()}**: {s[key]}\n")
            ancillary_keys = [k for k in s if k not in ("id", "label", "runtime", "language", "build")]
            if ancillary_keys:
                out.append(f"- **Otros**: {', '.join(f'`{k}`={s[k]}' for k in ancillary_keys)}\n")
            out.append("\n")

        # Group by dimension
        by_dim: dict[str, list[dict]] = defaultdict(list)
        for f in proj_findings:
            by_dim[f["dimension"]].append(f)
        for dim in DIMENSION_LABEL.keys():
            dim_items = by_dim.get(dim, [])
            if not dim_items:
                continue
            # Render dimension only if there are >= HIGH findings; otherwise fold to "MEDIUM / LOW (resumen)"
            sorted_items = sorted(dim_items, key=lambda r: (SEVERITY_RANK[r["severity"]], r["id"]))
            by_sev_in_dim: dict[str, list[dict]] = defaultdict(list)
            for r in sorted_items:
                by_sev_in_dim[r["severity"]].append(r)

            out.append(f"#### 3.{PROJECT_INDEX[proj] + 1}.{DIMENSION_INDEX[dim] + 1} {DIMENSION_LABEL[dim]}\n\n")
            for sev in SEVERITY_ORDER:
                if not by_sev_in_dim[sev]:
                    continue
                # If no CRITICAL/HIGH in this dim, fold MEDIUM and LOW into one block
                if sev in ("MEDIUM", "LOW") and not (by_sev_in_dim.get("CRITICAL") or by_sev_in_dim.get("HIGH")):
                    block = "MEDIUM / LOW (resumen)"
                    if not any("####" in l and block in l for l in out[-5:]):
                        out.append(f"**{block}** ({len(by_sev_in_dim[sev])})\n\n")
                else:
                    out.append(f"**{sev}** ({len(by_sev_in_dim[sev])})\n\n")
                for rec in by_sev_in_dim[sev]:
                    out.append(finding_bullet(rec) + "\n\n")
        out.append("---\n\n")
    return "".join(out)


def render_backlog(findings: list[dict], top_n: int = 30) -> str:
    out = ["## §4 Backlog priorizado (top {})\n\n".format(top_n)]
    out.append("Auto-derivado de `findings.yaml`. Orden: severidad desc, blast_radius, "
               "proyecto, dimensión.\n\n")
    out.append("| # | ID | Sev | Componente | Dimensión | Blast | Esfuerzo | Hallazgo | Acción |\n")
    out.append("|---:|---|:--:|---|---|---|---|---|---|\n")
    sorted_f = sorted(findings, key=lambda r: (
        SEVERITY_RANK[r["severity"]],
        {"data_loss": 0, "financial_fraud": 1, "security": 2, "financial": 3, "perf": 4, "regression": 5, "dx": 6}.get(r.get("blast_radius", "dx"), 9),
        r["project"],
        r["dimension"],
        r["id"],
    ))
    for i, rec in enumerate(sorted_f[:top_n], 1):
        out.append(
            f"| {i} | `{rec['id']}` | {rec['severity']} | `{rec['project']}` | "
            f"{rec['dimension']} | `{rec.get('blast_radius', 'dx')}` | "
            f"{EFFORT_DEFAULT.get(rec['severity'], 'M')} | "
            f"{md_escape(rec.get('title', ''))[:80]} | "
            f"{md_escape(rec.get('action', ''))[:80]} |\n"
        )
    out.append(f"\n> Total backlog: **{len(sorted_f)}** hallazgos. Esta tabla muestra el top {top_n}.\n\n")
    out.append("### §4.b Dependencias críticas (DAG resumido)\n\n")
    out.append("- `BACK-SEC-001` (`@PreAuthorize` en controllers) — bloquea cualquier "
               "refactor que asuma el modelo de autorización actual.\n"
               "- `BACK-SEC-004` (quitar default `JWT_SECRET`) — bloquea `BACK-SEC-013` "
               "(fail-fast) y `BACK-REND-007` (Hikari+PgBouncer math).\n"
               "- `DB-SCALE-001` (partitioning) — bloquea métricas P95/P99 estables para "
               "medir `BACK-REND-002` (N+1) y `BACK-REND-008` (`idx_ventas_fecha_local`).\n"
               "- `FRONT-MAN-001` (vitest setup) — bloquea refactors frontend "
               "(`FRONT-REND-002`, `FRONT-UI-002`).\n\n"
               "---\n\n")
    return "".join(out)


def render_quick_wins(findings: list[dict]) -> str:
    quick = [f for f in findings if f["severity"] in ("CRITICAL", "HIGH") and EFFORT_DEFAULT[f["severity"]] == "S"]
    # Fall back: include MEDIUM with S effort too
    quick += [f for f in findings if f["severity"] == "MEDIUM" and EFFORT_DEFAULT[f["severity"]] == "S"]
    quick = list({f["id"]: f for f in quick}.values())  # dedupe by id
    quick.sort(key=lambda r: (SEVERITY_RANK[r["severity"]], r["id"]))
    out = ["## §5 Quick wins (auto-derivados)\n\n"]
    out.append(f"Filtro aplicado: `severity >= HIGH OR (MEDIUM)` AND `effort = S`. "
               f"Total: **{len(quick)}** quick wins.\n\n")
    for rec in quick[:15]:
        out.append(finding_bullet(rec) + "\n\n")
    out.append("---\n\n")
    return "".join(out)


def render_coverage(coverage: list[dict]) -> str:
    out = ["## §6 Cobertura y metodología\n\n"]
    out.append("Matriz de archivos revisados por proyecto. Definida en `audits/coverage.yaml`.\n\n")
    out.append("| Proyecto | Archivos totales (aprox) | Deep | Shallow | No revisados | Confianza |\n")
    out.append("|---|---:|---:|---:|---:|:--:|\n")
    for proj in coverage:
        s = proj.get("summary", {})
        out.append(
            f"| `{proj['project']}` ({PROJECT_LABEL.get(proj['project'], '')}) "
            f"| {s.get('files_total', '?')} | {s.get('files_deep', '?')} "
            f"| {s.get('files_shallow', '?')} | {s.get('files_not_reviewed', '?')} "
            f"| **{s.get('confidence', '?')}** |\n"
        )
    out.append("\n### §6.b Áreas NO inspeccionadas (declaration of known gaps)\n\n")
    for proj in coverage:
        not_rev = proj.get("not_reviewed_examples", [])
        if not_rev:
            out.append(f"**{PROJECT_LABEL.get(proj['project'], proj['project'])}**:\n")
            for p in not_rev:
                out.append(f"- `{p}`\n")
            out.append("\n")
    out.append("---\n\n")
    return "".join(out)


def render_open_questions() -> str:
    return (
        "## §7 Open Questions / Unknowns\n\n"
        "Lo que inspeccionamos pero quedó incierto o necesita respuesta del equipo:\n\n"
        "1. **Cardinalidad real de `seg.auditoria` por mes en producción.** "
        "Estimación 315 M filas/año, pero sin métricas reales.\n"
        "2. **Endpoints con >100 RPS en producción.** No hay APM production-grade "
        "para validar los quick wins de performance.\n"
        "3. **¿Hay WAF frente a la app?** (Cloudflare, AWS WAF, nginx mod_security). "
        "No visible en repo. Si sí, varios MEDIUM/LOW de seguridad son mitigados.\n"
        "4. **¿Qué cubre el seguro de ciber-riesgo actual?** "
        "Afecta prioridades de remediación financiera.\n"
        "5. **¿El equipo tiene on-call rotation y runbooks?** "
        "Afecta la prioridad de Resilience4j / circuit breakers.\n"
        "6. **¿Hay OpenAPI/Swagger UI en producción?** "
        "Afecta lock-down de `/actuator/**` y `/swagger-ui/**`.\n"
        "7. **¿Cuál es la política de LFPDPPP y retención fiscal (SAT)?** "
        "Afecta partitioning + archivado de `seg.auditoria` y `ven.ventas`.\n"
        "8. **¿Qué archivos del frontend NO fueron leídos?** "
        "Listado explícito en `audits/coverage.yaml` §6.b. ~95 archivos "
        "no inspeccionados en `ferreteriaFront/`.\n\n"
        "---\n\n"
    )


def render_appendix_low(findings: list[dict]) -> str:
    low = [f for f in findings if f["severity"] == "LOW"]
    low.sort(key=lambda r: (r["project"], r["dimension"], r["id"]))
    out = [
        f"## §8 Apéndice — LOW backlog\n\n",
        f"Total LOW: **{len(low)}** hallazgos. Mantener/eliminar/aplazar decisión "
        f"documentada en cada fila cuando se revise.\n\n",
        "| ID | Proyecto | Dimensión | Título | Acción |\n",
        "|---|---|---|---|---|\n",
    ]
    for rec in low:
        out.append(
            f"| `{rec['id']}` | `{rec['project']}` | {rec['dimension']} "
            f"| {md_escape(rec.get('title', ''))[:80]} "
            f"| {md_escape(rec.get('action', ''))[:80]} |\n"
        )
    out.append("\n---\n\n")
    return "".join(out)


def render_footer() -> str:
    return (
        "## §9 Próximos pasos (esperando aprobación)\n\n"
        "Este archivo es **render derivado** de `audits/findings.yaml`. "
        "**No se modificó código** en este run.\n\n"
        "Cuando aprueben, los siguientes scripts y comandos están listos:\n\n"
        "1. `python3 scripts/parse_audit.py MEJORAS_ECC.md audits/findings.yaml` — re-extraer hallazgos.\n"
        "2. `python3 scripts/validate.py audits/findings.yaml` — verificar unicidad, refs, esquema.\n"
        "3. `python3 scripts/render.py` — regenerar este documento.\n"
        "4. **Aplicar quick wins** (§5) como primer commit aislado.\n"
        "5. **Refactorizar N+1** en `VentaService.toResponse` (BACK-REND-001) — commit aislado.\n"
        "6. **`@PreAuthorize`** en todos los controllers (BACK-SEC-001) — commit grande.\n"
        "7. **Segregación de roles DB** + `REVOKE DELETE` en ledger — migración versionada.\n"
        "8. **Partitioning** de `seg.auditoria` y ledgers — migración con ventana de mantenimiento.\n\n"
        "**Esperando aprobación para empezar a modificar código.** Indica qué fase priorizar.\n"
    )


# ----------------------------------------------------------------------------
# Main
# ----------------------------------------------------------------------------
def main() -> None:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--findings", type=Path, default=Path("audits/findings.yaml"))
    p.add_argument("--coverage", type=Path, default=Path("audits/coverage.yaml"))
    p.add_argument("--stacks", type=Path, default=Path("audits/stacks.yaml"))
    p.add_argument("--output", type=Path, default=Path("docs/MEJORAS_ECC.md"))
    args = p.parse_args()

    findings = load_findings(args.findings)
    coverage = load_yaml(args.coverage) or []
    stacks = load_yaml(args.stacks) or []

    md = (
        render_header(findings)
        + render_severity_convention()
        + render_counts_table(findings)
        + render_top_critical(findings)
        + render_cross_cutting(findings)
        + render_per_project(findings, stacks)
        + render_backlog(findings)
        + render_quick_wins(findings)
        + render_coverage(coverage)
        + render_open_questions()
        + render_appendix_low(findings)
        + render_footer()
    )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(md, encoding="utf-8")
    print(f"Rendered {len(findings)} findings → {args.output} ({len(md):,} chars)")


if __name__ == "__main__":
    main()