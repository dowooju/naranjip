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
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @AutoConfigureMockMvc
class CoupleFlowTest {
 @Autowired MockMvc mvc;
 @Autowired ObjectMapper json;
 @Autowired CoupleService couples;
 @Autowired FurnitureService furniture;
 @Autowired JdbcTemplate jdbc;
 record Member(String name,String id,MockHttpSession session) {}
 Member member(boolean create) throws Exception {
  String name="c"+UUID.randomUUID().toString().replace("-","").substring(0,12);
  var result=mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json")
   .content(json.writeValueAsString(Map.of("loginId",name,"email",name+"@example.com","password","CoupleTest123!"))))
   .andExpect(status().isCreated()).andExpect(jsonPath("$.user.houseId").doesNotExist()).andReturn();
  String id=json.readTree(result.getResponse().getContentAsString()).at("/user/accountId").asText();
  if(create)couples.createHouse(id);
  return new Member(name,id,(MockHttpSession)result.getRequest().getSession(false));
 }
 @Test void invitationConsentMovesMembershipDeletesOldHouseAndBothCanDecorate() throws Exception {
  var a=member(true);var b=member(true);var outsider=member(false);
  furniture.place(a.id(),null,new FurnitureService.Placement("desk",2,2,false,null,null,0));
  furniture.place(b.id(),null,new FurnitureService.Placement("chair",5,5,false,null,null,0));
  String code=couples.issue(a.id(),a.id()).code();
  assertTrue(code.matches("[A-Z0-9]{7}"));
  var preview=couples.preview(b.id(),code);assertTrue(preview.deletesExistingHouse());
  assertThrows(PlacementException.class,()->couples.accept(b.id(),code,false,b.id()));
  assertEquals(1,furniture.read(b.id()).furniture().size());
  mvc.perform(get("/api/houses/"+a.id()).session(b.session())).andExpect(status().isNotFound());
  var accepted=mvc.perform(post("/api/invites/accept").session(b.session()).with(csrf()).contentType("application/json")
   .content(json.writeValueAsString(Map.of("code",code,"agreeDelete",true,"existingHouseId",b.id()))))
   .andExpect(status().isOk()).andExpect(jsonPath("$.partner.displayName").value(a.name())).andExpect(jsonPath("$.houseId").value(a.id())).andReturn();
  String profile=accepted.getResponse().getContentAsString();assertFalse(profile.contains("email"));assertFalse(profile.contains("password"));
  assertThrows(PlacementException.class,()->furniture.read(b.id()));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM placed_furniture WHERE house_id=?",Integer.class,UUID.fromString(b.id())));
  mvc.perform(get("/api/me").session(b.session())).andExpect(jsonPath("$.user.houseId").value(a.id())).andExpect(jsonPath("$.user.canEdit").value(true));
  mvc.perform(get("/api/couple").session(a.session())).andExpect(jsonPath("$.partner.displayName").value(b.name())).andExpect(jsonPath("$.invite").doesNotExist());
  String payload="{\"typeId\":\"chair\",\"x\":6,\"y\":6,\"rotation\":false,\"revision\":1}";
  mvc.perform(post("/api/houses/"+a.id()+"/furniture").session(b.session()).with(csrf()).contentType("application/json").content(payload)).andExpect(status().isOk());
  mvc.perform(get("/api/houses/"+a.id()).session(a.session())).andExpect(jsonPath("$.furniture.length()").value(2));
  mvc.perform(get("/api/houses/"+a.id()).session(outsider.session())).andExpect(status().isNotFound());
  mvc.perform(post("/api/houses/"+a.id()+"/furniture").session(outsider.session()).with(csrf()).contentType("application/json").content(payload)).andExpect(status().isNotFound());
  assertThrows(PlacementException.class,()->couples.issue(b.id(),a.id()));
  assertThrows(PlacementException.class,()->couples.accept(outsider.id(),code,false,null));
  mvc.perform(post("/api/auth/logout").session(b.session()).with(csrf())).andExpect(status().isNoContent());
  mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("loginId",b.name(),"password","CoupleTest123!"))))
   .andExpect(status().isOk()).andExpect(jsonPath("$.user.houseId").value(a.id()));
 }
 @Test void invalidExpiredRevokedAndChangedPreviewsPreserveExistingHouse() throws Exception {
  var a=member(true);var b=member(true);var noHouse=member(false);
  mvc.perform(get("/api/couple")).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/invites/accept").session(b.session()).contentType("application/json").content("{}")).andExpect(status().isForbidden());
  assertThrows(PlacementException.class,()->couples.issue(noHouse.id(),a.id()));
  assertThrows(PlacementException.class,()->couples.preview(b.id(),"INVALID"));
  String first=couples.issue(a.id(),a.id()).code();
  assertThrows(PlacementException.class,()->couples.preview(a.id(),first));
  couples.preview(b.id(),first);String second=couples.issue(a.id(),a.id()).code();
  assertThrows(PlacementException.class,()->couples.accept(b.id(),first,true,b.id()));
  jdbc.update("UPDATE invites SET expires_at=TIMESTAMP '2000-01-01 00:00:00' WHERE code=?",second);
  assertThrows(PlacementException.class,()->couples.accept(b.id(),second,true,b.id()));
  String third=couples.issue(a.id(),a.id()).code();couples.revoke(a.id(),a.id());
  assertThrows(PlacementException.class,()->couples.accept(b.id(),third,true,b.id()));
  String fourth=couples.issue(a.id(),a.id()).code();
  couples.preview(noHouse.id(),fourth);couples.createHouse(noHouse.id());
  assertThrows(PlacementException.class,()->couples.accept(noHouse.id(),fourth,false,null));
  assertEquals(b.id(),couples.status(b.id()).houseId());
  assertEquals(noHouse.id(),couples.status(noHouse.id()).houseId());
 }
 @Test void simultaneousAcceptsOnlyOnePartnerAndLosingHouseSurvives() throws Exception {
  var a=member(true);var b=member(true);var c=member(true);String code=couples.issue(a.id(),a.id()).code();
  var gate=new CountDownLatch(1);var successes=new AtomicInteger();
  try(var executor=Executors.newFixedThreadPool(2)) {
   var futures=new ArrayList<Future<?>>();
   for(var person:List.of(b,c))futures.add(executor.submit(()->{
    try {gate.await();couples.accept(person.id(),code,true,person.id());successes.incrementAndGet();}
    catch(PlacementException expected){}catch(InterruptedException e){throw new RuntimeException(e);}
   }));
   gate.countDown();for(var f:futures)f.get(10,TimeUnit.SECONDS);
  }
  assertEquals(1,successes.get());assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM house_members WHERE house_id=?",Integer.class,UUID.fromString(a.id())));
  String loser=couples.status(b.id()).houseId().equals(a.id())?c.id():b.id();
  assertEquals(loser,furniture.read(loser).id());
 }
 @Test void memberWithoutHouseCanAcceptAndDatabaseFailureRollsBackOldHouseDeletion() throws Exception {
  var a=member(true);var b=member(false);
  String code=couples.issue(a.id(),a.id()).code();assertFalse(couples.preview(b.id(),code).deletesExistingHouse());
  couples.accept(b.id(),code,false,null);assertEquals(a.id(),couples.status(b.id()).houseId());
  var c=member(true);var d=member(true);
  furniture.place(d.id(),null,new FurnitureService.Placement("chair",1,1,false,null,null,0));
  String another=couples.issue(c.id(),c.id()).code();
  // Force a storage failure after deleting d's old house; the whole transaction must roll back.
  jdbc.execute("ALTER TABLE invites ADD CONSTRAINT test_never_consumed CHECK(consumed_at IS NULL OR code <> '"+another+"')");
  try {assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->couples.accept(d.id(),another,true,d.id()));}
  finally {jdbc.execute("ALTER TABLE invites DROP CONSTRAINT test_never_consumed");}
  assertEquals(d.id(),couples.status(d.id()).houseId());assertEquals(1,furniture.read(d.id()).furniture().size());
  assertNotNull(couples.preview(d.id(),another));
 }
}
