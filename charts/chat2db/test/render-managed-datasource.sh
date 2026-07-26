#!/usr/bin/env bash
set -euo pipefail

chart_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
render_dir="$(mktemp -d)"
trap 'rm -rf "${render_dir}"' EXIT

helm lint "${chart_dir}"
helm template chat2db "${chart_dir}" >"${render_dir}/default.yaml"

if grep -q 'CHAT2DB_BOOTSTRAP_DATASOURCE' "${render_dir}/default.yaml"; then
  echo "disabled datasource bootstrap unexpectedly rendered configuration" >&2
  exit 1
fi

helm template chat2db "${chart_dir}" \
  --set datasourceBootstrap.enabled=true \
  --set datasourceBootstrap.managementKey=test-readonly \
  --set 'datasourceBootstrap.alias=Test Read Only' \
  --set datasourceBootstrap.host=postgres.example.internal \
  --set datasourceBootstrap.database=app \
  --set datasourceBootstrap.user=reader \
  --set datasourceBootstrap.existingSecret=chat2db-database \
  >"${render_dir}/enabled.yaml"

grep -q 'name: CHAT2DB_BOOTSTRAP_DATASOURCE_ENABLED' "${render_dir}/enabled.yaml"
grep -q 'secretName: chat2db-database' "${render_dir}/enabled.yaml"
grep -q 'defaultMode: 0440' "${render_dir}/enabled.yaml"
grep -q 'mountPath: "/run/secrets/chat2db-datasource/password"' "${render_dir}/enabled.yaml"
grep -q 'mountPath: "/run/secrets/chat2db-datasource/ca.crt"' "${render_dir}/enabled.yaml"

if helm template chat2db "${chart_dir}" \
  --set datasourceBootstrap.enabled=true \
  >"${render_dir}/invalid.yaml" 2>"${render_dir}/invalid.err"; then
  echo "enabled datasource bootstrap rendered without required values" >&2
  exit 1
fi
