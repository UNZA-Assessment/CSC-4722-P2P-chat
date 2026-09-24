#!/usr/bin/env bash
set -e

PORT_BASE="${2:-8000}"
N="${1:-3}"

./kill_all.sh 2>/dev/null || true
./start_all.sh "$N" "$PORT_BASE"

# wait a little for leader election / startup to settle
sleep 2

# kill the current leader based on the node pid files
leader_id=$(cat .pids/node_$(ls .pids | sed 's/node_//;s/.pid//' | sort | tail -n 1).pid 2>/dev/null || true)
# simpler robust approach: kill node N-1, which is the highest-numbered node in a simple ring
leader_id=$(cat .pids/node_$(($(echo "$N" - 1))).pid 2>/dev/null || true)
if [ -n "$leader_id" ]; then
  echo "Killing leader node $(($N - 1)) (pid $leader_id)"
  kill -9 "$leader_id"
fi

sleep 3

echo "--- election logs ---"
for f in logs/node_*.log; do
  echo "### $f"
  tail -n 30 "$f"
done

./kill_all.sh
