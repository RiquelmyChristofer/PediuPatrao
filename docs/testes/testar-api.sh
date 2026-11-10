#!/usr/bin/env bash
# Teste manual dos endpoints do dashboard. Uso:
#   BASE=http://localhost:8080 USUARIO=gerente01 SENHA='sua-senha' ./docs/testes/testar-api.sh
# Repita com USUARIO de perfil ATENDENTE para comprovar o bloqueio (esperado: 403).
set -euo pipefail
BASE="${BASE:-http://localhost:8080}"
JAR="$(mktemp)"; trap 'rm -f "$JAR"' EXIT

curl -s -c "$JAR" -o /dev/null "$BASE/login"
TOKEN="$(awk '$6=="XSRF-TOKEN"{print $7}' "$JAR")"
curl -s -b "$JAR" -c "$JAR" -o /dev/null \
  --data-urlencode "username=${USUARIO:?defina USUARIO}" --data-urlencode "password=${SENHA:?defina SENHA}" \
  --data-urlencode "_csrf=${TOKEN}" "$BASE/login"

chamar() {
  printf '\n### GET %s\n' "$1"
  curl -s -b "$JAR" -w '\nHTTP %{http_code}\n' -H 'Accept: application/json' "$BASE$1"
}

chamar "/api/dashboard/filtros"
chamar "/api/dashboard/resumo"
chamar "/api/dashboard/resumo?inicio=2026-11-01&fim=2026-11-07"
chamar "/api/dashboard/vendas?agrupamento=semana"
chamar "/api/dashboard/vendas-por-hora"
chamar "/api/dashboard/vendas-por-categoria?categoria=Pizza"
chamar "/api/dashboard/pedidos-por-status?status=CANCELADO"
chamar "/api/dashboard/pedidos-recentes?limite=5"
chamar "/api/dashboard/produtos-mais-vendidos?limite=5&ordenarPor=receita"
echo; echo "### Casos de erro (esperado: HTTP 400)"
chamar "/api/dashboard/resumo?inicio=2026-12-01&fim=2026-11-01"
chamar "/api/dashboard/resumo?inicio=abc"
chamar "/api/dashboard/resumo?status=INEXISTENTE"
chamar "/api/dashboard/vendas?agrupamento=ano"
chamar "/api/dashboard/produtos-mais-vendidos?limite=999"
