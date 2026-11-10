param(
    [Parameter(Mandatory=$true)][string]$LoginId,
    [string]$Context='rancher-desktop',
    [string]$Database='naranjip',
    [switch]$Revoke
)
$ErrorActionPreference='Stop'
if($LoginId -notmatch '^[a-zA-Z0-9_가-힣]{3,20}$') { throw 'Use a registered login ID (3-20 letters, numbers or underscores).' }
if($Database -notmatch '^[a-zA-Z0-9_]+$') { throw 'Invalid database name.' }
$normalized=$LoginId.ToLowerInvariant()
$roleValue=if($Revoke){'FALSE'}else{'TRUE'}
$sql="UPDATE accounts SET is_admin=$roleValue WHERE login_id='$normalized' RETURNING login_id;"
$result=@($sql | & kubectl --context $Context -n room-demo exec -i postgres-0 -- sh -c 'psql -U "$POSTGRES_USER" -d "$1" -v ON_ERROR_STOP=1 -t -A' -- $Database)
if($LASTEXITCODE -ne 0) { throw 'Admin permission update failed.' }
if($result -notcontains $normalized) { throw 'Account not found. Register on the website first; no new account was created.' }
Write-Host "Admin permission for $normalized = $roleValue. Refresh the website."
