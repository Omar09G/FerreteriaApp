#!/usr/bin/env bash
# Pipeline: parse_audit → validate → render.
# Idempotent. Run after editing any YAML in audits/ or after regenerating the source MD.
set -euo pipefail
cd "$(dirname "$0")/.."

echo "=== 1/3 parse_audit (only if you need to re-extract from the legacy MD) ==="
if [[ "${REPARSE:-0}" == "1" ]]; then
    python3 scripts/parse_audit.py docs/MEJORAS_ECC.v1.md audits/findings.yaml
else
    echo "Skipping parse (set REPARSE=1 to force)."
fi

echo ""
echo "=== 2/3 validate ==="
python3 scripts/validate.py audits/findings.yaml

echo ""
echo "=== 3/3 render ==="
python3 scripts/render.py \
    --findings audits/findings.yaml \
    --coverage audits/coverage.yaml \
    --stacks audits/stacks.yaml \
    --output docs/MEJORAS_ECC.md

echo ""
echo "Done. Open docs/MEJORAS_ECC.md"