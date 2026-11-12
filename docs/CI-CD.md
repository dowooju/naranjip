# CI: 나란집 GitHub Actions

## 흐름

```text
코드 수정 → git push (main 또는 PR)
  → test: Java 21로 ./mvnw verify, Node 배치·입력 검증 테스트
  → compose-smoke: 깨끗한 Ubuntu에서 docker compose up -d --build 후 실제 접속 확인
  → publish (main 푸시만): Docker 이미지를 GHCR에 저장
```

**CI**는 코드가 올라올 때마다 자동으로 테스트하고 빌드하는 과정입니다. 설정 파일은 `.github/workflows/ci-cd.yml` 하나입니다.

## compose-smoke가 확인하는 것

GitHub의 Ubuntu 실행기는 매번 새로 만들어지는 깨끗한 컴퓨터이고, Docker만 설치되어 있습니다. 여기서 README와 똑같이 `.env` 없이 `docker compose up -d --build`를 실행합니다. 그래서 이 단계가 통과하면 "Docker만 있는 다른 컴퓨터에서도 안내대로 실행된다"는 근거가 됩니다.

1. `/actuator/health/readiness`가 `UP`이 될 때까지 기다립니다(DB 연결 포함).
2. 홈페이지(`/`)와 가구 이미지(`/api/assets/furniture-atlas.png`) 응답을 확인합니다.
3. `/api/rankings`에 샘플 집 4채가 있고 1위가 햇살방인지 확인합니다.
4. `/api/rankings?type=theme`의 콘셉트가 "비 오는 날의 집"인지 확인합니다.
5. `docker compose down` 후 다시 `up` 했을 때 데이터가 남아 있고 샘플이 중복 생성되지 않는지 확인합니다.
6. 마지막에 `docker compose down -v`로 정리합니다. 실패하면 컨테이너 로그를 출력합니다.

## GitHub에서 확인하기

1. [저장소 Actions](https://github.com/dowooju/naranjip/actions)를 엽니다.
2. `Test, publish and compose smoke` 실행을 선택합니다.
3. `test`, `compose-smoke`가 초록색이면 통과입니다. PR에서는 `publish`가 실행되지 않는 것이 정상입니다.

이미지 업로드는 GitHub가 실행마다 제공하는 `GITHUB_TOKEN`을 사용하므로 별도 토큰이 필요 없습니다. 이미지 이름은 `ghcr.io/dowooju/naranjip:sha-전체커밋해시`입니다. 실행에는 이 이미지가 필요 없습니다. `docker compose up -d --build`가 저장소의 Dockerfile로 직접 빌드합니다.

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

코드를 바꾼 뒤에는 `docker compose up -d --build`를 다시 실행하면 새 이미지로 앱만 교체됩니다. DB 데이터는 유지됩니다.

## 이전 방식(Kubernetes)

2026-11 중순까지는 Rancher Desktop Kubernetes에 배포하고, 선택적으로 self-hosted runner로 로컬 CD를 했습니다. Docker Compose로 바꾼 이유는 [README](../README.md#왜-kubernetes-대신-docker-compose인가)에 있습니다.

## 공식 참고 자료

- [Docker Compose](https://docs.docker.com/compose/)
- [GitHub: Docker 이미지 게시](https://docs.github.com/en/actions/tutorials/publish-packages/publish-docker-images)
- [GitHub: GHCR 사용](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry)
