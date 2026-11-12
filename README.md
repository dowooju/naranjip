# 나란집

나란히, 조금씩. 함께 채워갈 우리의 공간.

커플이 같은 집을 실시간으로 함께 꾸미는 웹 서비스입니다. 클라우드 프로그래밍 기말 프로젝트입니다.

## 실행 방법 (처음 받은 사람 기준)

어떤 운영체제든 Docker만 있으면 명령 한 줄로 앱과 DB가 함께 실행됩니다. Java, Maven, PostgreSQL을 따로 설치할 필요가 없습니다.

### 1. 준비물

- **Windows / macOS**: [Docker Desktop](https://www.docker.com/products/docker-desktop/)을 설치하고 실행합니다.
- **Linux**: Docker Engine과 compose 플러그인을 설치합니다([공식 안내](https://docs.docker.com/engine/install/)).
- Git

설치 확인:

```bash
docker compose version
```

### 2. 저장소 받기

```bash
git clone https://github.com/dowooju/naranjip.git
```

```bash
cd naranjip
```

### 3. 실행

```bash
docker compose up -d --build
```

처음에는 이미지 다운로드, 빌드, 테스트 때문에 몇 분 걸립니다. 앱 이미지를 만들 때 Dockerfile 안에서 `mvn verify`로 테스트를 실행하므로, 테스트가 실패하면 앱도 실행되지 않습니다. 두 번째부터는 빨라집니다.

진행 상태 확인:

```bash
docker compose ps
```

```bash
docker compose logs -f app
```

`Started NaranjipApplication`이 보이면 준비된 것입니다(로그 보기는 Ctrl+C로 빠져나옵니다).

### 4. 접속

브라우저에서 <http://localhost:8080>을 엽니다.

### 5. 샘플 집 둘러보기

처음 실행하면 DB 테이블(Flyway)과 구경용 샘플 집 4채가 자동으로 만들어집니다.

- 홈 왼쪽 **명예의 전당**에 햇살방·구름집·달빛집·솔바람집이 하트 순위대로 보입니다.
- **이번 주 콘셉트** 탭에는 "비 오는 날의 집" 순위가 보입니다.
- 순위 카드를 누르면 로그인 없이 집과 방명록을 구경할 수 있습니다.
- 직접 꾸며 보려면 회원가입 후 **새 집 만들기**를 누릅니다. 다른 창(시크릿 창)에서 두 번째 계정을 만들고 초대 코드로 들어오면, 둘이 같은 집을 실시간으로 함께 꾸밀 수 있습니다.

샘플 계정으로 로그인하는 방법은 [샘플 데이터 안내](docs/SAMPLE-DATA.md)를 참고하세요.

### 6. 종료와 초기화

```bash
docker compose down
```

컨테이너만 끕니다. 회원, 집, 가구 데이터는 남아 있어 다시 `docker compose up -d`하면 그대로 이어집니다.

```bash
docker compose down -v
```

DB 볼륨까지 지워 처음 상태로 돌아갑니다. 다음 실행 때 샘플 집이 새로 만들어집니다.

### 잘 안 될 때

| 증상 | 확인할 것 |
| --- | --- |
| `Cannot connect to the Docker daemon` / `docker_engine` 오류 | Docker Desktop이 켜져 있는지 확인 |
| `port is already allocated` (8080) | 8080을 쓰는 다른 프로그램을 끄거나, `docker-compose.yml`의 `"8080:8080"`을 `"8081:8080"`으로 바꾸고 <http://localhost:8081> 접속 |
| 접속이 안 됨 | `docker compose logs app`에서 오류 확인. 첫 실행은 앱 시작까지 30초 정도 걸릴 수 있습니다 |
| `.env`로 비밀번호를 바꾼 뒤 DB 연결 실패 | PostgreSQL은 처음 만들 때만 비밀번호를 정합니다. `docker compose down -v` 후 다시 실행 |

## 공개 서버(EC2)에서 실행할 때: 반드시 새 키 사용

`docker-compose.yml`에는 `.env` 없이도 바로 실행되도록 **로컬 시연 전용 고정 키**가 들어 있습니다. 이 키는 저장소에 공개되어 있으므로 **고정 키로 암호화한 이메일은 누구나 풀 수 있습니다.**

실제 사용자가 접속하는 공개 서버에서는 처음 실행하기 전에 반드시 `.env`를 만들어 새 값을 넣으세요.

```bash
cp .env.example .env
```

`.env` 안의 안내대로 `DB_PASSWORD`, `EMAIL_ENCRYPTION_KEY`, `EMAIL_LOOKUP_KEY`를 채웁니다. 키는 `openssl rand -base64 32`로 하나씩 만듭니다(두 키는 서로 달라야 합니다). `.env`는 `.gitignore`에 있어 Git에 올라가지 않습니다. 키를 바꾸면 기존 이메일을 풀 수 없으므로, 첫 실행 뒤에는 바꾸지 마세요.

같은 Compose 파일을 그대로 씁니다. 앱은 접속한 주소를 기준으로 WebSocket 출처를 확인하므로, `http://서버-IP:8080`으로 접속해도 실시간 편집이 동작합니다. 별도 설정은 필요 없습니다.

## 관리자 지정과 이번 주 콘셉트

기본 관리자 계정은 없습니다. 사이트에서 가입한 계정에 DB에서 직접 권한을 줍니다(예: `sudo1234`). 프로젝트 폴더에서, 운영체제와 상관없이 같은 명령입니다.

```bash
docker compose exec postgres psql -U naranjip -d naranjip -c "UPDATE accounts SET is_admin=TRUE WHERE login_id='sudo1234';"
```

`UPDATE 1`이 나오면 성공, `UPDATE 0`이면 그 아이디로 가입한 계정이 없는 것입니다. 회수는 `TRUE`를 `FALSE`로 바꿔 실행합니다. 콘셉트 등록 방법은 [좋아요·코멘트·관리자 안내](docs/SOCIAL-AND-ADMIN.md)를 참고하세요.

## 구성

```text
브라우저 ──HTTP·WebSocket──▶ app (Spring Boot, 호스트 8080 포트)
                               │ 컨테이너 내부 네트워크
                               ▼
                             postgres (PostgreSQL 17, named volume pgdata, 외부 포트 없음)
```

- `docker-compose.yml`: 위 두 컨테이너 정의. app은 postgres의 healthcheck가 통과한 뒤 시작하고, 꺼지면 자동으로 다시 켜집니다(`restart: unless-stopped`).
- `Dockerfile`: Maven으로 테스트·빌드한 뒤 Java 21 JRE 이미지에 jar와 가구 이미지(`assets/`)를 넣습니다.
- DB 테이블은 앱이 켜질 때 Flyway(`src/main/resources/db/migration`)가 만듭니다.
- 가구 이미지는 저장소의 `assets/furniture-atlas.png`를 그대로 씁니다. S3·RDS 같은 AWS 관리형 서비스는 쓰지 않습니다.

### 왜 Kubernetes 대신 Docker Compose인가

처음에는 Rancher Desktop의 Kubernetes에 배포했지만 Docker Compose 하나로 바꿨습니다.

- 기존 k8s 구성은 앱 1개, DB 1개, 노드 1개였습니다. 그래서 Kubernetes를 써도 가용성이 실제로 늘지 않았습니다.
- 실시간 편집 잠금(`web/LiveEditing.java`)이 앱 메모리에 있습니다. 그래서 앱을 여러 개로 늘릴 수도 없습니다.
- 반면 Windows, Rancher Desktop, PowerShell 스크립트에 묶여 있어서 다른 PC에서 재현하기 어려웠습니다.
- 그래서 같은 노력을 "누구나 명령 한 줄로 실행"과 외부 접속(EC2)에 쓰기로 했습니다. 로컬과 EC2 모두 `docker compose up -d --build`로 실행합니다.

## 기능

- 홈: 명예의 전당 / 이번 주 콘셉트 순위, 로그인·회원가입
- 회원가입·로그인: 비밀번호는 BCrypt 해시, 이메일은 AES-256-GCM 암호문과 중복 확인용 HMAC으로 저장([DB 설계·암호화](docs/AUTH-DATABASE.md))
- 8×8 집, 새 집 만들기 또는 7자리·24시간·1회용 초대 코드로 커플 집 합치기([커플 초대](docs/COUPLE-INVITES.md))
- 꾸미기: 가구 11종 드래그 배치·이동·회전·삭제, 배치 규칙 검사([꾸미기](docs/ROOM-EDITOR.md), [집 DB](docs/HOUSE-DATABASE.md))
- 커플 실시간 공동 편집(WebSocket), 같은 가구는 먼저 잡은 사람이 편집([실시간 공동 편집](docs/REALTIME-EDITING.md))
- 냉장고 쪽지 200자, 방문객 공개 여부 선택([쪽지](docs/NOTES.md))
- 일반·콘셉트 하트, 방명록, 이웃집 방문, 관리자 콘셉트 등록([좋아요·코멘트·관리자](docs/SOCIAL-AND-ADMIN.md))

세션은 2시간 비활동 또는 앱 재시작 시 만료됩니다. 저장된 집과 쪽지는 DB에 남습니다.

## 기술

Java 21 / Spring Boot 3.5 / Spring Security / JDBC / PostgreSQL 17 / Flyway / Maven Wrapper / HTML·CSS·JavaScript / Docker Compose / GitHub Actions

## 개발자용

테스트는 H2 메모리 DB와 테스트 전용 키를 써서 DB 없이 실행됩니다(Java 21 필요).

```bash
./mvnw -B verify
```

```bash
node --test scripts/placement.test.mjs scripts/auth-validation.test.mjs
```

Windows 명령 프롬프트·PowerShell에서는 `./mvnw` 대신 `.\mvnw.cmd`를 씁니다.

GitHub Actions는 main 푸시와 PR마다 테스트 → `docker compose up -d --build` 후 실제 접속·샘플 데이터 확인(스모크)을 실행하고, main에서는 이미지를 GHCR에 올립니다. [CI 안내](docs/CI-CD.md)를 참고하세요.

| 위치 | 역할 |
| --- | --- |
| `docker-compose.yml`, `.env.example` | 실행 구성과 공개 서버용 비밀값 예시 |
| `Dockerfile` | 테스트·빌드·실행 이미지 |
| `src/main/java/com/naranjip/web/` | API 컨트롤러, 보안 설정, 실시간 편집(WebSocket) |
| `src/main/java/com/naranjip/application/` | 가구 카탈로그·배치 규칙·서비스 |
| `src/main/java/com/naranjip/infrastructure/` | PostgreSQL 저장소, 이메일 암호화, 이미지 저장소, 샘플 데이터 |
| `src/main/resources/db/migration/` | Flyway DB 스키마 |
| `src/main/resources/static/` | 화면(HTML·CSS·JS) |
| `assets/furniture-atlas.png` | 가구 11종 이미지 |
| `src/test/java/com/naranjip/` | 인증·암호화·CSRF·집 접근·배치·실시간 편집 테스트 |
| `scripts/*.mjs` | 브라우저 스모크 테스트(테스트 계정을 만들므로 운영 DB에는 실행하지 않음) |
| `.github/workflows/ci-cd.yml` | 테스트 → Compose 스모크 → GHCR 이미지 |

검증 기록은 [검증 기록](docs/VERIFICATION.md)에 있습니다.
