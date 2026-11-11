param([string]$Context='rancher-desktop')
# Sets the login password for the four sample accounts (see docs/SAMPLE-DATA.md).
# The password is read without echo and passed to kubectl through stdin only:
# never written to a file, process argument or the console.
# On restart the app creates the samples with it, or resets only the sample accounts to it.
$ErrorActionPreference='Stop'
$secure=Read-Host -AsSecureString 'Sample account password (8+ chars, do not reuse a real password)'
$pointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try { $plain=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) } finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
$bytes=[Text.Encoding]::UTF8.GetBytes($plain)
if($plain.Length -lt 8 -or $bytes.Length -gt 72) { throw 'Use at least 8 characters and at most 72 UTF-8 bytes.' }

$json=& kubectl --context $Context -n room-demo get secret naranjip-auth -o json | Out-String
if($LASTEXITCODE -ne 0) { throw 'Secret naranjip-auth not found. Run scripts/init-auth-secret.ps1 first.' }
$secret=$json | ConvertFrom-Json
$secret.data | Add-Member -NotePropertyName SAMPLE_PASSWORD -NotePropertyValue ([Convert]::ToBase64String($bytes)) -Force
$secret | ConvertTo-Json -Depth 20 | & kubectl --context $Context replace -f -
if($LASTEXITCODE -ne 0) { throw 'Secret update failed.' }

& kubectl --context $Context -n room-demo rollout restart deployment/naranjip
if($LASTEXITCODE -ne 0) { throw 'App restart failed.' }
& kubectl --context $Context -n room-demo rollout status deployment/naranjip --timeout=240s
if($LASTEXITCODE -ne 0) { throw 'App did not become ready. Check kubectl logs.' }
Write-Host 'Sample password applied. The four sample accounts can now log in.'
