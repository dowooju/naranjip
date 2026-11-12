#!/usr/bin/env bash
# 앱 컨테이너가 모두 준비될 때까지 기다린다(CI와 EC2 확인용).
# 사용: bash scripts/wait-ready.sh [주소] [앱 개수]   예: bash scripts/wait-ready.sh http://localhost:8080 2
# nginx가 붙이는 X-Upstream 헤더로, 서로 다른 앱 컨테이너가 각각 readiness UP(DB·Redis 연결 포함)을 응답하는지 센다.
base="${1:-http://localhost:8080}"
want="${2:-2}"
for i in $(seq 1 60); do
  ready=$(for j in $(seq 1 $((want * 4))); do
    curl -fsS -D - -o /dev/null "$base/actuator/health/readiness" 2>/dev/null \
      | tr -d '\r' | awk -F': ' 'tolower($1)=="x-upstream" && $2 !~ /,/ {print $2}'
  done | sort -u | wc -l)
  if [ "$ready" -ge "$want" ]; then echo "$want app containers ready after $((i * 5))s"; exit 0; fi
  sleep 5
done
echo "app containers did not become ready (saw $ready of $want)"
docker compose ps 2>/dev/null
exit 1
