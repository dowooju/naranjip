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
class NoteFlowTest {
 @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc;
 @Autowired NoteService notes; @Autowired CoupleService couples; @Autowired FurnitureService furniture;
 record User(String id,MockHttpSession session) {}
 User user() throws Exception {
  String login="n"+UUID.randomUUID().toString().replace("-","").substring(0,12);
  var r=mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("loginId",login,"email",login+"@example.com","password","NotesTest123!")))).andExpect(status().isCreated()).andReturn();
  return new User(json.readTree(r.getResponse().getContentAsString()).at("/user/accountId").asText(),(MockHttpSession)r.getRequest().getSession(false));
 }
 String add(String house,String type,String parent,int x,int y,boolean rotation) {
  var s=furniture.place(house,null,new FurnitureService.Placement(type,x,y,rotation,null,parent,furniture.read(house).revision()));
  return s.furniture().getLast().id();
 }
 String[] setup(User u) {couples.createHouse(u.id());String fridge=add(u.id(),"fridge",null,2,2,false);return new String[]{fridge,add(u.id(),"note",fridge,0,0,false)};}
 @Test void privacyMembershipPublishingAndRevocation() throws Exception {
  var owner=user();var partner=user();var visitor=user();var ids=setup(owner);String h=owner.id(),id=ids[1],url="/api/houses/"+h+"/notes";
  assertFalse(notes.read(h,id,h).isPublic());assertEquals("",notes.read(h,id,h).body());
  mvc.perform(get(url)).andExpect(jsonPath("$.length()").value(0));
  mvc.perform(get(url+"/"+id)).andExpect(status().isNotFound());
  notes.save(h,id,h,"우리만의 비밀\n두 번째 줄",false,0);
  mvc.perform(get(url+"/"+id).session(visitor.session())).andExpect(status().isNotFound());
  mvc.perform(put(url+"/"+id).session(visitor.session()).with(csrf()).contentType("application/json").content("{\"body\":\"bad\",\"revision\":1}")).andExpect(status().isForbidden());
  mvc.perform(put(url+"/"+id).session(owner.session()).contentType("application/json").content("{}")).andExpect(status().isForbidden());
  couples.accept(partner.id(),couples.issue(h,h).code(),false,null);
  mvc.perform(get(url+"/"+id).session(partner.session())).andExpect(jsonPath("$.body").value("우리만의 비밀\n두 번째 줄"));
  notes.save(h,id,partner.id(),"공개 첫 줄\n두 번째 줄",true,1);
  mvc.perform(get(url)).andExpect(jsonPath("$[0].preview").value("공개 첫 줄")).andExpect(header().string("Cache-Control","no-store"));
  mvc.perform(get(url+"/"+id)).andExpect(jsonPath("$.body").value("공개 첫 줄\n두 번째 줄"));
  notes.save(h,id,h,"다시 비밀",false,2);
  mvc.perform(get(url)).andExpect(jsonPath("$.length()").value(0));
  mvc.perform(get(url+"/"+id)).andExpect(status().isNotFound());
  assertFalse(mvc.perform(get("/api/visits/"+h)).andReturn().getResponse().getContentAsString().contains("다시 비밀"));
  mvc.perform(get(url+"/"+ids[0]).session(owner.session())).andExpect(status().isNotFound());
 }
 @Test void furnitureChangesPreserveContentsAndExplicitDeletionCascades() throws Exception {
  var owner=user();var ids=setup(owner);String h=owner.id(),fridge=ids[0],id=ids[1];
  notes.save(h,id,h,"계속 남는 내용",false,0);
  furniture.place(h,fridge,new FurnitureService.Placement("fridge",4,4,true,null,null,furniture.read(h).revision()));
  add(h,"chair",null,0,0,false);
  assertEquals("계속 남는 내용",notes.read(h,id,h).body());
  String second=add(h,"fridge",null,1,1,false);
  furniture.place(h,id,new FurnitureService.Placement("note",0,0,false,null,second,furniture.read(h).revision()));
  assertEquals("계속 남는 내용",notes.read(h,id,h).body());
  assertThrows(PlacementException.class,()->furniture.delete(h,second,furniture.read(h).revision(),false));
  assertEquals(1,notes.read(h,id,h).revision());
  furniture.delete(h,second,furniture.read(h).revision(),true);
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM note_contents WHERE furniture_id=?",Integer.class,UUID.fromString(id)));
  String another=add(h,"note",fridge,0,0,false);notes.save(h,another,h,"삭제",true,0);
  furniture.delete(h,another,furniture.read(h).revision(),false);
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM note_contents WHERE furniture_id=?",Integer.class,UUID.fromString(another)));
 }
 @Test void validationAndConcurrentSavesDoNotOverwrite() throws Exception {
  var owner=user();var ids=setup(owner);String h=owner.id(),id=ids[1],url="/api/houses/"+h+"/notes/"+id;
  mvc.perform(put(url).session(owner.session()).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("body","a".repeat(201),"isPublic",true,"revision",0)))).andExpect(status().isBadRequest());
  long oldLayout=furniture.read(h).revision();
  try(var pool=Executors.newFixedThreadPool(2)) {
   Callable<Boolean> save=()->{try{notes.save(h,id,h,"a".repeat(200),true,0);return true;}catch(PlacementException e){return false;}};
   var a=pool.submit(save);var b=pool.submit(save);assertNotEquals(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));
  }
  assertEquals(200,notes.read(h,id,h).body().length());
  assertEquals(oldLayout+1,furniture.read(h).revision());
  mvc.perform(put(url).session(owner.session()).with(csrf()).contentType("application/json").content("{\"body\":\"stale\",\"revision\":0}")).andExpect(status().isConflict());
  assertThrows(PlacementException.class,()->furniture.place(h,null,new FurnitureService.Placement("chair",0,0,false,null,null,oldLayout)));
  notes.save(h,id,h,"",false,1);assertEquals("",notes.read(h,id,h).body());
 }
}
