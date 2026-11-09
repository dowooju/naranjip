# 전체 화면 꾸미기

## 사용 방법

1. 아무 아이디로 로그인 → 우리집 → 수정하기.
2. 편집 화면은 상단 사이트 메뉴와 홈 카드를 숨기고 브라우저 전체 영역을 사용합니다.
3. 오른쪽 가구를 방으로 드래그합니다. 클릭으로 가구를 선택한 뒤 방을 클릭해도 됩니다.
4. 붉은 반투명 칸은 놓을 수 있는 위치입니다. 회색 칸은 불가능한 위치입니다. 놓으면 서버에 즉시 저장합니다.
5. 기존 가구도 드래그해서 옮깁니다. 방 바깥이나 겹치는 곳에 놓으면 저장하지 않고 원래 배치를 유지합니다.
6. 가구 클릭/우클릭 → ↻ 회전, ⌫ 삭제. 회전하면 90도 단위로 점유 크기가 바뀝니다. Esc는 선택/이동 취소입니다.
7. 꾸미기 완료 → 집 구경 화면. 새로고침해도 같은 세션에서는 배치가 유지됩니다.

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
| 쪽지 | 냉장고에만 부착, 내용 편집 기능 없음 |
| 작은 화분 / 큰 화분 | 각각 1×1 |
| 러그 | 3×2, 일반 가구 아래에 둘 수 있음, 러그끼리는 겹침 금지 |

같은 모델을 여러 번 배치할 수 있습니다. 문은 벽 레이어, 러그는 바닥 아래 레이어라 일반 가구와 독립적으로 점유를 검사합니다. 액자·쪽지는 부모 가구를 따라다닙니다. 액자나 쪽지가 붙은 부모 가구를 지울 때는 함께 삭제한다는 확인창이 나옵니다.

가구 그림은 PNG atlas에서 가져옵니다. 현재 한 시점의 그림과 좌우 반전으로 두 시각 방향을 표현합니다. 논리 회전/점유 판정은 0·90·180·270도이며, 완전한 4방향 앞/뒤 그림은 이후 atlas를 확장할 수 있습니다. 배경 방은 기존 SVG를 유지합니다.

## 서버 메모리 구조

- `application/FurnitureService`: 경계·겹침·회전·문·부착 제한을 검증하고 변경을 원자적으로 확정합니다.
- `application/HouseRepository`: 저장소 계약.
- `infrastructure/InMemoryHouseRepository`: 서버 메모리 구현. 재시작 시 전부 초기화됩니다.
- 집별 monitor와 revision으로 동시 요청을 직렬화합니다. 오래된 revision의 요청은 409로 거절하며 클라이언트는 최신 상태를 조회합니다.
- 집 접근은 세션의 집 ID를 확인합니다. 다른 집 ID로 조회/수정할 수 없습니다.
- 로그인마다 새 집이 생깁니다. 로그아웃하면 해당 집을 메모리에서 제거합니다. 2시간 동안 접근하지 않은 집은 다음 집 생성 시 정리합니다. 최대 1,000개 집 / 집당 150개 물건으로 메모리를 제한합니다.
- 여러 탭에서 최신 배치를 확인할 수 있지만 WebSocket 공동 편집, 잠금 공유, 초대는 아직 범위 밖입니다. 단일 Pod만 사용합니다.
- 저장 중 새 변경을 막고 완료 버튼은 응답을 기다립니다. 연결 오류로 저장 여부를 모르면 자동 재전송하지 않고 조회로 복구합니다. 연결 복구나 창 포커스 복귀 시 재조회합니다.

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

이 스크립트는 테스트용 로그인 세션을 만들고 실제 포인터 이벤트로 드래그·회전·문·부착·이동·삭제·충돌·구경 화면·새로고침·모바일 폭을 확인합니다. 출력 스크린샷은 Git에 넣지 않습니다.
