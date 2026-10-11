#!/bin/sh
# DB-ESC-008: crea la stanza pgBackRest en el primer init (best-effort).
# Corre dentro del entrypoint de postgres (solo con PGDATA vacío); si falla,
# el arranque SIGUE (el archivado reintenta) y el operador la crea manual:
#   podman compose exec postgres-primary pgbackrest --stanza=ferreteria stanza-create
# Ver deploy/BACKUP.md §3.2.
PGUSER_BIN=""
if [ "$(id -u)" = "0" ]; then
    mkdir -p /backups
    chown postgres:postgres /backups
    chmod 700 /backups
    if command -v su-exec >/dev/null 2>&1; then
        PGUSER_BIN="su-exec postgres"
    fi
fi
export PGHOST=/var/run/postgresql PGUSER=postgres
# shellcheck disable=SC2086
if ${PGUSER_BIN:+$PGUSER_BIN }pgbackrest --stanza=ferreteria --log-level-console=warn stanza-create; then
    if ${PGUSER_BIN:+$PGUSER_BIN }pgbackrest --stanza=ferreteria --log-level-console=warn check; then
        echo "pgBackRest: stanza ferreteria lista"
    else
        echo "AVISO pgBackRest: check falló; ver deploy/BACKUP.md §3.2"
    fi
else
    echo "AVISO pgBackRest: stanza-create falló (¿/backups sin montar?); ver deploy/BACKUP.md §3.2"
fi
