# CI: 나란집 GitHub Actions

## 흐름

```text
코드 수정 → git push (main 또는 PR)
  → test: Java 21로 ./mvnw verify, Node 배치·입력 검증 테스트
  → compose-smoke: 깨끗한 Ubuntu에서 docker compose up -d --build 후 실제 접속, 앱 2개 공유 확인
```

**CI**는 코드가 올라올 때마다 자동으로 테스트하고 빌드하는 과정입니다. 설정 파일은 `.github/workflows/ci-cd.yml` 하나입니다.

## compose-smoke가 확인하는 것

GitHub의 Ubuntu 실행기는 매번 새로 만들어지는 깨끗한 컴퓨터이고, Docker만 설치되어 있습니다. 여기서 README와 똑같이 `.env` 없이 `docker compose up -d --build`를 실행합니다. 그래서 이 단계가 통과하면 "Docker만 있는 다른 컴퓨터에서도 안내대로 실행된다"는 근거가 됩니다.

1. 두 app 컨테이너가 모두 `/actuator/health/readiness` `UP`을 응답할 때까지 기다립니다(DB·Redis 연결 포함). nginx의 `X-Upstream` 헤더로 서로 다른 app 두 개를 구분합니다.
2. 홈페이지(`/`)와 가구 이미지(`/furniture-atlas.png`) 응답을 확인합니다.
3. `/api/rankings`에 샘플 집 4채가 있고 1위가 햇살방인지 확인합니다.
4. `/api/rankings?type=theme`의 콘셉트가 "비 오는 날의 집"인지 확인합니다.
5. `scripts/multi-instance-smoke.mjs`로 앱 2개가 Redis로 상태를 공유하는지 확인합니다.
   - 로그인 상태가 두 app 모두에서 유지되는지 봅니다.
   - 서로 다른 app에 붙은 두 WebSocket 사이에서 잠금이 한 명만 되는지, 드래그 미리보기와 저장 결과가 전달되는지 봅니다.
   - 로그아웃하면 두 연결이 모두 끊기는지 봅니다.
6. `docker compose down` 후 다시 `up` 했을 때 데이터가 남아 있고 샘플이 중복 생성되지 않는지 확인합니다.
7. 마지막에 `docker compose down -v`로 정리합니다. 실패하면 컨테이너 로그를 출력합니다.

## GitHub에서 확인하기

1. [저장소 Actions](https://github.com/dowooju/naranjip/actions)를 엽니다.
2. `Test and compose smoke` 실행을 선택합니다.
3. `test`, `compose-smoke`가 초록색이면 통과입니다. 실패하면 실행 요약의 Annotations에 실패한 명령과 컨테이너 로그가 표시됩니다(로그인 없이 보임).

테스트는 CI에서만 실행합니다. Docker 이미지는 테스트 없이 패키징만 하므로 서버(EC2)에서 빌드가 빠릅니다. 이미지를 레지스트리에 올리지 않고, 서버에서 `docker compose up -d --build`로 저장소의 코드를 직접 빌드합니다.

## 실행 중인 앱 확인하기

```bash
docker compose ps
```

```bash
docker compose logs app --tail=100
```

```bash
docker compose restart app
```

코드를 바꾼 뒤에는 `docker compose up -d --build`를 다시 실행하면 새 이미지로 app 컨테이너만 교체됩니다. DB 데이터와 로그인 세션은 유지됩니다. 로그는 app 두 개가 섞여 나오며 줄 앞의 `app-1`, `app-2`로 구분합니다.

## 이전 방식(Kubernetes)

2026-11 중순까지는 Rancher Desktop Kubernetes에 배포하고, 선택적으로 self-hosted runner로 로컬 CD를 했습니다. Docker Compose로 바꾼 이유는 [README](../README.md#왜-kubernetes-대신-docker-compose인가)에 있습니다.

## 공식 참고 자료

- [Docker Compose](https://docs.docker.com/compose/)
- [GitHub Actions](https://docs.github.com/en/actions)
