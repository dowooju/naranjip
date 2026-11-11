package com.naranjip;
import com.naranjip.application.*;
import com.naranjip.infrastructure.SampleDataSeeder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** 다른 테스트와 DB를 공유하지 않도록 별도 H2 DB에서 샘플 데이터를 검증한다. */
@SpringBootTest(properties={
 "app.sample-data=true","app.sample-password=SampleDemo123!",
 "spring.datasource.url=jdbc:h2:mem:naranjip-sample;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
class SampleDataTest {
 @Autowired SampleDataSeeder seeder;@Autowired SocialService social;@Autowired AccountService accounts;
 @Autowired FurnitureService furniture;@Autowired NoteService notes;@Autowired JdbcTemplate jdbc;
 UUID id(String loginId){return jdbc.queryForObject("SELECT id FROM accounts WHERE login_id=?",UUID.class,loginId);}
 @Test void sampleHousesRankingsCommentsAndNotesExistOnceAfterRestart() {
  seeder.run(null);seeder.run(null); // 재시작해도 중복 생성하지 않는다
  assertEquals(4,jdbc.queryForObject("SELECT COUNT(*) FROM accounts",Integer.class));
  assertEquals(4,jdbc.queryForObject("SELECT COUNT(*) FROM houses",Integer.class));
  assertEquals(4,jdbc.queryForObject("SELECT COUNT(*) FROM guestbook",Integer.class));
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM themes",Integer.class));

  var general=social.rankings(false).houses();
  assertEquals(List.of(3L,2L,1L,1L),general.stream().map(r->r.house().likes()).toList());
  assertEquals(List.of(1L,2L,3L,3L),general.stream().map(SocialService.Ranked::rank).toList());
  assertEquals("햇살방",general.getFirst().house().residents());

  var theme=social.rankings(true);
  assertEquals(SampleDataSeeder.THEME_TITLE,theme.theme().title());
  assertEquals("달빛집",theme.houses().getFirst().house().residents());
  assertEquals(3L,theme.houses().getFirst().house().likes());

  String h1=id("햇살방").toString();
  var layout=furniture.read(h1).furniture();
  assertTrue(layout.stream().anyMatch(f->f.typeId().equals("rug")));
  assertEquals(2,layout.stream().filter(f->f.typeId().equals("note")).count());
  var visible=notes.list(h1,null);
  assertEquals(1,visible.size());assertTrue(visible.getFirst().isPublic());
  assertEquals(2,notes.list(h1,h1).size());

  assertEquals("구름집",accounts.login("구름집","SampleDemo123!").loginId());
  assertNotNull(social.random(h1));
 }
}
