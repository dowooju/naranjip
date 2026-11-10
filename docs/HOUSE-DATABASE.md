# 집 배치 저장

모든 회원의 가구는 하나의 placed_furniture 테이블에 저장하고 house_id로 구분합니다. 그림은 기존 PNG atlas를 그대로 사용하며 DB에 이미지 바이너리를 넣지 않습니다. S3 없이도 배치 저장이 가능합니다.

최신 커플 멤버십과 초대는 [커플 초대 설계](COUPLE-INVITES.md)를 참고하세요. V3부터 houses.owner_id 대신 house_members로 소유 관계를 관리하며 가입 후 새 집 만들기 또는 초대 수락을 선택합니다. 아래는 V2 도입 시점의 구조입니다.

## 테이블

Flyway V2__persist_house_layouts.sql이 기존 accounts를 보존하고 아래 테이블을 추가합니다. 기존 회원에게 빈 집을 생성하고 신규 회원의 집은 가입 시 생성합니다. 이전 서버 메모리의 배치는 이 마이그레이션으로 이전되지 않습니다.

| 테이블 | 컬럼 | 의미 |
| --- | --- | --- |
| houses | id, owner_id | 집 UUID, 소유 회원 FK(UNIQUE: 회원당 집 하나) |
| houses | revision, updated_at | 수정 버전과 마지막 저장 시각 |
| placed_furniture | id, house_id | 배치된 물건 UUID, 소속 집 FK |
| placed_furniture | type_id | 카탈로그 가구 종류. 같은 종류도 여러 개 배치 가능 |
| placed_furniture | x, y | 8×8 바닥 좌표 |
| placed_furniture | rotation BOOLEAN | false: 기본 방향, true: 반대 방향 |
| placed_furniture | wall | 문이 붙은 left/right 벽, 일반 가구는 NULL |
| placed_furniture | parent_id, slot | 액자·쪽지가 붙은 가구 UUID와 부착 자리 |
| placed_furniture | sort_order | 같은 깊이에서 그리는 순서를 보존 |

문은 왼쪽 벽이면 true, 오른쪽 벽이면 false입니다. 액자·쪽지는 자체 rotation=false, x=y=0으로 저장하고 부모의 위치·방향을 따라 그립니다. 부모 이동/회전 시 부착 관계가 유지됩니다. 프런트엔드와 API도 boolean을 사용하며 숫자 각도나 문자열은 거절합니다.

## 저장 방식

배치·이동·방향 전환·삭제마다 즉시 자동 저장합니다. 집 행을 SELECT FOR UPDATE로 잠그고 요청 revision을 검사한 뒤 최대 150개 물건을 한 트랜잭션에서 교체합니다. 부모 가구부터 삽입하므로 부착 FK가 유지됩니다. 오류 시 전체 변경이 롤백되며 오래된 탭은 409 응답 후 최신 집을 조회합니다.

house_id 인덱스로 해당 집만 조회합니다. 복합 FK는 다른 집의 가구에 액자·쪽지를 붙이는 것을 막습니다. 수정 API는 세션의 소유 집을 검사하며 클라이언트가 보낸 회원 ID를 신뢰하지 않습니다.

로그아웃은 세션만 제거합니다. 재로그인하면 동일한 회원 UUID의 집을 DB에서 읽습니다. 앱·PostgreSQL Pod가 재시작돼도 PVC의 데이터가 유지됩니다. PVC/클러스터 삭제에 대비한 별도 백업은 아직 없습니다.

## 검증

- Java HousePersistenceTest: 재로그인 시 배치·방향·부착·순서 복원, 잘못된 방향 입력 거절, 다른 집 접근 차단, 실패한 수정의 원상 유지, 부모/부착 삭제, 동시 수정 충돌.
- 실제 PostgreSQL 검증: scripts/house-persistence-smoke.mjs로 준비 → 앱/DB 재시작 → 검증. 테스트 계정은 별도 생성합니다.
- 이미지와 infrastructure의 LocalAssetStorage는 변경하지 않습니다.
