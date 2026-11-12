# PostgreSQL 회원 인증

## 이번 범위
아이디/비밀번호 로그인, 이메일/아이디/비밀번호 회원가입, 필드별 오류, 로그아웃, PostgreSQL 회원 저장.
집/가구 배치도 PostgreSQL에 저장합니다. 상세 스키마는 [집 DB 설계](HOUSE-DATABASE.md)를 참고하세요. 이메일 인증과 계정 복구는 다음 단계입니다.

## 회원 테이블
Flyway가 `V1__create_accounts.sql`을 최초 실행하고 이력을 기록합니다.

| 컬럼 | 용도 |
|---|---|
| id (UUID, PK) | 내부 회원 식별자 |
| login_id (varchar20, UNIQUE) | 영문 소문자로 정규화한 로그인/표시 아이디 |
| email_ciphertext (text) | v1: 접두사 + Base64(12바이트 nonce + 암호문 + 인증 태그) |
| email_lookup_hash (varchar64, UNIQUE) | 정규화 이메일의 HMAC-SHA256, 중복 검사 |
| password_hash (varchar100) | BCrypt cost 12, 자동 무작위 salt 포함 |
| created_at (timestamptz) | 가입 시각 |

아이디는 한글·영문·숫자·밑줄 3~20자, 이메일은 필수입니다. 아이디·이메일의 영문 대소문자는 구분하지 않습니다. 비밀번호는 공백을 임의로 제거하지 않으며 8자 이상, UTF-8 72바이트 이내입니다(BCrypt 입력 길이 제한).

## 어떻게 보호하는가
- 비밀번호: **암호화 대신 단방향 BCrypt 해시**. 원문을 저장하지 않으며 복호화 기능/키가 없습니다. 로그인 때 입력 비밀번호를 BCrypt 검증 함수로 비교합니다. 같은 비밀번호도 무작위 salt 때문에 다른 해시가 됩니다.
- 이메일: **AES-256-GCM**. 매번 새로운 96비트 nonce와 128비트 인증 태그를 사용합니다. 암호문 조작도 탐지하도록 AAD를 지정합니다.
- 이메일 검색: 무작위 nonce 때문에 암호문으로 중복 검색할 수 없으므로 별도의 **HMAC-SHA256** 값에 UNIQUE 제약을 둡니다. 단순 SHA256과 달리 검색용 비밀 키가 필요합니다.
- 아이디: 공개 표시 식별자라 평문 저장합니다. 모든 필드를 암호화했다고 주장하지 않습니다.
- DB 비밀번호, AES 키, HMAC 키: 환경변수로 주입합니다. `docker-compose.yml`에는 `.env` 없이 바로 실행되도록 **로컬 시연 전용 고정 키**가 기본값으로 들어 있습니다. 이 값은 공개 저장소에 있으므로 공개 서버에서는 반드시 `.env`에 각각 독립적인 32바이트 난수(`openssl rand -base64 32`)를 넣어야 합니다. `.env`는 Git에 올리지 않습니다. 운영 수준에서는 백업 접근 제한과 Secret Manager/KMS도 함께 고려해야 합니다.
- 브라우저: HttpOnly/SameSite=Lax 세션 쿠키, 로그인 시 새 세션, 변경 요청에 Spring Security CSRF 토큰. 비밀번호/키/이메일을 응답하거나 localStorage에 저장하지 않습니다.
- 로컬 주소는 HTTP입니다. DB 저장 보호와 전송 암호화는 별개이므로 외부 서비스에서는 HTTPS와 `SESSION_COOKIE_SECURE=true`를 사용해야 합니다.

키를 삭제/교체하면 기존 암호문이나 이메일 중복 확인이 깨집니다. DB와 함께 키를 안전하게 백업하고, 키 교체 시 별도 마이그레이션을 설계해야 합니다. 현재 v1은 키 회전 작업을 자동 수행하지 않습니다.

## 실행
```bash
docker compose up -d --build
```
PostgreSQL 17 컨테이너와 named volume(`pgdata`)을 사용하여 컨테이너가 다시 만들어져도 회원 DB가 유지됩니다. `docker compose down -v`로 볼륨을 지우는 경우까지 보존되는 백업은 아닙니다.
DB는 Compose 내부 네트워크에서만 접속할 수 있고 호스트로 포트를 노출하지 않습니다.
로컬 Java 실행 시 DB_URL, DB_USERNAME, DB_PASSWORD, EMAIL_ENCRYPTION_KEY, EMAIL_LOOKUP_KEY가 필요합니다.

## 검증
```sh
./mvnw -B verify
node --test scripts/placement.test.mjs scripts/auth-validation.test.mjs
```
자동 테스트의 H2는 PostgreSQL 호환 모드이며 테스트 전용 고정 키만 사용합니다. 실제 PostgreSQL 연결, 재시작 후 로그인, 브라우저 입력 오류/회원가입/로그인은 별도로 확인합니다.
브라우저 회귀 검사는 Chrome 디버그 포트 9223을 사용하는 `scripts/auth-browser-smoke.mjs`로 실행합니다.

## 근거
- [Spring Security password storage](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html)
- [OWASP password storage](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)
- [OWASP cryptographic storage](https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html)
