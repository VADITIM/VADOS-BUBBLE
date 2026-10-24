#!/usr/bin/env bash
# Blocks dangerous shell commands: force push, destructive operations. Pushing to main is allowed — the project works on main. PreToolUse hook for Bash operations.
# Exit 2 = block. Exit 0 = allow.

set -uo pipefail

emit_deny() {
  # Emit a JSON deny decision and exit 2.
  local reason="${1//\"/\\\"}"
  printf '{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"deny","permissionDecisionReason":"%s"}}\n' "$reason"
  exit 2
}

if ! command -v jq >/dev/null 2>&1; then
  emit_deny "jq is required for command protection hooks but is not installed."
fi

INPUT=$(cat)
COMMAND=$(printf '%s' "$INPUT" | jq -r '.tool_input.command // empty' 2>/dev/null || true)
[ -z "$COMMAND" ] && exit 0

contains_cmd() { printf '%s' "$COMMAND" | grep -qE "$1"; }
contains_icmd() { printf '%s' "$COMMAND" | grep -qiE "$1"; }

# ── Git push protections ────────────────────────────────────────────────
if contains_cmd '(^|[;&|()]+[[:space:]]*)git[[:space:]]+push'; then
  # Force push (but allow --force-with-lease)
  if contains_cmd 'git[[:space:]]+push([[:space:]]+[^[:space:]]+)*[[:space:]]+(-[a-zA-Z]*f[a-zA-Z]*|--force)([[:space:]=]|$)' \
     && ! contains_cmd '\-\-force-with-lease'; then
    emit_deny "Blocked: force push is not allowed. Use --force-with-lease if you must overwrite remote."
  fi
fi

# ── Destructive filesystem operations ───────────────────────────────────
# rm -rf targeting root, home, $HOME, $VAR (any unresolved expansion), or parent traversal.
# We normalise quotes before matching so "my folder", '$HOME/trash', etc. Are all inspected.
CMD_NOQUOTE=$(printf '%s' "$COMMAND" | tr -d "'\"")
if printf '%s' "$CMD_NOQUOTE" | grep -qE 'rm[[:space:]]+(-[a-zA-Z]*[[:space:]]+)*-?[a-zA-Z]*r[a-zA-Z]*f[a-zA-Z]*[[:space:]]+(/([[:space:]]|\*|$)|~|\$HOME|\$[A-Za-z_][A-Za-z0-9_]*|\.\./\.\.)' ; then
  emit_deny "Blocked: recursive force-delete on /, ~, \$HOME, an unresolved \$VAR, or .../.. Path. Specify a concrete safe target."
fi
# rm -rf /usr, /etc, /var, /bin, etc.
if printf '%s' "$CMD_NOQUOTE" | grep -qE 'rm[[:space:]]+(-[a-zA-Z]+[[:space:]]+)*-?[a-zA-Z]*r[a-zA-Z]*f[a-zA-Z]*[[:space:]]+/(usr|etc|var|bin|sbin|lib|opt|root|boot)([[:space:]/]|$)'; then
  emit_deny "Blocked: recursive delete targeting a system directory."
fi

# ── Dangerous database operations ───────────────────────────────────────
# DROP TABLE|DATABASE|SCHEMA
if contains_icmd 'DROP[[:space:]]+(TABLE|DATABASE|SCHEMA)[[:space:]]+'; then
  emit_deny "Blocked: DROP TABLE/DATABASE/SCHEMA detected. Run manually if intended."
fi
# DELETE FROM without a WHERE on the SAME statement.
# Split on ';' so multi-statement inputs are analysed per-statement.
if printf '%s\n' "$COMMAND" | awk '
  BEGIN { IGNORECASE=1; RS=";" }
  /DELETE[[:space:]]+FROM[[:space:]]+[A-Za-z_][A-Za-z0-9_.]*/ {
    if ($0 !~ /WHERE/) { print "BAD"; exit }
  }
' | grep -q BAD; then
  emit_deny "Blocked: DELETE FROM without a WHERE clause. Add a WHERE or run manually."
fi
if contains_icmd 'TRUNCATE[[:space:]]+TABLE'; then
  emit_deny "Blocked: TRUNCATE TABLE detected. Run manually if intended."
fi

# ── Dangerous system commands ───────────────────────────────────────────
# chmod: any world-writable/universal mode (0?777 or a+rwx)
if contains_cmd 'chmod([[:space:]]+-[a-zA-Z]+)*[[:space:]]+0?777([[:space:]]|$)' \
  || contains_cmd 'chmod([[:space:]]+-[a-zA-Z]+)*[[:space:]]+a\+rwx([[:space:]]|$)'; then
  emit_deny "Blocked: chmod 777 / a+rwx grants everyone full access. Use restrictive perms."
fi

# curl/wget piped to a shell
if contains_cmd '(curl|wget)[[:space:]].*\|[[:space:]]*(sudo[[:space:]]+)?(bash|sh|zsh|ksh|fish|dash|csh)([[:space:]]|$)'; then
  emit_deny "Blocked: piping downloaded content directly to a shell is dangerous."
fi

# Disk / partition. Note: only REDIRECTIONS to /dev/ are destructive. `2>/dev/null` is not.
# Pattern matches: `>[ ]*/dev/<something>` but NOT `2>/dev/null` or `&>/dev/null` style for fd-null.
# Strategy: match `>` optionally with whitespace, followed by /dev/<name>, EXCLUDING /dev/null and /dev/stderr/stdout.
if printf '%s' "$COMMAND" | grep -qE '(^|[^0-9&])>[[:space:]]*/dev/[a-zA-Z][a-zA-Z0-9]*' \
   && ! printf '%s' "$COMMAND" | grep -qE '>[[:space:]]*/dev/(null|stdout|stderr|tty|zero|random|urandom)([[:space:]]|$)' ; then
  emit_deny "Blocked: redirection into a raw device file can destroy data."
fi
if contains_cmd '(^|[;&|[:space:]])(mkfs|mkfs\.[a-z0-9]+)([[:space:]]|$)' \
  || contains_cmd '(^|[;&|[:space:]])dd[[:space:]]+[^|]*(if|of)=/dev/[a-zA-Z]' ; then
  emit_deny "Blocked: mkfs/dd against a device node. Irreversible data loss."
fi

# ── Destructive git ─────────────────────────────────────────────────────
if contains_cmd 'git[[:space:]]+reset[[:space:]]+--hard'; then
  emit_deny "Blocked: git reset --hard discards uncommitted changes permanently."
fi
if contains_cmd 'git[[:space:]]+clean[[:space:]]+-[a-zA-Z]*f'; then
  emit_deny "Blocked: git clean -f permanently deletes untracked files."
fi

# ── Accidental package publishing ───────────────────────────────────────
# Allow --dry-run variants (npm publish --dry-run is safe and common in CI).
publish_patterns=(
  '(npm|yarn|pnpm|bun)[[:space:]]+publish'
  'cargo[[:space:]]+publish'
  'gem[[:space:]]+push'
  'twine[[:space:]]+upload'
)
for pat in "${publish_patterns[@]}"; do
  if contains_cmd "$pat" && ! contains_cmd '(^|[[:space:]])(--dry-run|-n)([[:space:]=]|$)'; then
    emit_deny "Blocked: publishing packages should run in CI or manually, not via Claude."
  fi
done

exit 0
