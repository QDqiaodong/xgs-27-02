#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")"

for port in 8172 8182 3398 6471; do
  if command -v lsof >/dev/null 2>&1 && lsof -nP -iTCP:"$port" -sTCP:LISTEN >/tmp/xgs-port-check.$$ 2>/dev/null; then
    echo "端口占用: $port"
    cat /tmp/xgs-port-check.$$
    rm -f /tmp/xgs-port-check.$$
    exit 1
  fi
  rm -f /tmp/xgs-port-check.$$
done

docker compose up -d --build
