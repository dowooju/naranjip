package com.naranjip.web;

import com.fasterxml.jackson.databind.*;
import com.naranjip.application.*;
import com.naranjip.web.LiveStore.Lease;
import com.naranjip.web.LiveStore.Member;
import jakarta.servlet.http.HttpSession;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 실시간 공동 편집 조정자.
 * WebSocket 연결은 이 앱이 들고 있고, 잠금·접속자·접속권·브로드캐스트는 {@link LiveStore}(운영은 Redis)로 공유한다.
 * 그래서 커플 두 사람이 nginx 뒤의 서로 다른 앱 컨테이너에 연결돼도 같은 집을 함께 편집할 수 있다.
 * DB 트랜잭션이 커밋된 뒤에만 snapshot을 보낸다. 같은 집의 동시 저장은 DB 행 잠금과 revision 검사가 막는다.
 */
@Component
public class LiveEditing extends TextWebSocketHandler implements LiveStore.Delivery {
 static final long LEASE_MS=15000,TICKET_MS=30000,MEMBER_MS=30000,SILENT_MS=20000;
 private final ObjectMapper json; private final FurnitureService furniture; private final HouseRepository houses;
 private final LiveStore store; private final SessionRepository<? extends Session> sessions;
 private final String server=hostname();
 /** 이 앱에 연결된 WebSocket만 들어 있다. */
 private final Map<String,Client> clients=new ConcurrentHashMap<>();
 private final Map<String,Lease> held=new ConcurrentHashMap<>();
 private final Map<String,Long> seen=new ConcurrentHashMap<>(), previews=new ConcurrentHashMap<>();
 /** session은 같은 앱 안에서 발급한 경우(메모리 저장소)에만 있다. 공유 세션(Redis)은 sessionId로 다시 찾는다. */
 public record Ticket(String house,String actor,String name,String sessionId,long expires,HttpSession session) {}
 private static final class Client {
  final WebSocketSession socket; final Ticket ticket; volatile boolean valid=true;
  Client(WebSocketSession socket,Ticket ticket){this.socket=socket;this.ticket=ticket;}
 }
 public LiveEditing(ObjectMapper json,FurnitureService furniture,HouseRepository houses,LiveStore store,Optional<SessionRepository<? extends Session>> sessions) {
  this.json=json;this.furniture=furniture;this.houses=houses;this.store=store;this.sessions=sessions.orElse(null);
  store.listen(this);
 }
 private static String hostname(){try{return InetAddress.getLocalHost().getHostName();}catch(Exception e){return "local";}}
 private static long now(){return System.currentTimeMillis();}

 public String ticket(String house,String actor,String name,HttpSession session) {
  furniture.readForMember(house,actor);
  String id=UUID.randomUUID().toString();
  store.putTicket(id,new Ticket(house,actor,name,session.getId(),now()+TICKET_MS,session));return id;
 }
 public boolean consume(String ticket,HttpSession session,Map<String,Object> attributes) {
  Ticket t=store.takeTicket(ticket);
  if(t==null||t.expires()<now()||!t.sessionId().equals(session.getId()))return false;
  t=new Ticket(t.house(),t.actor(),t.name(),t.sessionId(),t.expires(),session);
  if(!valid(t))return false;
  attributes.put("liveTicket",t);return true;
 }
 /** 로그아웃·세션 만료·집 탈퇴를 확인한다. 공유 세션이면 다른 앱에서 로그아웃한 것도 보인다. */
 private boolean valid(Ticket t) {
  try {
   AuthController.Resident resident;
   if(sessions!=null){Session s=sessions.findById(t.sessionId());resident=s==null?null:s.getAttribute("resident");}
   else resident=(AuthController.Resident)t.session().getAttribute("resident");
   return resident!=null&&resident.houseId().equals(t.actor())&&houses.isMember(t.house(),t.actor());
  }catch(IllegalStateException e){return false;}
 }
 private Member member(String client,Ticket t){return new Member(client,t.house(),t.actor(),t.name(),now()+MEMBER_MS);}
 @Override public synchronized void afterConnectionEstablished(WebSocketSession socket) {
  Ticket t=(Ticket)socket.getAttributes().get("liveTicket");
  if(t==null||!valid(t)){close(socket);return;}
  socket.setTextMessageSizeLimit(4096);
  clients.put(socket.getId(),new Client(new org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator(socket,5000,65536),t));
  seen.put(socket.getId(),now());
  store.join(member(socket.getId(),t));
  send(socket,Map.of("type","hello","clientId",socket.getId(),"server",server,"house",furniture.readForMember(t.house(),t.actor())));
  presence(t.house());
 }
 private String root(String house,String actor,String id) {
  var item=furniture.readForMember(house,actor).furniture().stream().filter(f->f.id().equals(id)).findFirst().orElseThrow(()->new PlacementException("삭제된 가구예요."));
  return item.parentId()==null?item.id():item.parentId();
 }
 @Override protected synchronized void handleTextMessage(WebSocketSession socket,TextMessage message) {
  Client c=clients.get(socket.getId());if(c==null)return;
  seen.put(socket.getId(),now());
  if(!valid(c.ticket)){disconnect(socket);return;}
  String requestId="";
  try {
   JsonNode m=json.readTree(message.getPayload());String type=m.path("type").asText();requestId=m.path("requestId").asText();
   String house=c.ticket.house(),actor=c.ticket.actor(),client=socket.getId();
   if(type.equals("ping")) {
    store.join(member(client,c.ticket));
    Lease own=held.get(client);
    if(own!=null) {
     Lease renewed=new Lease(client,own.house(),own.root(),own.item(),now()+LEASE_MS);
     if(store.acquire(renewed))held.put(client,renewed);else{held.remove(client);presence(house);}
    }
    send(socket,Map.of("type","pong"));return;
   }
   if(type.equals("release")) {release(client);presence(house);return;}
   String id=m.path("id").asText(),root=root(house,actor,id);
   if(type.equals("lock")) {
    Lease lease=store.lease(house,root);
    if(lease!=null&&!lease.client().equals(client))throw new PlacementException("상대방이 이 가구를 편집 중이에요.");
    release(client);
    Lease mine=new Lease(client,house,root,id,now()+LEASE_MS);
    // 다른 앱의 사용자가 그 사이에 먼저 잡았으면 여기서 실패한다.
    if(!store.acquire(mine))throw new PlacementException("상대방이 이 가구를 편집 중이에요.");
    held.put(client,mine);
    send(socket,Map.of("type","locked","requestId",requestId,"id",id));presence(house);
   } else if(type.equals("preview")) {
    long now=now();if(now-previews.getOrDefault(client,0L)<60)return;previews.put(client,now);
    Lease lease=held.get(client);
    if(lease==null||!lease.root().equals(root)||lease.expires()<=now)throw new PlacementException("편집 권한이 만료됐어요. 다시 선택해 주세요.");
    var item=furniture.readForMember(house,actor).furniture().stream().filter(f->f.id().equals(id)).findFirst().orElseThrow();
    int x=m.path("x").asInt(-1),y=m.path("y").asInt(-1);
    if(x<0||x>7||y<0||y>7)return;
    String wall=item.wall(),parent=item.parentId();int slot=item.slot();boolean rotation=m.path("rotation").asBoolean();
    if(wall!=null) {
     String proposed=m.path("wall").asText();
     if(!proposed.equals("left")&&!proposed.equals("right"))return;
     wall=proposed;rotation=wall.equals("left");if(rotation)x=0;else y=0;
    }
    if(parent!=null) {
     String proposed=m.path("parentId").asText(parent);
     var target=furniture.readForMember(house,actor).furniture().stream().filter(f->f.id().equals(proposed)).findFirst().orElse(null);
     if(target==null||!target.typeId().equals(item.typeId().equals("note")?"fridge":"desk"))return;
     available(house,actor,client,proposed);parent=proposed;slot=m.path("slot").asInt(slot);
     if(slot<0||slot>=(item.typeId().equals("note")?3:2))return;
    }
    var preview=new HouseState.PlacedFurniture(item.id(),item.typeId(),x,y,rotation,wall,parent,slot);
    broadcast(house,Map.of("type","preview","clientId",client,"item",preview),client);
   }
  } catch(Exception e) {send(socket,Map.of("type","rejected","requestId",requestId,"message",e instanceof PlacementException?e.getMessage():"요청을 처리하지 못했어요."));}
 }
 private void release(String client) {
  Lease own=held.remove(client);
  if(own!=null){store.release(own);broadcast(own.house(),Map.of("type","previewEnd","clientId",client),null);}
 }
 /** REST 저장. 요청이 들어온 앱과 X-Live-Client의 WebSocket이 연결된 앱이 달라도 공유 저장소로 확인한다. */
 public synchronized <T> T mutate(String house,String actor,String client,String item,Supplier<T> write) {
  if(!houses.isMember(house,actor))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
  if(client!=null) {
   Member m=store.member(house,client);
   if(m==null||!m.actor().equals(actor))throw new PlacementException("실시간 연결이 끊겼어요. 다시 연결한 뒤 시도해 주세요.");
  }
  if(item!=null) {
   Lease l=store.lease(house,root(house,actor,item));
   if(l!=null&&!l.client().equals(client))throw new PlacementException("상대방이 이 가구를 편집 중이에요.");
   if(client!=null&&l==null)throw new PlacementException("편집 권한이 만료됐어요. 다시 선택해 주세요.");
  }
  T result=write.get();
  // Never push note bodies: each client re-fetches through the note visibility API.
  broadcast(house,Map.of("type","snapshot","house",furniture.readForMember(house,actor)),null);
  return result;
 }
 public void available(String house,String actor,String client,String target) {
  if(target==null)return;
  Lease l=store.lease(house,root(house,actor,target));
  if(l!=null&&!l.client().equals(client))throw new PlacementException("상대방이 붙일 가구를 편집 중이에요.");
 }
 public long revision(String house,String actor,String client,long original) {
  return client==null?original:furniture.readForMember(house,actor).revision();
 }
 private void presence(String house) {
  var people=store.members(house).stream().map(m->Map.of("clientId",m.client(),"name",m.name())).toList();
  var locks=store.leases(house).stream().map(l->Map.of("clientId",l.client(),"rootId",l.root(),"itemId",l.item())).toList();
  broadcast(house,Map.of("type","presence","people",people,"locks",locks),null);
 }
 /** 모든 앱에 보낸다. 각 앱은 {@link #deliver}에서 자기에게 연결된 사람에게만 전달한다. */
 private void broadcast(String house,Object message,String excluded) {
  try{store.publish(house,json.writeValueAsString(message),excluded);}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}
 }
 @Override public void deliver(String house,String payload,String excluded) {
  for(var c:List.copyOf(clients.values()))if(c.ticket.house().equals(house)&&!c.socket.getId().equals(excluded)&&c.valid)sendRaw(c.socket,payload);
 }
 private void send(WebSocketSession socket,Object message) {
  try{sendRaw(socket,json.writeValueAsString(message));}catch(Exception e){close(socket);}
 }
 private void sendRaw(WebSocketSession socket,String payload) {
  try {if(socket.isOpen())socket.sendMessage(new TextMessage(payload));}catch(Exception e){close(socket);}
 }
 private void close(WebSocketSession socket){try{socket.close(CloseStatus.POLICY_VIOLATION);}catch(Exception ignored){}}
 private void disconnect(WebSocketSession socket) {
  Client c=clients.remove(socket.getId());seen.remove(socket.getId());previews.remove(socket.getId());
  release(socket.getId());close(socket);
  if(c!=null){c.valid=false;store.leave(c.ticket.house(),socket.getId());presence(c.ticket.house());}
 }
 @Override public synchronized void afterConnectionClosed(WebSocketSession socket,CloseStatus status){disconnect(socket);}
 @Override public synchronized void handleTransportError(WebSocketSession socket,Throwable exception){disconnect(socket);}
 /** 1초마다: 끊긴 연결·로그아웃 정리, 갱신이 끊긴 잠금(다른 앱이 죽은 경우 포함) 정리. */
 @Scheduled(fixedDelay=1000) public synchronized void sweep() {
  long now=now();
  for(var c:List.copyOf(clients.values())) {
   boolean ok=c.socket.isOpen()&&now-seen.getOrDefault(c.socket.getId(),0L)<=SILENT_MS&&valid(c.ticket);
   if(ok)c.valid=true;else disconnect(c.socket);
  }
  var changed=new HashSet<String>();
  for(var e:List.copyOf(held.entrySet()))if(e.getValue().expires()<now){held.remove(e.getKey());store.release(e.getValue());changed.add(e.getValue().house());}
  var active=new HashSet<String>();clients.values().forEach(c->active.add(c.ticket.house()));
  for(String house:active)if(store.purge(house,now))changed.add(house);
  changed.forEach(this::presence);
 }
}
