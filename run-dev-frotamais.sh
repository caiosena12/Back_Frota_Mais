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
# Perfil dev: credenciais locais padrao (application-dev.properties).
export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-dev}"
if [[ -x ./mvnw ]]; then
  exec ./mvnw spring-boot:run
fi
exec mvn spring-boot:run
