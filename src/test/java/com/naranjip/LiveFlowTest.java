package com.naranjip;
import com.fasterxml.jackson.databind.*;
import com.naranjip.application.*;
import com.naranjip.web.LiveEditing;
import com.naranjip.web.LiveStore;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @AutoConfigureMockMvc
class LiveFlowTest {
 @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired LiveEditing live;
 @Autowired CoupleService couples;@Autowired FurnitureService furniture;@Autowired NoteService notes;
 @Autowired HouseRepository houses;@Autowired LiveStore store;
 record User(String id,MockHttpSession session) {}
 record Peer(LiveEditing server,WebSocketSession socket,List<JsonNode> messages) {String id(){return socket.getId();}}
 final List<Peer> peers=new ArrayList<>();
 @AfterEach void disconnect() {for(var p:peers)p.server().afterConnectionClosed(p.socket(),CloseStatus.NORMAL);}
 User user() throws Exception {
  String name="l"+UUID.randomUUID().toString().replace("-","").substring(0,12);
  var r=mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("loginId",name,"email",name+"@example.com","password","LiveTest123!")))).andExpect(status().isCreated()).andReturn();
  return new User(json.readTree(r.getResponse().getContentAsString()).at("/user/accountId").asText(),(MockHttpSession)r.getRequest().getSession(false));
 }
 Peer connect(User user,String house) throws Exception {return connect(live,user,house);}
 /** server: WebSocket을 받는 앱. 접속권은 항상 기본 앱(live)이 HTTP로 발급한 것을 쓴다. */
 Peer connect(LiveEditing server,User user,String house) throws Exception {
  String ticket=live.ticket(house,user.id(),"테스터",user.session());
  var attributes=new HashMap<String,Object>();assertTrue(server.consume(ticket,user.session(),attributes));
  assertFalse(server.consume(ticket,user.session(),new HashMap<>()));
  var socket=mock(WebSocketSession.class);var open=new AtomicBoolean(true);
  when(socket.getId()).thenReturn(UUID.randomUUID().toString());when(socket.getAttributes()).thenReturn(attributes);when(socket.isOpen()).thenAnswer(i->open.get());
  doAnswer(i->{open.set(false);return null;}).when(socket).close(any(CloseStatus.class));
  List<JsonNode> messages=Collections.synchronizedList(new ArrayList<>());
  doAnswer(i->{messages.add(json.readTree(((TextMessage)i.getArgument(0)).getPayload()));return null;}).when(socket).sendMessage(any());
  Peer peer=new Peer(server,socket,messages);peers.add(peer);server.afterConnectionEstablished(socket);return peer;
 }
 void send(Peer p,Map<String,Object> message) throws Exception {p.server().handleMessage(p.socket(),new TextMessage(json.writeValueAsString(message)));}
 void lock(Peer p,String id) throws Exception {send(p,Map.of("type","lock","id",id,"requestId",UUID.randomUUID().toString()));}
 long count(Peer p,String type){return p.messages().stream().filter(m->m.path("type").asText().equals(type)).count();}
 String add(String house,String type,String parent,int x) {return furniture.place(house,null,new FurnitureService.Placement(type,x,2,false,null,parent,furniture.read(house).revision())).furniture().getLast().id();}
 @Test void sameFurnitureAndAttachmentsAreExclusiveEvenThroughRest() throws Exception {
  var a=user();var b=user();couples.createHouse(a.id());couples.accept(b.id(),couples.issue(a.id(),a.id()).code(),false,null);
  String fridge=add(a.id(),"fridge",null,2),note=add(a.id(),"note",fridge,0);
  var one=connect(a,a.id());var two=connect(b,a.id());lock(one,note);lock(two,fridge);
  assertEquals(1,count(one,"locked"));assertEquals(1,count(two,"rejected"));
  mvc.perform(delete("/api/houses/"+a.id()+"/furniture/"+fridge).param("revision","2").param("cascade","true").session(b.session()).with(csrf())).andExpect(status().isConflict());
  mvc.perform(put("/api/houses/"+a.id()+"/notes/"+note).header("X-Live-Client",two.id()).session(b.session()).with(csrf()).contentType("application/json").content("{\"body\":\"bad\",\"revision\":0}")).andExpect(status().isConflict());
  send(one,Map.of("type","release"));lock(two,fridge);assertEquals(1,count(two,"locked"));
 }
 @Test void distinctEditsRebaseButOverlapRollsBackAndBroadcastsOnlyCommittedState() throws Exception {
  var a=user();var b=user();couples.createHouse(a.id());couples.accept(b.id(),couples.issue(a.id(),a.id()).code(),false,null);
  String left=add(a.id(),"chair",null,1),right=add(a.id(),"chair",null,5);
  var one=connect(a,a.id());var two=connect(b,a.id());lock(one,left);lock(two,right);
  String url="/api/houses/"+a.id()+"/furniture/";
  mvc.perform(put(url+left).session(a.session()).with(csrf()).header("X-Live-Client",one.id()).contentType("application/json").content("{\"typeId\":\"chair\",\"x\":2,\"y\":3,\"rotation\":false,\"revision\":0}")).andExpect(status().isOk());
  mvc.perform(put(url+right).session(b.session()).with(csrf()).header("X-Live-Client",two.id()).contentType("application/json").content("{\"typeId\":\"chair\",\"x\":5,\"y\":4,\"rotation\":true,\"revision\":0}")).andExpect(status().isOk());
  assertEquals(2,count(one,"snapshot"));assertEquals(2,count(two,"snapshot"));
  long revision=furniture.read(a.id()).revision();
  mvc.perform(put(url+right).session(b.session()).with(csrf()).header("X-Live-Client",two.id()).contentType("application/json").content("{\"typeId\":\"chair\",\"x\":2,\"y\":3,\"rotation\":false,\"revision\":0}")).andExpect(status().isConflict());
  assertEquals(revision,furniture.read(a.id()).revision());assertEquals(2,count(one,"snapshot"));
 }
 @Test void concurrentLocksHaveOneWinnerAndDisconnectOrLogoutReleasesThem() throws Exception {
  var a=user();couples.createHouse(a.id());String chair=add(a.id(),"chair",null,2);
  var one=connect(a,a.id());var two=connect(a,a.id());
  try(var pool=Executors.newFixedThreadPool(2)) {
   var x=pool.submit(()->{lock(one,chair);return true;});var y=pool.submit(()->{lock(two,chair);return true;});x.get(5,TimeUnit.SECONDS);y.get(5,TimeUnit.SECONDS);
  }
  assertEquals(1,count(one,"locked")+count(two,"locked"));
  Peer winner=count(one,"locked")==1?one:two,loser=winner==one?two:one;
  live.afterConnectionClosed(winner.socket(),CloseStatus.NORMAL);lock(loser,chair);assertEquals(1,count(loser,"locked"));
  a.session().invalidate();live.sweep();assertFalse(loser.socket().isOpen());
 }
 @Test void ticketsNeedMembershipAndCsrfAndPreviewNeverWritesDatabase() throws Exception {
  var a=user();var stranger=user();couples.createHouse(a.id());String chair=add(a.id(),"chair",null,2);
  mvc.perform(post("/api/live/ticket").param("houseId",a.id()).session(a.session())).andExpect(status().isForbidden());
  mvc.perform(post("/api/live/ticket").param("houseId",a.id()).with(csrf())).andExpect(status().isUnauthorized());
  assertThrows(PlacementException.class,()->live.ticket(a.id(),stranger.id(),"bad",stranger.session()));
  var one=connect(a,a.id());var two=connect(a,a.id());long before=furniture.read(a.id()).revision();lock(one,chair);
  send(one,Map.of("type","preview","id",chair,"x",4,"y",4,"rotation",true));
  assertEquals(1,count(two,"preview"));assertEquals(before,furniture.read(a.id()).revision());
  assertEquals(2,furniture.read(a.id()).furniture().getFirst().x());
 }

 /** nginx 뒤 앱 두 개를 흉내 낸다: 두 사람이 서로 다른 앱에 연결돼도 같은 공유 저장소로 잠금·미리보기·저장을 나눈다. */
 @Test void twoServersShareLocksPreviewsAndSnapshots() throws Exception {
  var other=new LiveEditing(json,furniture,houses,store,Optional.empty());
  var a=user();var b=user();couples.createHouse(a.id());couples.accept(b.id(),couples.issue(a.id(),a.id()).code(),false,null);
  String chair=add(a.id(),"chair",null,2);
  var one=connect(live,a,a.id());var two=connect(other,b,a.id());
  assertTrue(two.messages().stream().anyMatch(m->m.path("type").asText().equals("presence")&&m.path("people").size()==2));
  lock(one,chair);lock(two,chair);
  assertEquals(1,count(one,"locked"));assertEquals(1,count(two,"rejected"));
  send(one,Map.of("type","preview","id",chair,"x",4,"y",4,"rotation",false));assertEquals(1,count(two,"preview"));
  String url="/api/houses/"+a.id()+"/furniture/"+chair;
  mvc.perform(put(url).session(b.session()).with(csrf()).header("X-Live-Client",two.id()).contentType("application/json").content("{\"typeId\":\"chair\",\"x\":5,\"y\":5,\"rotation\":false,\"revision\":0}")).andExpect(status().isConflict());
  mvc.perform(put(url).session(a.session()).with(csrf()).header("X-Live-Client",one.id()).contentType("application/json").content("{\"typeId\":\"chair\",\"x\":4,\"y\":4,\"rotation\":false,\"revision\":0}")).andExpect(status().isOk());
  assertEquals(1,count(one,"snapshot"));assertEquals(1,count(two,"snapshot"));
  live.afterConnectionClosed(one.socket(),CloseStatus.NORMAL);
  lock(two,chair);assertEquals(1,count(two,"locked"));
 }
}
