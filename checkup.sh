#!/usr/bin/env bash
# checkup.sh
# Indexes all code files in the project and copies them flat into the ./checkup folder
# (no subdirectories). Files with colliding names get their path prepended, separated
# by double-underscores, so every file lands at the top level of checkup/.

set -euo pipefail

WORKSPACE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CHECKUP_DIR="$WORKSPACE_ROOT/checkup"
INDEX_FILE="$CHECKUP_DIR/FILE_INDEX.txt"

# Directories / files to exclude from the copy/index
EXCLUDE_PATTERNS=(
  "*/node_modules/*"
  "*/.git/*"
  "*/.cache/*"
  "*/llama.cpp/*"
  "*/dist/*"
  "*/.expo/*"
  "*/checkup/*"
  "*/.agents/*"
  "*/.local/*"
  "*/.replit-artifact"
  "*/pnpm-lock.yaml"
)

echo "================================================"
echo " ARIA Codebase Checkup — File Indexer & Copier"
echo "================================================"
echo "Workspace : $WORKSPACE_ROOT"
echo "Output    : $CHECKUP_DIR"
echo ""

# Build the find exclusion arguments
FIND_ARGS=()
for pattern in "${EXCLUDE_PATTERNS[@]}"; do
  FIND_ARGS+=("!" "-path" "$pattern")
done

# Collect all files
mapfile -t ALL_FILES < <(find "$WORKSPACE_ROOT" -type f "${FIND_ARGS[@]}" | sort)

TOTAL=${#ALL_FILES[@]}
echo "Found $TOTAL files to index and copy."
echo ""

# Create the checkup directory
mkdir -p "$CHECKUP_DIR"

# Write the index header
{
  echo "ARIA Codebase — Full File Index"
  echo "Generated  : $(date)"
  echo "Workspace  : $WORKSPACE_ROOT"
  echo "Total files: $TOTAL"
  echo "========================================"
  echo ""
} > "$INDEX_FILE"

COPIED=0
FAILED=0

# Track flat filenames already used (for collision detection)
declare -A SEEN_NAMES

for filepath in "${ALL_FILES[@]}"; do
  # Compute relative path from workspace root
  rel="${filepath#$WORKSPACE_ROOT/}"

  # Write to index
  echo "$rel" >> "$INDEX_FILE"

  # Flat filename = just the basename
  base="$(basename "$filepath")"

  # If this filename was already used by a different file, prefix it with
  # the path (slashes replaced by double-underscores) to avoid overwriting
  if [[ -n "${SEEN_NAMES[$base]+_}" ]]; then
    flat_name="${rel//\//__}"
  else
    flat_name="$base"
    SEEN_NAMES[$base]=1
  fi

  dest="$CHECKUP_DIR/$flat_name"

  if cp "$filepath" "$dest"; then
    COPIED=$((COPIED + 1))
  else
    echo "  [WARN] Failed to copy: $rel" >&2
    FAILED=$((FAILED + 1))
  fi
done

echo "========================================"
echo "Done."
echo "  Copied : $COPIED"
echo "  Failed : $FAILED"
echo "  Index  : $INDEX_FILE"
echo "========================================"
