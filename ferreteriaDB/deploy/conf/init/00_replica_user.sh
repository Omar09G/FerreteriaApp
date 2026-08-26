#!/bin/bash
# Se ejecuta automáticamente por el entrypoint de la imagen oficial.
# Orden en /docker-entrypoint-initdb.d/: 00_* antes que los .sql numerados.
set -e

echo ">> [init] Creando rol de replicación..."
psql -v ON_ERROR_STOP=1 -U postgres <<-EOSQL
    DO \$\$
    BEGIN
        IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'replicator') THEN
            CREATE ROLE replicator LOGIN REPLICATION PASSWORD '${REPLICATION_PASSWORD}';
        ELSE
            ALTER ROLE replicator WITH LOGIN REPLICATION PASSWORD '${REPLICATION_PASSWORD}';
        END IF;
    END
    \$\$;
EOSQL


echo ">> [init] Listo."
