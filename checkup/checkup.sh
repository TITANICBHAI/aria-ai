#!/usr/bin/env bash
# checkup.sh
# Indexes all code files in the project and copies them into the ./checkup folder,
# preserving the original directory structure relative to the workspace root.

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

for filepath in "${ALL_FILES[@]}"; do
  # Compute relative path from workspace root
  rel="${filepath#$WORKSPACE_ROOT/}"

  # Write to index
  echo "$rel" >> "$INDEX_FILE"

  # Destination inside checkup/
  dest="$CHECKUP_DIR/$rel"
  dest_dir="$(dirname "$dest")"

  # Create destination directory and copy file
  if mkdir -p "$dest_dir" && cp "$filepath" "$dest"; then
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
