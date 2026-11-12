# 초대 코드 검증 기록 — 2026-11-10

> **k8s 시절 기록**: 아래는 Rancher Desktop Kubernetes로 실행하던 때의 검증 기록입니다. 지금은 Docker Compose로 실행합니다. Pod·PVC·port-forward 같은 표현은 당시 환경을 뜻합니다.

- 기준 문서 01·02·03을 읽고 아이디 신청 방식 대신 7자리 초대 코드로 구현.
- Docker Maven verify: Java 17개 성공. Node 입력/배치 테스트 5개 성공.
- CoupleFlowTest: 집 없는 수락, 기존 집 삭제 동의, 두 멤버 읽기/수정, 제3자 차단, CSRF, 이메일/비밀번호 미노출, 만료·재발급·취소, 같은 코드 동시 수락, 미리보기 후 소속 변경, DB 오류 시 기존 집/가구 삭제 롤백.
- 별도 PostgreSQL 검증 DB에서 V2 계정에 가구 9개/revision 10 저장 후 V3로 이전. 전체 배치 일치.
- 실제 Chrome: 새 집 생성, 초대 코드 발급, 삭제 확인 취소 시 기존 가구 유지, 수락 후 기존 집 404, 두 사람 프로필, 각자 편집, 재로그인 시 동일 집.
- 검증 앱 Pod 재시작 후 양쪽 계정의 소속 집·상대 프로필·가구 전체 일치.
- 실시간 WebSocket 공동 편집과 두 기기 LAN 접속은 이번 검증 범위가 아님.

실행 스크립트: scripts/couple-browser-smoke.mjs, scripts/couple-persistence-smoke.mjs.
스크린샷과 테스트 계정 정보는 로컬 검증 폴더에만 저장하고 Git에는 포함하지 않음.
