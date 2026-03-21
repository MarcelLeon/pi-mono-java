#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ ! -f "pom.xml" ]]; then
  echo "ERROR: run this script inside repo root."
  exit 1
fi

TS="$(date +%Y%m%d-%H%M%S)"
OUT_FILE="${1:-benchmarks/benchmark-${TS}.md}"
LOG_DIR="benchmarks/logs/${TS}"
mkdir -p "$(dirname "$OUT_FILE")" "$LOG_DIR"

JAVA_VERSION="$(java -version 2>&1 | head -n 1)"
MVN_VERSION_OUTPUT="$(mvn -version 2>/dev/null)"
MAVEN_VERSION="$(printf '%s\n' "$MVN_VERSION_OUTPUT" | head -n 1)"
MAVEN_JAVA="$(printf '%s\n' "$MVN_VERSION_OUTPUT" | grep -E '^Java version:' | head -n 1)"
OS_INFO="$(uname -a)"

RESULT_ROWS=()
HAS_FAIL=0

run_step() {
  local name="$1"
  local cmd="$2"
  local log_file="$LOG_DIR/${name// /_}.log"
  local start end duration status

  echo "==> $name"
  start="$(date +%s)"
  if bash -lc "$cmd" >"$log_file" 2>&1; then
    status="PASS"
  else
    status="FAIL"
    HAS_FAIL=1
  fi
  end="$(date +%s)"
  duration="$((end - start))"
  RESULT_ROWS+=("| ${name} | \`${cmd}\` | ${status} | ${duration}s | \`${log_file}\` |")
}

run_step "Compile" "mvn -q -DskipTests clean compile"
run_step "Session Unit Test" "mvn -q -pl pi-session test"
run_step "Spring Example Tests" "mvn -q -f spring-test-example/pom.xml test"
run_step "CLI Smoke" "printf 'help\nexit\n' | mvn -q -pl pi-cli -DskipTests exec:java"

{
  echo "# Smoke Benchmark Report"
  echo
  echo "- Generated at: $(date '+%Y-%m-%d %H:%M:%S %z')"
  echo "- Java: ${JAVA_VERSION}"
  echo "- Maven: ${MAVEN_VERSION}"
  echo "- Maven Java Runtime: ${MAVEN_JAVA}"
  echo "- OS: ${OS_INFO}"
  echo
  echo "| Step | Command | Status | Duration | Log |"
  echo "|---|---|---|---:|---|"
  printf '%s\n' "${RESULT_ROWS[@]}"
  echo
  echo "## Notes"
  echo "- This is a smoke benchmark for reproducibility, not a lab-grade performance benchmark."
  echo "- Use the same machine/JDK for trend comparison."
} >"$OUT_FILE"

echo
echo "Report written to: $OUT_FILE"
if [[ "$HAS_FAIL" -ne 0 ]]; then
  echo "One or more steps failed. Check logs under $LOG_DIR."
  exit 1
fi

echo "All benchmark steps passed."
