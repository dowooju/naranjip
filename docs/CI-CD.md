# 처음 하는 CI/CD: 나란집으로 따라하기

## 1. 지금 연결할 흐름

```text
코드 수정 → 모듈별 git commit → git push origin main
  → GitHub Actions: 테스트(test)
  → Docker 이미지 생성·GHCR 저장(publish)
  → 내 PC의 Kubernetes에 적용(deploy)
  → 브라우저에서 확인
```

**CI**는 코드가 올라올 때 자동으로 테스트하고 빌드하는 과정입니다. **CD**는 통과한 버전을 실행 환경에 반영하는 과정입니다. 이미지를 저장하는 것과 Kubernetes에 배포하는 것은 서로 다른 단계입니다.

이 저장소는 main 푸시 시 CI와 이미지 저장까지 자동 실행합니다. 로컬 CD는 기본적으로 꺼져 있습니다. GitHub의 Ubuntu 실행기는 내 PC의 `127.0.0.1:6443`에 직접 접속할 수 없기 때문입니다. 먼저 수동 배포를 익히고, 원하면 내 PC에 실행기(runner)를 연결하세요.

## 2. 먼저 내 PC에서 Kubernetes에 올리기

Rancher Desktop 실행 → Kubernetes 활성화 → Container Engine이 Moby인지 확인합니다. PowerShell에서:

```powershell
Set-Location -LiteralPath 'C:\Users\LG PC\Desktop\github\naranjip'
kubectl --context rancher-desktop get nodes
docker context ls
docker version
.\scripts\deploy.ps1 -Build
kubectl --context rancher-desktop -n room-demo get pods,svc
kubectl --context rancher-desktop -n room-demo port-forward service/naranjip 8080:80
```

`Ready`, `1/1 Running`, `successfully rolled out`가 보이면 <http://localhost:8080>으로 들어갑니다. 빌드 스크립트는 매번 고유 태그를 만들어 변경한 코드가 실제로 새 Pod에 반영되게 합니다. 첫 빌드는 Java/Maven 이미지와 의존성 다운로드로 오래 걸릴 수 있습니다.

8080이 사용 중이면 `8081:80`으로 바꾸고 <http://localhost:8081>에 접속합니다. PowerShell 실행 정책에 의해 스크립트가 막히면 **해당 실행에만** `powershell -ExecutionPolicy Bypass -File .\scripts\deploy.ps1 -Build`를 사용할 수 있습니다.

## 3. GitHub에서 CI 확인하기

1. [저장소 Actions](https://github.com/dowooju/naranjip/actions)를 엽니다.
2. `Test, publish and deploy` 실행을 선택합니다.
3. `test`: Java 21 설정 → `mvnw verify`로 테스트/패키징.
4. `publish`: Dockerfile 빌드 → `ghcr.io/dowooju/naranjip:sha-전체커밋해시` 저장.
5. `deploy`는 `ENABLE_LOCAL_CD` 변수가 없으면 skipped가 정상입니다.

이미지 업로드는 GitHub가 실행마다 제공하는 `GITHUB_TOKEN`을 사용합니다. 워크플로에 `packages: write`가 설정되어 있으므로 별도 Docker Hub 계정이나 토큰 입력이 필요 없습니다. 조직/계정 정책으로 Actions 또는 패키지 작성이 제한되어 있으면 저장소 설정을 확인하세요.

## 4. GitHub가 만든 이미지를 수동 배포하기

GHCR 패키지는 처음 생성될 때 private일 수 있습니다. 학습용 이미지를 공개하려면 GitHub 프로필 → Packages → naranjip → Package settings에서 **패키지 공개 범위**를 직접 확인하세요. 코드 저장소 공개 여부와 패키지 공개 여부는 다를 수 있습니다. 아래는 클러스터에서 인증 없이 pull 가능한 패키지 기준입니다.

```powershell
$commit = git rev-parse HEAD
$image = 'ghcr.io/dowooju/naranjip:sha-' + $commit
# 반드시 이 커밋의 Actions publish가 성공한 후 실행
.\scripts\deploy.ps1 -Image $image
kubectl --context rancher-desktop -n room-demo port-forward service/naranjip 8080:80
```

private 패키지를 유지하려면 Kubernetes `imagePullSecret`을 별도로 만들어 Deployment의 `spec.template.spec.imagePullSecrets`에 연결해야 합니다. Docker CLI 로그인만으로 Kubernetes 인증이 설정되지는 않습니다. 토큰이나 kubeconfig를 Git에 올리지 마세요. 첫 연습은 `-Build` 로컬 이미지로 진행하면 registry 인증이 필요 없습니다.

## 5. 선택: push하면 내 PC까지 자동 배포

1. GitHub 저장소 → **Settings → Actions → Runners → New self-hosted runner**.
2. Windows / x64를 고르고 표시된 다운로드·설정 명령을 별도 runner 폴더에서 실행합니다. 임시 등록 토큰은 화면에 나온 값을 본인 터미널에만 사용합니다.
3. 추가 라벨에 **`naranjip-local`**을 넣습니다. 기본 `self-hosted`, `Windows`, `X64` 라벨은 유지합니다.
4. Rancher Desktop과 같은 Windows 사용자로 runner의 `run.cmd`를 실행합니다. 해당 터미널에서 `kubectl --context rancher-desktop get nodes`가 성공해야 합니다.
5. 저장소 **Settings → Environments**에서 `local-demo` 환경을 만들고 배포 branch를 `main`으로 제한합니다.
6. **Settings → Secrets and variables → Actions → Variables → New repository variable**에서 이름 `ENABLE_LOCAL_CD`, 값 `true`를 저장합니다.
7. 4단계의 GHCR pull이 되는지 먼저 확인한 뒤 main에 새 커밋을 푸시하거나 Actions의 Run workflow를 누릅니다.

이제 `test → publish → deploy`가 순서대로 실행됩니다. PC, Rancher Desktop, runner가 켜져 있어야 합니다. runner는 PR 작업을 수행하지 않도록 이 워크플로의 조건을 제한했습니다. GitHub는 self-hosted runner를 private 저장소에서 사용하는 것을 권장합니다. 공개 저장소이면 자동 CD 대신 수동 배포로 먼저 학습하세요. 자동 CD를 끄려면 `ENABLE_LOCAL_CD`를 `false`로 바꿉니다.

## 6. 모듈별로 수정하고 커밋하기

```powershell
# 예: 화면 수정
git add src/main/resources/static
git commit -m "feat(ui): improve room screen"
# 예: API 수정
git add src/main/java src/test/java
git commit -m "feat(auth): update demo session flow"
git push origin main
```

## 7. 상태 확인과 되돌리기

```powershell
kubectl --context rancher-desktop -n room-demo get pods
kubectl --context rancher-desktop -n room-demo logs deployment/naranjip --tail=100
kubectl --context rancher-desktop -n room-demo describe pods -l app=naranjip
kubectl --context rancher-desktop -n room-demo rollout history deployment/naranjip
# 직전 정상 배포가 존재할 때만 이전 버전으로 되돌리기
kubectl --context rancher-desktop -n room-demo rollout undo deployment/naranjip
kubectl --context rancher-desktop -n room-demo rollout status deployment/naranjip
```

| 증상 | 확인할 것 |
| --- | --- |
| `6443 connection refused` | Rancher Desktop과 Kubernetes가 실행 중인지 |
| Docker daemon 연결 실패 | Rancher Desktop Moby 엔진과 Docker context |
| `ImagePullBackOff` | 이미지 태그, publish 성공 여부, GHCR 패키지 접근 권한 |
| `Pending` | `describe pods`의 자원/스케줄링 이벤트 |
| 로그인 503 | `DEMO_AUTH_ENABLED=true` 설정 |
| 배포 후 로그인이 풀림 | 현재는 세션을 Pod 메모리에만 저장하므로 정상 |
| port-forward 종료 | 재배포 후 port-forward 명령 재실행 |
| GitHub deploy가 queued | runner 실행 여부와 `naranjip-local` 라벨 |

같은 와이파이의 다른 기기에서는 필요할 때 `kubectl --context rancher-desktop -n room-demo port-forward --address 0.0.0.0 service/naranjip 8080:80`으로 열고 `http://PC의-LAN-IP:8080`으로 접속합니다. Windows 방화벽과 공유기의 단말 격리 여부에 따라 추가 설정이 필요하며, 두 번째 기기의 접속 성공은 별도로 검증해야 합니다.

## 공식 참고 자료

- [GitHub: Docker 이미지 게시](https://docs.github.com/en/actions/tutorials/publish-packages/publish-docker-images)
- [GitHub: 자체 호스팅 runner 추가](https://docs.github.com/en/actions/how-tos/manage-runners/self-hosted-runners/add-runners)
- [GitHub: GHCR 사용](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry)
- [Kubernetes: port-forward](https://kubernetes.io/docs/tasks/access-application-cluster/port-forward-access-application-cluster/)
- [Rancher Desktop: 설치와 엔진 설정](https://docs.rancherdesktop.io/getting-started/installation/)
