param(
    [string]$ComposeFile = 'deploy/quality-verify.compose.yml',
    [string]$ProjectName = 'msp-quality-verify'
)
$ErrorActionPreference = 'Stop'
if ($ProjectName -ne 'msp-quality-verify') { throw 'This verifier is restricted to the isolated msp-quality-verify project.' }
function Invoke-Compose([string[]]$Arguments) {
    $output = & docker compose -p $ProjectName -f $ComposeFile @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Acceptance command failed: $($Arguments[0])" }
    return $output
}
Invoke-Compose @('config', '--quiet')
$health = Invoke-RestMethod 'http://127.0.0.1:17071/actuator/health/readiness'
if ($health.status -ne 'UP') { throw 'Application readiness is not UP.' }
$sql = "SELECT COUNT(*) FROM metersphere_version WHERE success=0; SELECT COUNT(*) FROM metersphere_version WHERE version IN ('3.7.2.91','3.7.2.92','3.7.2.93') AND success=1 AND checksum IS NOT NULL; START TRANSACTION; INSERT INTO execution_quality_policy_project(project_id) VALUES ('quality-environment-smoke'); SELECT COUNT(*) FROM execution_quality_policy_project WHERE project_id='quality-environment-smoke'; ROLLBACK;"
$result = $sql | & docker compose -p $ProjectName -f $ComposeFile exec -T mysql sh -c 'MYSQL_PWD=$MYSQL_ROOT_PASSWORD mysql -uroot -N metersphere_quality'
if ($LASTEXITCODE -ne 0) { throw 'Migration database verification failed.' }
$lines = @($result | Where-Object { $_ -match '^\d+$' })
if ($lines.Count -ne 3 -or $lines[0] -ne '0' -or $lines[1] -ne '3' -or $lines[2] -ne '1') {
    throw 'Migration history or transactional database write verification failed.'
}
$pong = Invoke-Compose @('exec','-T','redis','redis-cli','ping')
if ($pong -notcontains 'PONG') { throw 'Redis read/write service unavailable.' }
Invoke-Compose @('exec','-T','minio','curl','-fsS','http://127.0.0.1:9000/minio/health/ready')
Invoke-Compose @('exec','-T','kafka','/opt/kafka/bin/kafka-topics.sh','--bootstrap-server','kafka:9092','--list')
Invoke-Compose @('ps')
Write-Output 'PASS: application readiness, migration records, transactional database write, Redis, MinIO readiness and Kafka metadata.'
Write-Output 'Object upload/download and authenticated UI/MCP flows still require separate smoke evidence.'
