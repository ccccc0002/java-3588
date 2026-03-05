# Multi-Agent Collaboration Playbook v1

## 1. Scope
This playbook operationalizes the PRD plan with three hard requirements:
1. Event-driven context autosave with hook-driven key-state checkpointing.
2. Periodic context compaction and strict task-level workspace isolation.
3. Multi-agent conflict avoidance and end-to-end process logging.

Reference documents:
1. `docs/prd/PRD-ZLM-RK3588.md`
2. `docs/prd/interface-contract-v1.md`
3. `docs/prd/task-board-four-squads-v1.md`

## 2. Folder Layout
1. `scripts/collab/` automation scripts.
2. `hooks/hook-config.json` event-action routing.
3. `state/checkpoints/` timestamped node states.
4. `state/compact/` compact context snapshots.
5. `state/locks/` lock files to avoid agent conflicts.
6. `workspaces/tasks/<TASK_ID>/` task-isolated working folders.
7. `process/PROCESS-LOG.md` chronological process log.

## 3. Required Workflow
1. Create isolated workspace per task:
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\collab\New-TaskWorkspace.ps1 -TaskId TSK-001 -Owner media-team
```
2. Acquire lock before editing task assets:
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\collab\Acquire-TaskLock.ps1 -TaskId TSK-001 -AgentId media-agent
```
3. Trigger start hook:
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\collab\Invoke-Hook.ps1 -Event task_started -TaskId TSK-001 -Stage phase1 -Summary "start implementation"
```
4. Milestone hook (auto checkpoint + compact + sync):
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\collab\Invoke-Hook.ps1 -Event milestone_reached -TaskId TSK-001 -Stage phase1 -Summary "resolver done"
```
5. Before handoff hook (auto checkpoint + compact + sync):
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\collab\Invoke-Hook.ps1 -Event before_handoff -TaskId TSK-001 -Stage phase1 -Summary "handoff package ready"
```
6. Release lock when complete:
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\collab\Release-TaskLock.ps1 -TaskId TSK-001 -AgentId media-agent
```
7. Optional manual sync (if needed):
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\collab\Sync-RecorderRepo.ps1 -Root .
```
8. One-click setup (no scheduler required):
```powershell
$env:GITHUB_TOKEN='your_token'
powershell -ExecutionPolicy Bypass -File .\scripts\collab\Setup-Autosave-And-Sync.ps1 -Root . -TaskId global -Stage collab -SkipAdminCheck -AllowLoopFallback
```
9. One-click stream contract validation:
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\testing\Run-Stream-Validation.ps1 -BaseUrl "http://127.0.0.1:8080" -CameraId 1 -TimeoutSec 10 -Mode full
```
10. Quick stream validation (contracts only):
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\testing\Run-Stream-Validation.ps1 -BaseUrl "http://127.0.0.1:8080" -CameraId 1 -TimeoutSec 10 -Mode quick
```
11. Stream + inference combined validation:
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\testing\Run-Stream-Validation.ps1 -BaseUrl "http://127.0.0.1:8080" -CameraId 1 -ModelId 1 -AlgorithmId 1 -Mode full -IncludeInference
```
12. Standalone inference validation:
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\testing\Validate-Inference-Contracts.ps1 -BaseUrl "http://127.0.0.1:8080" -CameraId 1 -ModelId 1 -AlgorithmId 1
```

## 4. Conflict Control Policy
1. One task lock can be owned by only one agent at any time.
2. Any merge/cross-task operation requires fresh checkpoint.
3. On conflict detection:
   - trigger `conflict_detected` hook,
   - compact context,
   - resolve ownership and trigger `conflict_resolved`.

## 5. Compaction Policy
1. Compaction is event-driven (no timer required in simple mode).
2. Mandatory compaction points:
   - before handoff,
   - after major contract changes,
   - after conflict resolution.
3. Use `state/compact/compact-latest.md` as canonical short context.

## 6. GitHub Process Recording Policy
Target repository: `https://github.com/ccccc0002/java-3588`

Record these artifacts continuously:
1. `process/PROCESS-LOG.md`
2. `state/compact/compact-latest.md`
3. Milestone checkpoints from `state/checkpoints/`
4. Updated PRD/contract/task-board documents

Recommended sync cadence:
1. On every `milestone_reached` hook.
2. On every `task_started` and `before_handoff` hook.
3. Before handoff and at end-of-day.

## 7. Operational Notes
1. Current working directory is not a git repository, so native `.git/hooks` is unavailable.
2. This playbook uses project-level hook scripts (`Invoke-Hook.ps1`) as functional equivalent.
3. If repository is later initialized with git, this hook system can be mapped to native hooks.
4. Event-driven sync requires `GITHUB_TOKEN` to push to remote GitHub repository.
