# Phase2 Status Board

- task_id: `PHASE2-EXEC`
- source_of_truth: [`phase2-status.json`](/D:/yihecode-server-master/yihecode-server-master/state/progress/phase2-status.json)
- note: this markdown file is a usage guide. Current status always comes from the JSON file.

## Read Current Status

```powershell
Get-Content .\state\progress\phase2-status.json
```

## Update Commands

```powershell
# Start a phase
powershell -ExecutionPolicy Bypass -File .\scripts\collab\Update-PhaseStatus.ps1 `
  -TaskId PHASE2-EXEC -PhaseId Phase1 -Status in_progress -Summary "phase1 start"

# Save an important milestone node
powershell -ExecutionPolicy Bypass -File .\scripts\collab\Save-ImportantNode.ps1 `
  -TaskId PHASE2-EXEC -PhaseId Phase1 -Summary "home cockpit skeleton finished"

# Compact session context explicitly
powershell -ExecutionPolicy Bypass -File .\scripts\collab\Compact-Session.ps1 `
  -TaskId PHASE2-EXEC -Stage Phase1 -Summary "phase1 mid compact"

# Mark phase done
powershell -ExecutionPolicy Bypass -File .\scripts\collab\Update-PhaseStatus.ps1 `
  -TaskId PHASE2-EXEC -PhaseId Phase1 -Status completed -Summary "phase1 accepted"
```
