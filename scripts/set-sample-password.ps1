param([string]$Context='rancher-desktop')
# 샘플 계정(햇살방·구름집·달빛집·솔바람집)의 로그인 비밀번호를 정한다.
# 비밀번호는 화면에 보이지 않게 입력받고, 파일·명령 인자·콘솔에 남기지 않은 채 Secret에 넣는다.
# 앱을 재시작하면 샘플이 없을 때는 이 비밀번호로 만들고, 이미 있으면 샘플 계정 비밀번호만 이 값으로 바꾼다.
$ErrorActionPreference='Stop'
$secure=Read-Host -AsSecureString '샘플 계정 비밀번호 (8자 이상, 실제로 쓰는 비밀번호는 피하세요)'
$pointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try { $plain=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) } finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
$bytes=[Text.Encoding]::UTF8.GetBytes($plain)
if($plain.Length -lt 8 -or $bytes.Length -gt 72) { throw '비밀번호는 8자 이상, UTF-8 기준 72바이트 이내로 입력해 주세요.' }

$json=& kubectl --context $Context -n room-demo get secret naranjip-auth -o json | Out-String
if($LASTEXITCODE -ne 0) { throw 'naranjip-auth Secret을 찾지 못했어요. 먼저 scripts/init-auth-secret.ps1을 실행해 주세요.' }
$secret=$json | ConvertFrom-Json
$secret.data | Add-Member -NotePropertyName SAMPLE_PASSWORD -NotePropertyValue ([Convert]::ToBase64String($bytes)) -Force
$secret | ConvertTo-Json -Depth 20 | & kubectl --context $Context replace -f -
if($LASTEXITCODE -ne 0) { throw 'Secret 갱신에 실패했어요.' }

& kubectl --context $Context -n room-demo rollout restart deployment/naranjip
if($LASTEXITCODE -ne 0) { throw '앱 재시작에 실패했어요.' }
& kubectl --context $Context -n room-demo rollout status deployment/naranjip --timeout=240s
if($LASTEXITCODE -ne 0) { throw '앱이 준비되지 않았어요. kubectl logs로 확인해 주세요.' }
Write-Host '샘플 계정 비밀번호를 적용했어요. 햇살방·구름집·달빛집·솔바람집으로 로그인할 수 있어요.'
