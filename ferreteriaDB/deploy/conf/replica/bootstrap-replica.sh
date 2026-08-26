#!/bin/bash
# Bootstrap de la RÉPLICA de lectura (streaming replication).
# Se usa como command del servicio postgres-replica en docker-compose.
set -e

DATA_DIR=/var/lib/postgresql/data

if [ ! -s "$DATA_DIR/PG_VERSION" ]; then
    echo ">> [replica] Directorio vacío: ejecutando pg_basebackup desde ${PRIMARY_HOST}:5432 ..."
    until PGPASSWORD="$REPLICATION_PASSWORD" pg_basebackup \
            --dbname="host=${PRIMARY_HOST} port=5432 user=replicator password=${REPLICATION_PASSWORD}" \
            -D "$DATA_DIR" -Fp -Xs -P -R; do
        echo ">> [replica] Primario no disponible, reintentando en 3s..."
        sleep 3
    done
    # -R ya genera standby.signal y primary_conninfo con credenciales
    echo ">> [replica] Basebackup completo. Modo standby configurado."
else
    echo ">> [replica] Datos existentes, arrancando standby normal."
fi

exec docker-entrypoint.sh postgres
