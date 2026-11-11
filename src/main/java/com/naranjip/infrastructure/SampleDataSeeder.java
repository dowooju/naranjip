package com.naranjip.infrastructure;

import com.naranjip.application.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;

/**
 * 나란집 샘플 집 4채(H1~H4)와 주인 계정(S1~S4)을 한 번만 만든다.
 * 이미 만들어져 있으면 아무것도 하지 않으므로 앱을 재시작해도 중복되지 않는다.
 * 하트·방명록·콘셉트·쪽지까지 하나의 트랜잭션으로 넣고, 실패하면 전부 되돌린다.
 */
@Component
@ConditionalOnProperty(name="app.sample-data", havingValue="true")
public class SampleDataSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SampleDataSeeder.class);
    /** S1~S4. 홈 화면 순위에 거주자 이름으로 표시된다. */
    public static final List<String> LOGIN_IDS = List.of("햇살방", "구름집", "달빛집", "솔바람집");
    public static final String THEME_TITLE = "비 오는 날의 집";

    private final JdbcTemplate jdbc;
    private final AccountService accounts;
    private final CoupleService couples;
    private final FurnitureService furniture;
    private final NoteRepository notes;
    private final SocialRepository social;
    private final TransactionTemplate transaction;
    private final String password;

    public SampleDataSeeder(JdbcTemplate jdbc, AccountService accounts, CoupleService couples, FurnitureService furniture,
                            NoteRepository notes, SocialRepository social, PlatformTransactionManager transactions,
                            @Value("${app.sample-password:}") String password) {
        this.jdbc = jdbc; this.accounts = accounts; this.couples = couples; this.furniture = furniture;
        this.notes = notes; this.social = social; this.transaction = new TransactionTemplate(transactions);
        this.password = password;
    }

    @Override public void run(ApplicationArguments args) {
        try {
            transaction.executeWithoutResult(status -> seed());
        } catch (RuntimeException e) {
            // 같은 아이디를 다른 사용자가 먼저 쓰는 등 실패해도 앱은 정상 기동하고 샘플만 건너뛴다(전부 롤백됨).
            log.warn("샘플 데이터를 만들지 못해 건너뜁니다.", e);
        }
    }

    private void seed() {
        long existing = jdbc.queryForObject("SELECT COUNT(*) FROM accounts WHERE login_id IN (?,?,?,?)", Long.class, LOGIN_IDS.toArray());
        if (existing == LOGIN_IDS.size()) return;
        if (existing > 0) { log.warn("샘플 아이디 일부가 이미 사용 중이라 샘플 데이터를 건너뜁니다."); return; }

        // 비밀번호를 지정하지 않으면 아무도 모르는 무작위 값이 되어, 샘플 계정은 구경용으로만 쓰인다.
        String secret = password == null || password.isBlank() ? UUID.randomUUID().toString() : password;
        List<UUID> ids = new ArrayList<>();
        for (int n = 0; n < LOGIN_IDS.size(); n++) {
            var account = accounts.register(LOGIN_IDS.get(n), "s" + (n + 1) + "@sample.naranjip.local", secret);
            couples.createHouse(account.id().toString());
            ids.add(account.id());
        }
        UUID s1 = ids.get(0), s2 = ids.get(1), s3 = ids.get(2), s4 = ids.get(3);
        // 집 ID는 처음 만든 사람의 계정 ID와 같다.
        UUID h1 = s1, h2 = s2, h3 = s3, h4 = s4;

        // H1 햇살방: 러그 위 식탁, 냉장고 쪽지 2장(공개 1·비공개 1)
        String house = h1.toString();
        put(house, "door", 3, 0, false, "right", null);
        put(house, "rug", 2, 3, false, null, null);
        put(house, "dining", 2, 3, false, null, null);
        put(house, "chair", 4, 3, false, null, null);
        put(house, "chair", 4, 4, false, null, null);
        String fridge1 = put(house, "fridge", 0, 1, false, null, null);
        put(house, "sofa", 5, 6, false, null, null);
        put(house, "plant-tall", 7, 7, false, null, null);
        note(house, fridge1, "오늘 저녁은 같이 카레 만들기!\n장보기: 감자, 당근, 양파", true);
        note(house, fridge1, "주말에 몰래 꽃 사 오기 (비밀)", false);

        // H2 구름집: 책상과 액자, 세운 소파, 왼쪽 벽 문
        house = h2.toString();
        put(house, "plant-small", 0, 0, false, null, null);
        String desk2 = put(house, "desk", 1, 1, false, null, null);
        put(house, "frame", 0, 0, false, null, desk2);
        put(house, "chair", 1, 2, false, null, null);
        put(house, "sofa", 6, 2, true, null, null);
        put(house, "rug", 3, 4, false, null, null);
        put(house, "door", 0, 4, true, "left", null);

        // H3 달빛집: 비 오는 날 콘셉트 1위 집. 초록 화분과 러그, 공개 쪽지
        house = h3.toString();
        put(house, "plant-tall", 0, 0, false, null, null);
        put(house, "plant-small", 6, 0, false, null, null);
        String fridge3 = put(house, "fridge", 7, 0, false, null, null);
        put(house, "rug", 2, 2, false, null, null);
        put(house, "sofa", 2, 4, false, null, null);
        put(house, "chair", 5, 2, false, null, null);
        put(house, "desk", 4, 6, false, null, null);
        note(house, fridge3, "창문 열고 빗소리 듣기 좋은 날 ☔", true);

        // H4 솔바람집: 가운데 식탁과 의자 둘
        house = h4.toString();
        put(house, "door", 1, 0, false, "right", null);
        put(house, "dining", 3, 3, false, null, null);
        put(house, "chair", 2, 3, false, null, null);
        put(house, "chair", 5, 4, false, null, null);
        put(house, "plant-tall", 7, 7, false, null, null);
        put(house, "plant-small", 0, 7, false, null, null);

        // 일반 하트: H1 3개, H2 2개, H3·H4 1개씩 → 순위 1, 2, 3, 3
        like(h1, s2, null); like(h1, s3, null); like(h1, s4, null);
        like(h2, s1, null); like(h2, s3, null);
        like(h3, s1, null);
        like(h4, s2, null);

        // 방명록: 작성자 프로필을 눌러 서로의 집으로 오갈 수 있다
        social.addComment(h1, s2, "햇살이 가득해서 기분이 좋아져요!");
        social.addComment(h2, s3, "구름처럼 폭신한 소파가 부러워요.");
        social.addComment(h3, s4, "비 오는 날 놀러 오고 싶은 집이에요.");
        social.addComment(h4, s1, "식탁에 둘러앉고 싶어요 :)");

        // 관리자가 아직 콘셉트를 정하지 않았을 때만 첫 콘셉트를 연다. 진행 중인 콘셉트 순위는 건드리지 않는다.
        if (social.active(true) == null) {
            UUID theme = UUID.randomUUID();
            social.activate(theme, THEME_TITLE, "창밖에 비가 내리는 날, 머물고 싶은 집을 꾸며 주세요.", s1);
            like(h3, s1, theme); like(h3, s2, theme); like(h3, s4, theme);
        }
        log.info("나란집 샘플 집 {}채를 만들었어요.", LOGIN_IDS.size());
    }

    /** 배치 규칙(경계·겹침·벽·부착)을 그대로 거쳐 놓고, 새 가구의 ID를 돌려준다. */
    private String put(String house, String type, int x, int y, boolean rotation, String wall, String parent) {
        long revision = furniture.read(house).revision();
        var snapshot = furniture.place(house, null, new FurnitureService.Placement(type, x, y, rotation, wall, parent, revision));
        return snapshot.furniture().getLast().id();
    }

    private void note(String house, String fridge, String body, boolean isPublic) {
        String id = put(house, "note", 0, 0, false, null, fridge);
        notes.save(UUID.fromString(id), body, isPublic, 1);
    }

    private void like(UUID house, UUID voter, UUID theme) { social.like(house, voter, theme, true); }
}
