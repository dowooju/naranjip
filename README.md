# 나란집

나란히, 조금씩. 함께 채워갈 우리의 공간.

클라우드 프로그래밍 개발문서 01·02·03의 구조를 바탕으로, **화면 이동과 Kubernetes 배포를 먼저 경험하는 최소 버전**입니다.

## 구현된 흐름

- 홈: 왼쪽 명예의 전당 / 이번 주 콘셉트 탭(내용은 준비 중), 가운데 빈 집, 오른쪽 로그인
- 회원가입 및 로그인: 이메일·아이디·비밀번호로 가입 후 실제 계정 검증. 빈 입력은 필드 아래 빨간 글씨로 안내
- 로그인 후 우리집 가기, 8×8 고정 시점 빈 집, 확대·축소
- 전체 화면 꾸미기: 가구 11종 드래그 배치·이동·두 방향 전환·삭제·파란 배치 미리보기
- 문은 벽에만 배치, 액자는 책상·쪽지는 냉장고에 부착, 러그는 가구 아래 배치
- 좋아요, 콘셉트 좋아요, 코멘트 버튼: 준비 중 안내만 표시
- 새로고침 시 세션 유지, 로그아웃, 세션 없는 집 접근 차단

회원 정보는 PostgreSQL에 저장합니다. 비밀번호는 BCrypt 해시, 이메일은 AES-256-GCM 암호문과 중복 확인용 HMAC으로 보호합니다. [DB 설계·암호화·실행 안내](docs/AUTH-DATABASE.md)를 확인하세요.

집/가구 배치도 PostgreSQL에 자동 저장합니다. 다시 로그인하거나 앱·DB Pod를 재시작해도 같은 집을 불러옵니다. 회전은 boolean 두 방향입니다. [집 DB 설계](docs/HOUSE-DATABASE.md)를 확인하세요. 세션은 2시간 비활동 또는 서버 재시작 시 만료됩니다. 초대, 공동 편집, 순위 집계, 댓글 저장은 후속 단계입니다.

꾸미기 사용 방법, 배치 규칙, DB 저장, S3 교체 구조는 **[꾸미기 개발 문서](docs/ROOM-EDITOR.md)**에서 확인하세요. 가구 이미지는 **[단일 PNG atlas](assets/furniture-atlas.png)**입니다.

## 기술

Java 21 / Spring Boot 3.5 / Spring Security / JDBC / PostgreSQL 17 / Flyway / Maven Wrapper / HTML·CSS·JavaScript / Docker / Kubernetes / GitHub Actions.

## 가장 빠른 실행: Rancher Desktop

Rancher Desktop을 실행하고 Kubernetes가 Ready인지 확인하세요. 컨테이너 엔진은 **Moby(dockerd)** 기준입니다. Windows PowerShell에서:

```powershell
Set-Location -LiteralPath 'C:\Users\LG PC\Desktop\github\naranjip'
.\scripts\init-auth-secret.ps1
.\scripts\deploy.ps1 -Build
kubectl --context rancher-desktop -n room-demo port-forward service/naranjip 8080:80
```

브라우저에서 <http://localhost:8080>을 엽니다. port-forward 터미널은 유지하세요. 재배포 후 연결이 끊어지면 port-forward를 다시 실행합니다. Java가 PC에 없어도 Docker가 빌드합니다.

Docker context가 다른 PC는 `docker context ls`로 확인한 뒤 `-DockerContext 이름`을 지정하세요. 이 PC는 `default`를 사용합니다. 클러스터가 컨테이너 이미지를 공유하지 않는 환경은 GHCR 이미지를 배포해야 합니다.

## Java로 실행

Java 21 설치 후 JAVA_HOME과 PATH를 설정하세요. Maven은 Wrapper가 자동 다운로드합니다.

```powershell
.\mvnw.cmd -B verify
# DB_URL, DB_USERNAME, DB_PASSWORD, EMAIL_ENCRYPTION_KEY, EMAIL_LOOKUP_KEY 설정 필요
.\mvnw.cmd spring-boot:run
```

macOS/Linux: 동일한 환경변수를 설정하고 `./mvnw spring-boot:run`. 테스트는 H2와 테스트 전용 키를 사용하므로 실제 DB 비밀정보가 필요하지 않습니다.

## 파일 위치

| 위치 | 역할 |
| --- | --- |
| `src/main/java/com/naranjip/web/AuthController.java` | 회원가입·로그인·세션·집 소유권 API |
| `src/main/resources/static/app.js` | 홈/가입/우리집/수정 화면과 이벤트 |
| `src/main/resources/static/room.js` | 가구 없는 8×8 SVG 렌더링 |
| `src/main/resources/static/editor.js`, `furniture-scene.js` | 전체 화면 편집기와 PNG 가구 렌더링 |
| `src/main/java/com/naranjip/application/` | 가구 카탈로그·배치 규칙·저장소 인터페이스 |
| `src/main/java/com/naranjip/infrastructure/` | 로컬 이미지 접근·PostgreSQL 회원/집 저장소 |
| `assets/furniture-atlas.png` | 생성한 가구 11종 단일 이미지 파일 |
| `src/main/resources/static/styles.css` | 반응형 화면 스타일 |
| `src/test/java/com/naranjip/` | 실제 인증·암호화 저장·CSRF·집 접근·배치 테스트 |
| `Dockerfile`, `k8s/`, `scripts/deploy.ps1` | 컨테이너 빌드·배포·상태 확인 |
| `.github/workflows/ci-cd.yml` | 테스트 → GHCR 이미지 → 선택형 로컬 CD |

처음 하는 CI/CD는 **[단계별 안내](docs/CI-CD.md)**를 따라가세요. 실제 확인 기록은 [검증 기록](docs/VERIFICATION.md)에 남깁니다.
