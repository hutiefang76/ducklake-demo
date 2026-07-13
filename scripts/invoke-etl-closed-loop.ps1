[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://127.0.0.1:8080',
    [string]$ProjectId = 'notebooks-etl',
    [string]$WorkflowId = 'material.master_refresh',
    [string]$TaskId = 'ods_etl.upsert_ods_material_from_delta',
    [string]$RequestedBy = 'hutie',
    [string]$FilePath,
    [string]$ArtifactId,
    [string]$DeltaUri,
    [string]$PlanId = 'plan-2026-07',
    [string]$VersionId = 'v1',
    [int]$PollSeconds = 2,
    [int]$MaxPolls = 90,
    [switch]$StopAfterStart
)

$ErrorActionPreference = 'Stop'
$base = $BaseUrl.TrimEnd('/')

$contract = Invoke-RestMethod -Method Get -Uri "$base/api/scheduler/tasks/$TaskId/contract"
$lineage = Invoke-RestMethod -Method Get -Uri "$base/api/scheduler/tasks/$TaskId/lineage"

if ($FilePath) {
    $resolvedFile = Get-Item -LiteralPath $FilePath
    $artifact = Invoke-RestMethod -Method Post `
        -Uri "$base/api/v1/etl/artifacts" `
        -Headers @{ 'X-Requested-By' = $RequestedBy } `
        -Form @{ file = $resolvedFile }
    $ArtifactId = $artifact.artifactId
}

if (-not $ArtifactId -and -not $DeltaUri) {
    throw '请通过 -FilePath、-ArtifactId 或 -DeltaUri 提供一个物料增量输入。'
}
if ($ArtifactId -and $DeltaUri) {
    throw '-ArtifactId 与 -DeltaUri 只能提供一个。'
}

$request = @{
    artifactId = $ArtifactId
    deltaUri = $DeltaUri
    planId = $PlanId
    versionId = $VersionId
    reason = 'PowerShell closed-loop acceptance'
} | ConvertTo-Json

$run = Invoke-RestMethod -Method Post `
    -Uri "$base/api/v1/material-master/refresh" `
    -Headers @{ 'X-Requested-By' = $RequestedBy } `
    -ContentType 'application/json' `
    -Body $request
$instanceId = [long]$run.scheduler.workflowInstanceId

if ($StopAfterStart) {
    $stop = Invoke-RestMethod -Method Post `
        -Uri "$base/api/scheduler/projects/$ProjectId/runs/$instanceId/stop"
}

$status = $null
for ($index = 0; $index -lt $MaxPolls; $index++) {
    $status = Invoke-RestMethod -Method Get `
        -Uri "$base/api/scheduler/projects/$ProjectId/runs/$instanceId/status"
    if ($status.terminal) { break }
    Start-Sleep -Seconds $PollSeconds
}

$tasks = Invoke-RestMethod -Method Get `
    -Uri "$base/api/scheduler/projects/$ProjectId/runs/$instanceId/tasks"
$log = $null
if ($tasks.tasks.Count -gt 0) {
    $taskInstanceId = $tasks.tasks[0].id
    $log = Invoke-RestMethod -Method Get `
        -Uri "$base/api/scheduler/projects/$ProjectId/runs/$instanceId/log?taskInstanceId=$taskInstanceId&skipLineNum=0&limit=20000"
}

[pscustomobject]@{
    contract = $contract
    lineage = $lineage
    run = $run
    stop = $stop
    status = $status
    tasks = $tasks
    log = $log
}
