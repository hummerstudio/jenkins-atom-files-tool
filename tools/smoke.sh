#!/usr/bin/env bash
set -uo pipefail
cd "$(dirname "$0")/.."
./build.sh >/dev/null || { echo "build failed" >&2; exit 1; }
. tools/smoke-lib.sh

dir="$(mktemp -d)"
printf '{"operation": "touch", "files": "a/b/one.txt, two.txt"}' >"$dir/input.json"
run "$dir" >/dev/null 2>&1 || fail "touch failed"
[ -f "$dir/a/b/one.txt" ] && [ -f "$dir/two.txt" ] || fail "touch"

dir="$(mktemp -d)"; mkdir -p "$dir/dist"; printf 'hello' >"$dir/dist/a.jar"
printf '{"operation": "sha256", "pattern": "dist/*.jar", "output": "checksums.sha256"}' >"$dir/input.json"
run "$dir" >/dev/null 2>&1 || fail "sha256 failed"
grep -q "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824" "$dir/checksums.sha256" || fail "checksum"

dir="$(mktemp -d)"; mkdir -p "$dir/x"; printf 'a' >"$dir/x/one.txt"; printf 'b' >"$dir/y.txt"
printf '{"operation": "find", "pattern": "**/*.txt"}' >"$dir/input.json"
run "$dir" >/dev/null 2>&1 || fail "find failed"
[ "$(output "$dir" count)" = "2" ] || fail "find count"

dir="$(mktemp -d)"; printf 'name,version\nparcel,2.4.1\n' >"$dir/pkg.csv"
printf '{"operation": "readcsv", "file": "pkg.csv", "column": "version"}' >"$dir/input.json"
run "$dir" >/dev/null 2>&1 || fail "readcsv failed"
[ "$(output "$dir" first)" = "2.4.1" ] || fail "csv"

echo "smoke ok: touch, sha256, find, readcsv"
