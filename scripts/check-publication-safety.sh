#!/usr/bin/env bash

set -eu

forbidden_files=""

if [ -d src/main/resources ]; then
    forbidden_files="$(
        find src/main/resources -type f \
            \( -name 'timeline.bin' -o -path '*/bundled/openf1/*' \)
    )"
fi

if [ -n "$forbidden_files" ]; then
    echo "Forbidden bundled third-party data detected:" >&2
    printf '%s\n' "$forbidden_files" >&2
    exit 1
fi

forbidden_references="$(
    grep -R -n -E 'media\.formula1\.com|content/dam/fom-website' \
        --exclude='check-publication-safety.sh' \
        --exclude-dir=.git \
        --exclude-dir=target \
        . || true
)"

if [ -n "$forbidden_references" ]; then
    echo "Forbidden third-party media reference detected:" >&2
    printf '%s\n' "$forbidden_references" >&2
    exit 1
fi

unpinned_actions=""

if [ -d .github/workflows ]; then
    unpinned_actions="$(
        grep -R -n -E 'uses:[[:space:]]+[^[:space:]]+@' .github/workflows \
            | grep -v -E '@[0-9a-f]{40}([[:space:]]|$)' \
            || true
    )"
fi

if [ -n "$unpinned_actions" ]; then
    echo "GitHub Actions must be pinned to full commit SHAs:" >&2
    printf '%s\n' "$unpinned_actions" >&2
    exit 1
fi

echo "Publication safety checks passed."
