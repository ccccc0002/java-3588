# Execution Constraints

## Scope
This file defines mandatory execution constraints for ongoing development of task `PHASE2-EXEC`.

## Mandatory Rules
1. Every functional code change must be tested on the RK3588 edge device and pass before closing the change.
2. At each important stage/milestone, code must be synchronized to GitHub.
3. GitHub synchronization is required on milestones, not on every micro-change.

## Edge Test Baseline
- Host: `192.168.1.104`
- User: `zql`
- Workdir: `/home/zql/ks/java-rk3588`
- Expected evidence per stage:
  - executed command(s)
  - pass/fail status
  - timestamp

## Milestone Sync Baseline
- Remote repo: `https://github.com/ccccc0002/java-3588`
- Expected evidence per milestone:
  - commit hash / range
  - branch name
  - sync status
  - timestamp

## Enforcement
- `scripts/collab/Update-PhaseStatus.ps1` must include quality-gate fields:
  - `edge_test_status`
  - `edge_test_command`
  - `github_sync_status`
  - `github_sync_ref`
- Phase completion should not be marked without gate evidence unless explicitly blocked.
