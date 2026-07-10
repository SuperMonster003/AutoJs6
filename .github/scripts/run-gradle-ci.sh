#!/usr/bin/env bash

set -Eeuo pipefail

if [ "$#" -eq 0 ]; then
    echo "Usage: $0 <gradle command> [arguments...]" >&2
    exit 2
fi

readonly timeout_seconds="${GRADLE_CI_TIMEOUT_SECONDS:-3300}"
readonly max_log_bytes="${GRADLE_CI_MAX_LOG_BYTES:-50331648}"
readonly heartbeat_seconds="${GRADLE_CI_HEARTBEAT_SECONDS:-60}"
readonly log_file="${GRADLE_CI_LOG_FILE:-${RUNNER_TEMP:-/tmp}/android-gradle-build.log}"

mkdir -p "$(dirname "$log_file")"
: > "$log_file"

fifo_path="$(mktemp -u "${RUNNER_TEMP:-/tmp}/android-gradle-output.XXXXXX")"
mkfifo "$fifo_path"
gradle_pid=""
tee_pid=""
terminating=0

cleanup_stream() {
    if [ -n "$tee_pid" ] && kill -0 "$tee_pid" 2>/dev/null; then
        kill -TERM "$tee_pid" 2>/dev/null || true
        wait "$tee_pid" 2>/dev/null || true
    fi
    tee_pid=""
    rm -f "$fifo_path"
}

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

print_resource_snapshot() {
    local elapsed="$1"
    local current_log_bytes="$2"
    local last_task
    last_task="$(grep -E '> Task ' "$log_file" 2>/dev/null | tail -n 1 || true)"

    echo "[gradle-ci] heartbeat elapsed=${elapsed}s log_bytes=${current_log_bytes}"
    if [ -n "$last_task" ]; then
        echo "[gradle-ci] last_task=${last_task}"
    fi
    free -m 2>/dev/null | sed 's/^/[gradle-ci] /' || true
    ps -eo pid,ppid,pgid,pcpu,pmem,rss,etime,comm,args --sort=-rss 2>/dev/null \
        | awk 'NR == 1 || /[j]ava|[g]radle|[k]otlin/' \
        | head -n 12 \
        | sed 's/^/[gradle-ci] /' || true
}

handle_signal() {
    local signal_name="$1"
    if [ "$terminating" -ne 0 ]; then
        exit 130
    fi
    terminating=1
    trap - INT TERM
    echo "Gradle CI guard received signal ${signal_name}." >&2
    stop_gradle_process_group
    wait "$gradle_pid" 2>/dev/null || true
    gradle_pid=""
    wait "$tee_pid" 2>/dev/null || true
    tee_pid=""
    print_failure_tail
    cleanup_stream
    exit 130
}

trap 'handle_signal INT' INT
trap 'handle_signal TERM' TERM
trap cleanup_stream EXIT

# A named pipe avoids Bash process-substitution teardown races when GitHub sends
# a termination signal, while tee preserves normal live output and a full log.
tee "$log_file" < "$fifo_path" &
tee_pid=$!
setsid "$@" > "$fifo_path" 2>&1 &
gradle_pid=$!
readonly started_at=$SECONDS
last_heartbeat_at=0

while kill -0 "$gradle_pid" 2>/dev/null; do
    if grep -Fq "Unexpected error during rewriting of Kotlin metadata" "$log_file" \
        || grep -Fq "An error occurred when parsing kotlin metadata" "$log_file"; then
        echo "Detected an incompatible Kotlin/R8 metadata rewrite failure; stopping Gradle early." >&2
        stop_gradle_process_group
        wait "$gradle_pid" 2>/dev/null || true
        gradle_pid=""
        wait "$tee_pid" 2>/dev/null || true
        tee_pid=""
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
        wait "$tee_pid" 2>/dev/null || true
        tee_pid=""
        print_failure_tail
        exit 87
    fi

    current_log_bytes="$(stat -c '%s' "$log_file" 2>/dev/null || printf '0')"
    if [ "$current_log_bytes" -gt "$max_log_bytes" ]; then
        echo "Gradle output exceeded ${max_log_bytes} bytes; stopping a probable log flood." >&2
        stop_gradle_process_group
        wait "$gradle_pid" 2>/dev/null || true
        gradle_pid=""
        wait "$tee_pid" 2>/dev/null || true
        tee_pid=""
        print_failure_tail
        exit 88
    fi

    elapsed=$((SECONDS - started_at))
    if [ $((elapsed - last_heartbeat_at)) -ge "$heartbeat_seconds" ]; then
        print_resource_snapshot "$elapsed" "$current_log_bytes"
        last_heartbeat_at="$elapsed"
    fi

    if [ "$elapsed" -ge "$timeout_seconds" ]; then
        echo "Gradle exceeded the ${timeout_seconds}-second CI deadline." >&2
        print_resource_snapshot "$elapsed" "$current_log_bytes"
        stop_gradle_process_group
        wait "$gradle_pid" 2>/dev/null || true
        gradle_pid=""
        wait "$tee_pid" 2>/dev/null || true
        tee_pid=""
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
wait "$tee_pid" 2>/dev/null || true
tee_pid=""
trap - INT TERM

if [ "$status" -ne 0 ]; then
    print_failure_tail
fi

exit "$status"
