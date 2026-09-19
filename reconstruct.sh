#!/usr/bin/env bash
set -euo pipefail
bundle_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
checkout_dir="${1:-$PWD/black-ice-emulator-1.0}"
if [[ -e "$checkout_dir" ]]; then
  echo "Destination already exists; choose a new directory." >&2
  exit 1
fi
git init "$checkout_dir"
git -C "$checkout_dir" remote add origin https://github.com/ARMSX2/ARMSX2.git
git -C "$checkout_dir" fetch --depth 1 origin bfae0f5a1255e08fa655b667b826cfd41da2e4db
git -C "$checkout_dir" checkout --detach FETCH_HEAD
git -C "$checkout_dir" apply "$bundle_dir/upstream.patch"
cp -R "$bundle_dir/overlay/." "$checkout_dir/"
echo "Source reconstructed. Read black-ice/README.md for build requirements and outstanding verification."
