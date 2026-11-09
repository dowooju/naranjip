param([string]$Context='rancher-desktop')
$ErrorActionPreference='Stop'
& kubectl --context $Context apply -f (Join-Path $PSScriptRoot '../k8s/namespace.yaml')
if($LASTEXITCODE -ne 0){throw 'Namespace initialization failed'}
& kubectl --context $Context -n room-demo get secret naranjip-auth -o name 2>$null
if($LASTEXITCODE -eq 0){Write-Host 'Existing auth secret preserved.';exit 0}
function New-RandomKey {
    $bytes=New-Object byte[] 32
    $rng=[System.Security.Cryptography.RandomNumberGenerator]::Create()
    try{$rng.GetBytes($bytes)}finally{$rng.Dispose()}
    return [Convert]::ToBase64String($bytes)
}
# Never write these values to a file, console, Git, or process arguments.
$manifest=@{
    apiVersion='v1';kind='Secret';metadata=@{name='naranjip-auth';namespace='room-demo'};type='Opaque'
    stringData=@{DB_USERNAME='naranjip';DB_PASSWORD=(New-RandomKey);EMAIL_ENCRYPTION_KEY=(New-RandomKey);EMAIL_LOOKUP_KEY=(New-RandomKey)}
}
$manifest | ConvertTo-Json -Depth 6 | & kubectl --context $Context create -f -
if($LASTEXITCODE -ne 0){throw 'Auth secret creation failed'}
Write-Host 'Auth secret initialized. Keep it when restarting PostgreSQL or the app.'
