# DB와 보안

PostgreSQL 17 컨테이너 하나에 모든 데이터를 저장합니다. 테이블은 앱이 켜질 때 Flyway(`src/main/resources/db/migration/V1~V5`)가 만듭니다. 데이터는 볼륨 `pgdata`에 남고, `docker compose down -v`를 해야 지워집니다. DB 포트는 외부에 열지 않습니다.

## 테이블

| 마이그레이션 | 테이블 | 내용 |
| --- | --- | --- |
| V1 | `accounts` | 회원. 아이디, 이메일 암호문, 이메일 검색용 HMAC, BCrypt 비밀번호 해시 |
| V2 | `houses`, `placed_furniture` | 집(revision 포함)과 가구 위치·방향·문 벽·부착 부모/슬롯 |
| V3 | `house_members`, `invites` | 집 멤버(한 계정 한 집, 한 집 최대 2명)와 초대 코드 |
| V4 | `themes`, `active_theme`, `general_likes`, `theme_likes`, `guestbook`, `accounts.is_admin` | 콘셉트, 하트, 방명록, 관리자 여부 |
| V5 | `note_contents` | 쪽지 본문·공개 여부·revision |

## 개인정보 보호

| 항목 | 저장 방법 |
| --- | --- |
| 비밀번호 | **BCrypt 해시**(cost 12, 무작위 salt). 원문을 저장하지 않고 되돌릴 수 없습니다. |
| 이메일 | **AES-256-GCM 암호문**. 매번 새 nonce를 써서 같은 이메일도 암호문이 다릅니다. |
| 이메일 중복 확인 | 암호문으로는 비교할 수 없어서 **HMAC-SHA256** 값에 UNIQUE 제약을 둡니다. |
| 아이디 | 화면에 보이는 공개 이름이라 평문으로 저장합니다. |
| 로그인 세션 | Spring Session이 **Redis**에 저장합니다(쿠키 `SESSION`, HttpOnly, SameSite=Lax). 앱이 여러 개여도 같은 로그인을 봅니다. |

- 변경 요청(POST·PUT·DELETE)은 모두 Spring Security **CSRF 토큰**을 검사합니다.
- 암호화 키(`EMAIL_ENCRYPTION_KEY`, `EMAIL_LOOKUP_KEY`)와 `DB_PASSWORD`는 환경변수로 넣습니다. `docker-compose.yml`의 기본값은 **로컬 시연 전용** 공개 키입니다. 공개 서버에서는 반드시 `.env`에 `openssl rand -base64 32`로 만든 새 키를 넣으세요. 첫 실행 뒤 키를 바꾸면 기존 이메일을 풀 수 없습니다.
- 지금은 HTTP입니다. HTTPS를 붙이면 `SESSION_COOKIE_SECURE=true`로 쿠키를 HTTPS 전용으로 바꿀 수 있습니다.

## 동시 수정 보호

- 가구를 저장할 때 `houses` 행을 `SELECT ... FOR UPDATE`로 잠그고, 요청의 revision이 최신인지 확인한 뒤 한 트랜잭션으로 저장합니다. 오래된 화면의 요청은 409로 거절되고 화면이 최신 배치를 다시 불러옵니다.
- 저장할 때는 바뀐 가구 행만 씁니다(새 가구 INSERT, 옮기거나 돌린 가구만 UPDATE, 지운 가구 DELETE). 행을 지우지 않고 갱신하므로 냉장고를 옮겨도 쪽지 내용이 유지됩니다.
- 초대 수락은 계정·집을 잠근 한 트랜잭션에서 인원 제한, 코드 만료·사용 여부를 다시 확인하고, 이전 집 삭제와 멤버 등록을 함께 처리합니다.
- 모든 API는 클라이언트가 보낸 회원 ID가 아니라 **세션의 계정**으로 집 멤버인지 확인합니다.

## API 요약

| 기능 | API |
| --- | --- |
| 회원 | `POST /api/auth/register`, `/login`, `/logout`, `GET /api/me`, `GET /api/csrf` |
| 집·초대 | `GET /api/couple`, `POST /api/houses`, `POST·DELETE /api/houses/{id}/invite`, `POST /api/invites/preview`, `/accept` |
| 가구 | `GET /api/catalog`, `POST /api/houses/{id}/furniture`, `PUT·DELETE .../furniture/{fid}` |
| 쪽지 | `GET /api/houses/{id}/notes`, `GET·PUT .../notes/{nid}` |
| 소셜 | `GET /api/rankings`, `/api/theme`, `/api/visits/{id}`, `/api/houses/random`, `PUT·DELETE /api/houses/{id}/likes/general` 또는 `theme`, 방명록 `GET·POST /api/houses/{id}/guestbook`, `DELETE /api/guestbook/{id}` |
| 관리자 | `GET·POST /api/admin/themes` |
| 실시간 편집 | `POST /api/live/ticket`, WebSocket `/ws/edit` |
