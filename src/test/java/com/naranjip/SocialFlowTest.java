package com.naranjip;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.naranjip.application.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @AutoConfigureMockMvc
class SocialFlowTest {
 @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;
 @Autowired SocialService social;@Autowired CoupleService couples;@Autowired FurnitureService furniture;
 record User(String id,MockHttpSession session) {}
 User user(boolean house,boolean admin) throws Exception {
  String name="s"+UUID.randomUUID().toString().replace("-","").substring(0,12);
  var r=mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("loginId",name,"email",name+"@example.com","password","SocialTest123!")))).andExpect(status().isCreated()).andReturn();
  String id=json.readTree(r.getResponse().getContentAsString()).at("/user/accountId").asText();
  if(house) {couples.createHouse(id);furniture.place(id,null,new FurnitureService.Placement("chair",2,2,false,null,null,0));}
  if(admin)jdbc.update("UPDATE accounts SET is_admin=TRUE WHERE id=?",UUID.fromString(id));
  return new User(id,(MockHttpSession)r.getRequest().getSession(false));
 }
 SocialRepository.Theme activate(User admin,String title) {var current=social.theme();return social.activate(admin.id(),title,"이 주제에 맞게 꾸며 주세요.",current==null?null:current.id());}
 @Test void themesRequireAdminAndKeepGeneralLikesAndCommentsAcrossWeeks() throws Exception {
  var admin=user(false,true);var owner=user(true,false);var visitor=user(false,false);
  mvc.perform(get("/api/admin/themes")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/admin/themes").session(visitor.session())).andExpect(status().isForbidden());
  mvc.perform(post("/api/admin/themes").session(visitor.session()).with(csrf()).contentType("application/json").content("{\"title\":\"bad\",\"description\":\"\"}")).andExpect(status().isForbidden());
  mvc.perform(post("/api/admin/themes").session(admin.session()).contentType("application/json").content("{}")).andExpect(status().isForbidden());
  var first=activate(admin,"비 오는 날");
  social.like(owner.id(),visitor.id(),false,null,true);social.like(owner.id(),visitor.id(),true,first.id(),true);
  social.comment(owner.id(),visitor.id(),"포근해요");
  var second=activate(admin,"따뜻한 겨울");
  var stats=social.stats(owner.id(),visitor.id());assertEquals(1,stats.generalLikes());assertEquals(0,stats.themeLikes());assertEquals(1,stats.comments());assertFalse(stats.themeLiked());
  assertThrows(PlacementException.class,()->social.like(owner.id(),visitor.id(),true,first.id(),true));
  assertThrows(PlacementException.class,()->social.like(owner.id(),visitor.id(),true,first.id(),false));
  assertThrows(PlacementException.class,()->social.activate(admin.id(),"오래된 화면","",first.id()));
  assertThrows(PlacementException.class,()->social.activate(admin.id(),"   ","",second.id()));
  assertEquals(second.id(),social.theme().id());
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM theme_likes WHERE theme_id=?",Integer.class,first.id()));
  social.like(owner.id(),visitor.id(),true,second.id(),true);
  assertEquals(1,social.stats(owner.id(),visitor.id()).themeLikes());
  mvc.perform(get("/api/me").session(admin.session())).andExpect(jsonPath("$.user.admin").value(true));
  jdbc.update("UPDATE accounts SET is_admin=FALSE WHERE id=?",UUID.fromString(admin.id()));
  mvc.perform(get("/api/admin/themes").session(admin.session())).andExpect(status().isForbidden());
 }
 @Test void likesAreIndependentIdempotentAndOwnersIncludingPartnerCannotVote() throws Exception {
  var owner=user(true,false);var partner=user(false,false);var voter=user(false,false);var admin=user(false,true);
  couples.accept(partner.id(),couples.issue(owner.id(),owner.id()).code(),false,null);
  var theme=activate(admin,"나란히");
  assertThrows(PlacementException.class,()->social.like(owner.id(),owner.id(),false,null,true));
  assertThrows(PlacementException.class,()->social.like(owner.id(),partner.id(),true,theme.id(),true));
  String route="/api/houses/"+owner.id()+"/likes/general";
  mvc.perform(put(route).with(csrf()).contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
  try(var pool=Executors.newFixedThreadPool(2)) {
   var a=pool.submit(()->social.like(owner.id(),voter.id(),false,null,true));
   var b=pool.submit(()->social.like(owner.id(),voter.id(),false,null,true));a.get(10,TimeUnit.SECONDS);b.get(10,TimeUnit.SECONDS);
  }
  assertEquals(1,social.stats(owner.id(),voter.id()).generalLikes());
  social.like(owner.id(),voter.id(),true,theme.id(),true);social.like(owner.id(),voter.id(),false,null,false);
  assertEquals(0,social.stats(owner.id(),voter.id()).generalLikes());assertEquals(1,social.stats(owner.id(),voter.id()).themeLikes());
 }
 @Test void publicVisitsCommentsPaginationAndDeletionPermissions() throws Exception {
  var owner=user(true,false);var author=user(false,false);var stranger=user(false,false);
  mvc.perform(get("/api/visits/"+owner.id())).andExpect(status().isOk()).andExpect(jsonPath("$.furniture[0].typeId").value("chair"));
  mvc.perform(get("/api/houses/"+owner.id())).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/houses/"+owner.id()+"/furniture").session(stranger.session()).with(csrf()).contentType("application/json").content("{\"typeId\":\"chair\",\"x\":3,\"y\":3}")).andExpect(status().isNotFound());
  String route="/api/houses/"+owner.id()+"/guestbook";
  mvc.perform(post(route).with(csrf()).contentType("application/json").content("{\"body\":\"hi\"}")).andExpect(status().isUnauthorized());
  mvc.perform(post(route).session(author.session()).with(csrf()).contentType("application/json").content("{\"body\":\" \"}")).andExpect(status().isBadRequest());
  assertThrows(PlacementException.class,()->social.comment(owner.id(),author.id(),"a".repeat(201)));
  social.comment(owner.id(),author.id(),"<script>alert(1)</script>");
  var comment=social.comments(owner.id(),author.id(),0).items().getFirst().comment();
  mvc.perform(delete("/api/guestbook/"+comment.id()).session(stranger.session()).with(csrf())).andExpect(status().isForbidden());
  mvc.perform(get(route)).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].comment.body").value("<script>alert(1)</script>")).andExpect(jsonPath("$.items[0].canDelete").value(false));
  social.deleteComment(owner.id(),comment.id());
  for(int n=0;n<22;n++)social.comment(owner.id(),author.id(),"comment "+n);
  assertEquals(20,social.comments(owner.id(),null,0).items().size());assertTrue(social.comments(owner.id(),null,0).hasMore());
  assertEquals(2,social.comments(owner.id(),null,1).items().size());
  social.deleteComment(author.id(),social.comments(owner.id(),author.id(),0).items().getFirst().comment().id());
 }
 @Test void rankingsTieAndHouseDeletionCascadePreservesOtherHousesReactions() throws Exception {
  var a=user(true,false);var b=user(true,false);var c=user(false,false);var d=user(false,false);
  jdbc.update("DELETE FROM general_likes");
  social.like(a.id(),c.id(),false,null,true);social.like(b.id(),c.id(),false,null,true);
  var ranked=social.rankings(false).houses();assertEquals(1,ranked.get(0).rank());assertEquals(1,ranked.get(1).rank());
  assertTrue(ranked.stream().noneMatch(r->r.house().houseId().equals(c.id())));
  social.comment(a.id(),c.id(),"삭제 대상");social.comment(b.id(),c.id(),"유지 대상");
  couples.accept(a.id(),couples.issue(b.id(),b.id()).code(),true,a.id());
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM guestbook WHERE house_id=?",Integer.class,UUID.fromString(a.id())));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM general_likes WHERE house_id=?",Integer.class,UUID.fromString(a.id())));
  assertEquals(1,social.stats(b.id(),c.id()).generalLikes());assertEquals(1,social.stats(b.id(),c.id()).comments());
  assertNotEquals(b.id(),social.random(b.id()));
 }
}
