#!/usr/bin/env bash

set -Eeuo pipefail

if [ "$#" -eq 0 ]; then
    echo "Usage: $0 <gradle command> [arguments...]" >&2
    exit 2
fi

readonly timeout_seconds="${GRADLE_CI_TIMEOUT_SECONDS:-3600}"
readonly max_log_bytes="${GRADLE_CI_MAX_LOG_BYTES:-50331648}"
readonly log_file="${GRADLE_CI_LOG_FILE:-${RUNNER_TEMP:-/tmp}/android-gradle-build.log}"

mkdir -p "$(dirname "$log_file")"
: > "$log_file"

gradle_pid=""

stop_gradle_process_group() {
    if [ -z "$gradle_pid" ] || ! kill -0 "$gradle_pid" 2>/dev/null; then
        return
    fi

    kill -TERM -- "-$gradle_pid" 2>/dev/null || kill -TERM "$gradle_pid" 2>/dev/null || true
    for _ in 1 2 3 4 5 6; do
        if ! kill -0 "$gradle_pid" 2>/dev/null; then
            return
        fi
        sleep 2
    done
    kill -KILL -- "-$gradle_pid" 2>/dev/null || kill -KILL "$gradle_pid" 2>/dev/null || true
}

print_failure_tail() {
    echo
    echo "===== Gradle log tail ====="
    tail -n 300 "$log_file" 2>/dev/null || true
    echo "===== End Gradle log tail ====="
}

handle_signal() {
    echo "Gradle CI guard received a termination signal." >&2
    stop_gradle_process_group
    print_failure_tail
    exit 130
}

trap handle_signal INT TERM

# Start Gradle in its own process group. The process substitution keeps normal
# live output while retaining a complete log for bounded diagnostics.
setsid "$@" > >(tee "$log_file") 2>&1 &
gradle_pid=$!
readonly started_at=$SECONDS

while kill -0 "$gradle_pid" 2>/dev/null; do
    if grep -Fq "Unexpected error during rewriting of Kotlin metadata" "$log_file" \
        || grep -Fq "An error occurred when parsing kotlin metadata" "$log_file"; then
        echo "Detected an incompatible Kotlin/R8 metadata rewrite failure; stopping Gradle early." >&2
        stop_gradle_process_group
        wait "$gradle_pid" 2>/dev/null || true
        gradle_pid=""
        print_failure_tail
        exit 86
    fi

    if grep -Fq "java.lang.OutOfMemoryError" "$log_file" \
        || grep -Fq "No space left on device" "$log_file" \
        || grep -Fq "Gradle build daemon disappeared unexpectedly" "$log_file"; then
        echo "Detected a non-recoverable runner resource failure; stopping Gradle early." >&2
        stop_gradle_process_group
        wait "$gradle_pid" 2>/dev/null || true
        gradle_pid=""
        print_failure_tail
        exit 87
    fi

    current_log_bytes=$(stat -c '%s' "$log_file" 2>/dev/null || printf '0')
    if [ "$current_log_bytes" -gt "$max_log_bytes" ]; then
        echo "Gradle output exceeded ${max_log_bytes} bytes; stopping a probable log flood." >&2
        stop_gradle_process_group
        wait "$gradle_pid" 2>/dev/null || true
        gradle_pid=""
        print_failure_tail
        exit 88
    fi

    elapsed=$((SECONDS - started_at))
    if [ "$elapsed" -ge "$timeout_seconds" ]; then
        echo "Gradle exceeded the ${timeout_seconds}-second CI deadline." >&2
        stop_gradle_process_group
        wait "$gradle_pid" 2>/dev/null || true
        gradle_pid=""
        print_failure_tail
        exit 124
    fi

    sleep 3
done

set +e
wait "$gradle_pid"
status=$?
set -e
gradle_pid=""
trap - INT TERM

if [ "$status" -ne 0 ]; then
    print_failure_tail
fi

exit "$status"
