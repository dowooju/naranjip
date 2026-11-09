# 나란집

나란히, 조금씩. 함께 채워갈 우리의 공간.

클라우드 프로그래밍 개발문서 01·02·03의 구조를 바탕으로, **화면 이동과 Kubernetes 배포를 먼저 경험하는 최소 버전**입니다.

## 구현된 흐름

- 홈: 왼쪽 명예의 전당 / 이번 주 콘셉트 탭(내용은 준비 중), 가운데 빈 집, 오른쪽 로그인
- 회원가입 및 로그인: 임의의 아이디로 입장. 비밀번호와 이메일은 서버로 보내거나 저장하지 않음
- 로그인 후 우리집 가기, 8×8 고정 시점 빈 집, 확대·축소
- 수정하기 화면 진입 및 꾸미기 완료로 돌아오기
- 좋아요, 콘셉트 좋아요, 코멘트 버튼: 준비 중 안내만 표시
- 새로고침 시 세션 유지, 로그아웃, 세션 없는 집 접근 차단

**실제 회원가입/인증이 아닙니다.** 아이디도 계정 DB에 저장하지 않습니다. 로그인마다 새 체험용 집 ID를 만들며 로그아웃·세션 만료(비활동 2시간)·서버/Pod 재시작 시 세션과 집은 사라집니다. 같은 아이디로 다른 브라우저에서 들어와도 별개 세션입니다. DB, 가구, 초대, 공동 편집, 순위 집계, 댓글 저장은 후속 단계입니다.

## 기술

Java 21 / Spring Boot 3.5 / Maven Wrapper / HTML·CSS·JavaScript / SVG / Docker / Kubernetes / GitHub Actions. PostgreSQL은 영속 데이터 기능을 만들 때 추가합니다. Google Fonts 연결이 안 되면 시스템 글꼴을 사용합니다.

## 가장 빠른 실행: Rancher Desktop

Rancher Desktop을 실행하고 Kubernetes가 Ready인지 확인하세요. 컨테이너 엔진은 **Moby(dockerd)** 기준입니다. Windows PowerShell에서:

```powershell
Set-Location -LiteralPath 'C:\Users\LG PC\Desktop\github\naranjip'
.\scripts\deploy.ps1 -Build
kubectl --context rancher-desktop -n room-demo port-forward service/naranjip 8080:80
```

브라우저에서 <http://localhost:8080>을 엽니다. port-forward 터미널은 유지하세요. 재배포 후 연결이 끊어지면 port-forward를 다시 실행합니다. Java가 PC에 없어도 Docker가 빌드합니다.

Docker context가 다른 PC는 `docker context ls`로 확인한 뒤 `-DockerContext 이름`을 지정하세요. 이 PC는 `default`를 사용합니다. 클러스터가 컨테이너 이미지를 공유하지 않는 환경은 GHCR 이미지를 배포해야 합니다.

## Java로 실행

Java 21 설치 후 JAVA_HOME과 PATH를 설정하세요. Maven은 Wrapper가 자동 다운로드합니다.

```powershell
.\mvnw.cmd -B verify
$env:DEMO_AUTH_ENABLED='true'
.\mvnw.cmd spring-boot:run
```

macOS/Linux: `DEMO_AUTH_ENABLED=true ./mvnw spring-boot:run`. 개발용 로그인은 기본으로 꺼져 있으며 Kubernetes 매니페스트에서 명시적으로 켭니다.

## 파일 위치

| 위치 | 역할 |
| --- | --- |
| `src/main/java/com/naranjip/web/DemoController.java` | 개발용 로그인·회원가입·세션·빈 집 API |
| `src/main/resources/static/app.js` | 홈/가입/우리집/수정 화면과 이벤트 |
| `src/main/resources/static/room.js` | 가구 없는 8×8 SVG 렌더링 |
| `src/main/resources/static/styles.css` | 반응형 화면 스타일 |
| `src/test/java/com/naranjip/` | 세션, 집 접근, 가입, 개발 모드 테스트 |
| `Dockerfile`, `k8s/`, `scripts/deploy.ps1` | 컨테이너 빌드·배포·상태 확인 |
| `.github/workflows/ci-cd.yml` | 테스트 → GHCR 이미지 → 선택형 로컬 CD |

처음 하는 CI/CD는 **[단계별 안내](docs/CI-CD.md)**를 따라가세요. 실제 확인 기록은 [검증 기록](docs/VERIFICATION.md)에 남깁니다.
