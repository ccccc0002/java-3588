# State Storage

This directory stores runtime collaboration state for multi-agent execution.

## Subfolders
1. `checkpoints/` key node snapshots saved by hooks.
2. `compact/` compacted context snapshots for low-drift continuation.
3. `locks/` task lock files used to avoid multi-agent edit conflicts.

