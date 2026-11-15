# CLAUDE.md

The project's instructions live in `AGENTS.md`, shared by every AI assistant; this file only adds what is specific to Claude Code.

@AGENTS.md

## Claude Code

- `.claude/settings.json` enforces the git, `gh`, `aws` and `terraform` rule with `ask`/`deny` permissions.
- A PostToolUse hook (`.claude/hooks/format.sh`) formats every file Claude edits with Spotless or Prettier. It needs `jq`, the Nix shell and `frontend/node_modules`.
- Personal settings go in `.claude/settings.local.json`, which is git-ignored.
