#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")"
if [[ -f .frotamais-jwt.env ]]; then
  set -a
  source ./.frotamais-jwt.env
  set +a
fi
if [[ -z "${JWT_SECRET:-}" ]]; then
  echo "ERRO: JWT_SECRET não definido." >&2
  exit 1
fi
# O banco nunca e escolhido por padrao: informe-o sempre em DB_URL.
if [[ -z "${DB_URL:-}" ]]; then
  echo "ERRO: DB_URL não definida. Ex.: DB_URL=jdbc:postgresql://localhost:5432/frotamais_dev_local ./run-dev-frotamais.sh" >&2
  exit 1
fi
# Perfil dev: credenciais locais padrao (application-dev.properties).
export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-dev}"
if [[ -x ./mvnw ]]; then
  exec ./mvnw spring-boot:run
fi
exec mvn spring-boot:run
