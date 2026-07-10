[CmdletBinding()]
param(
  [string]$BaseUrl = "http://127.0.0.1:8080",
  [int]$InsertN = 5,
  [int]$UpdateN = 3,
  [int]$DeleteN = 2
)

$ErrorActionPreference = "Stop"

foreach ($dao in @("jdbc", "jdbc-template", "mybatis", "jpa")) {
  $uri = "$BaseUrl/api/ducklake/$dao/scenario?insertN=$InsertN&updateN=$UpdateN&deleteN=$DeleteN"
  $result = Invoke-RestMethod -Method Post -Uri $uri
  [pscustomobject]@{
    Dao = $dao
    BatchId = $result.batchId
    Inserted = $result.inserted
    Updated = $result.updated
    Deleted = $result.deleted
    RemainingInBatch = @($result.remainingRows).Count
    TotalRows = $result.totalAfterDelete
  }
}
