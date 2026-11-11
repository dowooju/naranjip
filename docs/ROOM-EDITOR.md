# 전체 화면 꾸미기

## 사용 방법

1. 회원가입 후 로그인 → 우리집 → 수정하기.
2. 편집 화면은 상단 사이트 메뉴와 홈 카드를 숨기고 브라우저 전체 영역을 사용합니다.
3. 오른쪽 가구를 방으로 드래그합니다. 클릭하면 방 중앙의 빈자리에 자동 배치됩니다. 액자와 쪽지는 화면에서 가장 왼쪽에 있는 부착 가능한 가구로 갑니다.
4. 파란 반투명 칸은 놓을 수 있는 위치입니다. 회색 칸은 불가능한 위치입니다. 놓으면 서버에 즉시 저장합니다.
5. 기존 가구도 드래그해서 옮깁니다. 방 바깥이나 겹치는 곳에 놓으면 저장하지 않고 원래 배치를 유지합니다.
6. 가구 클릭/우클릭 → ↻ 회전, ⌫ 삭제. 회전하면 기본/반대 방향을 전환하고 점유 너비·높이가 바뀝니다. Esc는 선택/이동 취소입니다.
7. 꾸미기 완료 → 집 구경 화면. 재로그인·서버 재시작 후에도 저장된 배치가 유지됩니다.

## 가구와 배치 규칙

| 가구 | 바닥 크기 / 제약 |
| --- | --- |
| 의자 | 1×1 |
| 책상 | 2×1, 액자 최대 2개 |
| 소파 | 2×1 |
| 식탁 | 2×2 |
| 냉장고 | 1×1, 쪽지 최대 3개 |
| 문 | 왼쪽/오른쪽 벽 위에만 배치, 벽에 따라 방향 자동 결정 |
| 액자 | 책상에만 부착, 사진 등록 기능 없음 |
| 쪽지 | 냉장고에만 부착, 200자 내용·공개 여부 편집 |
| 작은 화분 / 큰 화분 | 각각 1×1 |
| 러그 | 3×2, 일반 가구 아래에 둘 수 있음, 러그끼리는 겹침 금지 |

같은 모델을 여러 번 배치할 수 있습니다. 문은 벽 레이어, 러그는 바닥 아래 레이어라 일반 가구와 독립적으로 점유를 검사합니다. 액자·쪽지는 부모 가구를 따라다닙니다. 액자나 쪽지가 붙은 부모 가구를 지울 때는 함께 삭제한다는 확인창이 나옵니다.

가구 그림은 PNG atlas에서 가져옵니다. 현재 한 시점의 그림과 좌우 반전으로 두 시각 방향을 표현합니다. 회전값은 false(기본)/true(반대) 두 가지입니다. 배경 방은 기존 SVG를 유지합니다.

## PostgreSQL 저장 구조

- houses에 소유 회원과 revision, placed_furniture에 모든 회원의 가구를 house_id로 구분해 저장합니다.
- 위치·방향·문 벽·부착 부모/슬롯·표시 순서를 저장합니다. [컬럼과 저장 방식](HOUSE-DATABASE.md).
- 집별 DB 행 잠금과 트랜잭션으로 변경을 확정합니다. 오래된 revision의 요청은 409로 거절하며 클라이언트는 최신 상태를 조회합니다.
- 집 접근은 로그인 세션의 집 ID를 확인합니다. 다른 회원의 집을 조회/수정할 수 없습니다.
- 계정당 집 하나, 집당 최대 150개 물건입니다. 로그아웃해도 집을 삭제하지 않습니다.
- 세션은 서버 메모리이므로 서버 재시작 후 다시 로그인해야 합니다. 배치는 DB에서 복구됩니다.
- 저장 중 새 변경을 막고 완료 버튼은 응답을 기다립니다. 연결 오류로 저장 여부가 불명확하면 조회로 복구합니다.
- WebSocket 공동 편집·초대는 아직 구현하지 않았습니다.

## 이미지 저장소와 S3 전환

```text
FurnitureController
  → application.AssetStorage.open("furniture-atlas.png")
  → infrastructure.LocalAssetStorage
  → assets/furniture-atlas.png
```

단일 이미지 파일 `assets/furniture-atlas.png`에 가구 11종을 담았습니다. 4열 × 3행이고 마지막 칸은 비어 있습니다. `FurnitureCatalog`의 column/row로 영역을 찾습니다. 클라이언트는 `/api/catalog`가 제공하는 URL을 사용하며 로컬 디렉터리를 직접 참조하지 않습니다.

로컬 실행은 저장소 루트에서 실행하세요. 다른 폴더를 쓰려면 `ASSET_ROOT` 환경 변수로 경로를 지정합니다. Docker 이미지는 `/app/assets`에 파일을 포함합니다.

나중에 S3를 연결할 때:

1. `infrastructure/S3AssetStorage`에서 같은 `AssetStorage` 인터페이스를 구현합니다.
2. `open(key)`를 S3 SDK의 `getObject(bucket, key)` 스트림으로 연결합니다.
3. S3 구현에 `@ConditionalOnProperty(name="app.asset-storage", havingValue="s3")`를 붙입니다.
4. bucket/region은 설정으로, 자격 증명은 IAM 역할 또는 SDK의 기본 자격 증명 체인으로 제공합니다.
5. `ASSET_STORAGE=s3`로 바꾸면 local 구현이 제외됩니다. Controller·가구 카탈로그·화면은 그대로 둡니다.

S3 SDK와 S3 구현은 이번 버전에 포함하지 않았습니다. `ASSET_STORAGE=s3`는 해당 구현을 추가한 뒤 사용해야 합니다.

## 검증

```powershell
.\mvnw.cmd -B verify
node --test scripts/placement.test.mjs
```

로컬 선택형 E2E는 테스트 전용 Chrome을 `--headless=new --remote-debugging-port=9223 --user-data-dir=별도폴더`로 켠 뒤:

```powershell
node scripts/browser-smoke.mjs http://localhost:8082 target/browser-check
```

이 스크립트는 테스트 계정을 만들고 로그인/가입 오류와 편집기 추가·삭제를 확인합니다. 집 저장 회귀 검사는 HousePersistenceTest와 scripts/house-persistence-smoke.mjs를 사용합니다. 출력 스크린샷은 Git에 넣지 않습니다.

같은 집 멤버끼리 동시에 수정하기에 들어가면 변경이 실시간 반영됩니다. [공동 편집](REALTIME-EDITING.md)과 [쪽지](NOTES.md)를 참고하세요.
