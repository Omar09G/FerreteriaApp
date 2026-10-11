#!/bin/sh
# Atajos pgBackRest (DB-ESC-008, ver deploy/BACKUP.md). Se ejecutan dentro de
# postgres-primary, que ya trae el binario (docker/postgres.Dockerfile).
# Uso: ./backup.sh {stanza-create|check|info|full|diff|expire}
# Cron sugerido en el HOST (usuario con acceso a podman):
#   0 2 * * 0 cd /ruta/a/ferreteriaDB/deploy && ./backup.sh full
#   0 2 * * 1-6 cd /ruta/a/ferreteriaDB/deploy && ./backup.sh diff
#   0 3 * * * cd /ruta/a/ferreteriaDB/deploy && ./backup.sh expire
# Restore = destructivo: hacerlo a mano siguiendo BACKUP.md §5.
set -eu
cd "$(dirname "$0")"
cmd="${1:-info}"
case "$cmd" in
    stanza-create | check | info | expire)
        exec podman compose exec -T postgres-primary \
            pgbackrest --stanza=ferreteria --log-level-console=info "$cmd"
        ;;
    full | diff)
        exec podman compose exec -T postgres-primary \
            pgbackrest --stanza=ferreteria --log-level-console=info --type="$cmd" backup
        ;;
    *)
        echo "uso: $0 {stanza-create|check|info|full|diff|expire}" >&2
        exit 2
        ;;
esac
