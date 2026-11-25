#!/usr/bin/env bash
# Sobe a API para integracao com o front-end, no perfil dev, contra um banco
# exclusivo montado so pelas migrations (padrao: frotamais_integracao).
# Nunca aponta para o banco persistente "frotamais" nem para bancos de teste.
# Uso: ./run-integracao-frotamais.sh   (Ctrl+C para parar)
# Detalhes: docs/AMBIENTE_INTEGRACAO_FRONTEND.md
set -Eeuo pipefail
cd "$(dirname "$0")"

BANCO="${FROTAMAIS_INTEGRACAO_DB:-frotamais_integracao}"
HOST_DB="${FROTAMAIS_INTEGRACAO_HOST:-localhost:5432}"

if [[ "$BANCO" == "frotamais" || "$BANCO" == *test* ]]; then
  echo "ERRO: o banco '$BANCO' nao pode ser usado para integracao." >&2
  exit 1
fi

if [[ -n "${DB_URL:-}" ]]; then
  echo "AVISO: DB_URL definida na sessao sera ignorada; usando '$BANCO'." >&2
fi
export DB_URL="jdbc:postgresql://${HOST_DB}/${BANCO}"

# O banco de integracao nasce vazio e e montado so pelas migrations:
# baseline nao se aplica aqui.
unset SPRING_FLYWAY_BASELINE_ON_MIGRATE SPRING_FLYWAY_BASELINE_VERSION

if command -v psql >/dev/null 2>&1; then
  if ! psql -h "${HOST_DB%%:*}" -p "${HOST_DB##*:}" -U "${DB_USER:-postgres}" -d postgres -Atc \
      "select 1 from pg_database where datname = '$BANCO'" 2>/dev/null | grep -q 1; then
    echo "ERRO: o banco '$BANCO' nao existe ou o Postgres nao respondeu." >&2
    echo "Crie-o vazio: psql -h localhost -U postgres -c \"CREATE DATABASE $BANCO\"" >&2
    exit 1
  fi
fi

if (exec 3<>/dev/tcp/127.0.0.1/8080) 2>/dev/null; then
  echo "ERRO: a porta 8080 ja esta em uso. Mantenha uma unica instancia da API." >&2
  exit 1
fi

export SPRING_PROFILES_ACTIVE=dev
echo "Subindo a API no perfil dev, banco '$BANCO', porta 8080..."
exec ./run-dev-frotamais.sh
