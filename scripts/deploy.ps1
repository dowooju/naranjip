param(
    [string]$Image = '',
    [string]$Context = 'rancher-desktop',
    [string]$DockerContext = 'default',
    [switch]$Build
)
$ErrorActionPreference = 'Stop'
function Invoke-Checked {
    param([string]$Command, [string[]]$Arguments)
    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Command failed (exit $LASTEXITCODE)." }
}
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $projectRoot
try {
    Invoke-Checked -Command kubectl -Arguments @('--context', $Context, '--request-timeout=10s', 'get', 'nodes')
    if ($Build) {
        if (!$Image) { $Image = 'naranjip:local-' + (Get-Date -Format 'yyyyMMddHHmmss') }
        # Explicit Docker context avoids accidentally building into a different daemon.
        Invoke-Checked -Command docker -Arguments @('--context', $DockerContext, 'build', '-t', $Image, '.')
    }
    if (!$Image) { throw 'Specify -Build or -Image ghcr.io/OWNER/naranjip:sha-COMMIT.' }
    if ($Image -notmatch '^[a-zA-Z0-9][a-zA-Z0-9._/@:-]+$') { throw 'Invalid image reference.' }
    Invoke-Checked -Command kubectl -Arguments @('--context', $Context, 'apply', '-f', 'k8s/namespace.yaml')
    # Set the final image before apply, avoiding an intermediate rollout of a local placeholder.
    $manifest = Get-Content -LiteralPath 'k8s/deployment.yaml' -Raw
    $manifest.Replace('naranjip:local', $Image) | & kubectl --context $Context apply -f -
    if ($LASTEXITCODE -ne 0) { throw 'Deployment apply failed.' }
    Invoke-Checked -Command kubectl -Arguments @('--context', $Context, 'apply', '-f', 'k8s/service.yaml')
    Invoke-Checked -Command kubectl -Arguments @('--context', $Context, '-n', 'room-demo', 'rollout', 'status', 'deployment/naranjip', '--timeout=240s')
    Write-Host 'Ready. Run: kubectl --context rancher-desktop -n room-demo port-forward service/naranjip 8080:80'
} finally { Pop-Location }
