package com.naranjip.web;

import com.fasterxml.jackson.databind.*;
import com.naranjip.application.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.util.*;
import java.util.function.Supplier;

/** Single-replica coordinator. All REST writes and socket leases share this monitor.
 * Database transactions commit before a snapshot is broadcast. */
@Component
public class LiveEditing extends TextWebSocketHandler {
 private final ObjectMapper json; private final FurnitureService furniture; private final HouseRepository houses;
 private final Map<String,Ticket> tickets=new HashMap<>();
 private final Map<String,Client> clients=new HashMap<>();
 private final Map<String,Lease> leases=new HashMap<>();
 private final Map<String,Long> seen=new HashMap<>(), previews=new HashMap<>();
 private record Ticket(String house,String actor,String name,HttpSession session,long expires) {}
 private record Client(WebSocketSession socket,Ticket ticket) {}
 private record Lease(String client,String house,String root,String item,long expires) {}
 public LiveEditing(ObjectMapper json,FurnitureService furniture,HouseRepository houses){this.json=json;this.furniture=furniture;this.houses=houses;}
 public synchronized String ticket(String house,String actor,String name,HttpSession session) {
  furniture.readForMember(house,actor);
  tickets.values().removeIf(t->t.expires()<System.currentTimeMillis());
  if(tickets.size()>1000)throw new PlacementException("잠시 후 다시 연결해 주세요.");
  String id=UUID.randomUUID().toString();tickets.put(id,new Ticket(house,actor,name,session,System.currentTimeMillis()+30000));return id;
 }
 public synchronized boolean consume(String ticket,HttpSession session,Map<String,Object> attributes) {
  Ticket t=tickets.remove(ticket);
  if(t==null||t.expires()<System.currentTimeMillis()||t.session()!=session||!valid(t))return false;
  attributes.put("liveTicket",t);return true;
 }
 private boolean valid(Ticket t) {
  try {
   var resident=(AuthController.Resident)t.session().getAttribute("resident");
   return resident!=null&&resident.houseId().equals(t.actor())&&houses.isMember(t.house(),t.actor());
  }catch(IllegalStateException e){return false;}
 }
 @Override public synchronized void afterConnectionEstablished(WebSocketSession socket) {
  Ticket t=(Ticket)socket.getAttributes().get("liveTicket");
  if(t==null||!valid(t)){close(socket);return;}
  socket.setTextMessageSizeLimit(4096);
  clients.put(socket.getId(),new Client(new org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator(socket,5000,65536),t));
  seen.put(socket.getId(),System.currentTimeMillis());
  send(socket,Map.of("type","hello","clientId",socket.getId(),"house",furniture.readForMember(t.house(),t.actor())));
  presence(t.house());
 }
 private String root(String house,String actor,String id) {
  var item=furniture.readForMember(house,actor).furniture().stream().filter(f->f.id().equals(id)).findFirst().orElseThrow(()->new PlacementException("삭제된 가구예요."));
  return item.parentId()==null?item.id():item.parentId();
 }
 private String key(String house,String root){return house+":"+root;}
 private void expire() {long now=System.currentTimeMillis();leases.values().removeIf(l->l.expires()<now);}
 @Override protected synchronized void handleTextMessage(WebSocketSession socket,TextMessage message) {
  Client c=clients.get(socket.getId());if(c==null)return;
  seen.put(socket.getId(),System.currentTimeMillis());
  if(!valid(c.ticket())){disconnect(socket);return;}
  String requestId="";
  try {
   JsonNode m=json.readTree(message.getPayload());String type=m.path("type").asText();requestId=m.path("requestId").asText();
   expire();String house=c.ticket().house(),actor=c.ticket().actor(),client=socket.getId();
   if(type.equals("ping")) {
    leases.replaceAll((k,l)->l.client().equals(client)?new Lease(client,l.house(),l.root(),l.item(),System.currentTimeMillis()+15000):l);
    send(socket,Map.of("type","pong"));return;
   }
   if(type.equals("release")) {release(client);presence(house);return;}
   String id=m.path("id").asText(),root=root(house,actor,id),key=key(house,root);
   Lease lease=leases.get(key);
   if(type.equals("lock")) {
    if(lease!=null&&!lease.client().equals(client))throw new PlacementException("상대방이 이 가구를 편집 중이에요.");
    release(client);
    leases.put(key,new Lease(client,house,root,id,System.currentTimeMillis()+15000));
    send(socket,Map.of("type","locked","requestId",requestId,"id",id));presence(house);
   } else if(type.equals("preview")) {
    long now=System.currentTimeMillis();if(now-previews.getOrDefault(client,0L)<60)return;previews.put(client,now);
    if(lease==null||!lease.client().equals(client))throw new PlacementException("편집 권한이 만료됐어요. 다시 선택해 주세요.");
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
  var own=leases.values().stream().filter(l->l.client().equals(client)).toList();
  leases.values().removeIf(l->l.client().equals(client));
  for(var l:own)broadcast(l.house(),Map.of("type","previewEnd","clientId",client),null);
 }
 public synchronized <T> T mutate(String house,String actor,String client,String item,Supplier<T> write) {
  if(!houses.isMember(house,actor))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
  expire();
  if(client!=null) {
   Client c=clients.get(client);
   if(c==null||!valid(c.ticket())||!c.ticket().actor().equals(actor)||!c.ticket().house().equals(house))throw new PlacementException("실시간 연결이 끊겼어요. 다시 연결한 뒤 시도해 주세요.");
  }
  if(item!=null) {
   Lease l=leases.get(key(house,root(house,actor,item)));
   if(l!=null&&!l.client().equals(client))throw new PlacementException("상대방이 이 가구를 편집 중이에요.");
   if(client!=null&&l==null)throw new PlacementException("편집 권한이 만료됐어요. 다시 선택해 주세요.");
  }
  T result=write.get();
  // Never push note bodies: each client re-fetches through the note visibility API.
  broadcast(house,Map.of("type","snapshot","house",furniture.readForMember(house,actor)),null);
  return result;
 }
 public synchronized void available(String house,String actor,String client,String target) {
  if(target==null)return;
  Lease l=leases.get(key(house,root(house,actor,target)));
  if(l!=null&&!l.client().equals(client))throw new PlacementException("상대방이 붙일 가구를 편집 중이에요.");
 }
 public synchronized long revision(String house,String actor,String client,long original) {
  return client==null?original:furniture.readForMember(house,actor).revision();
 }
 private void presence(String house) {
  var people=clients.entrySet().stream().filter(e->e.getValue().ticket().house().equals(house))
   .map(e->Map.of("clientId",e.getKey(),"name",e.getValue().ticket().name())).toList();
  var locks=leases.values().stream().filter(l->l.house().equals(house)).map(l->Map.of("clientId",l.client(),"rootId",l.root(),"itemId",l.item())).toList();
  broadcast(house,Map.of("type","presence","people",people,"locks",locks),null);
 }
 private void broadcast(String house,Object message,String excluded) {
  for(var c:List.copyOf(clients.values()))if(c.ticket().house().equals(house)&&!c.socket().getId().equals(excluded)&&valid(c.ticket()))send(c.socket(),message);
 }
 private void send(WebSocketSession socket,Object message) {
  try {if(socket.isOpen())socket.sendMessage(new TextMessage(json.writeValueAsString(message)));}catch(Exception e){close(socket);}
 }
 private void close(WebSocketSession socket){try{socket.close(CloseStatus.POLICY_VIOLATION);}catch(Exception ignored){}}
 private void disconnect(WebSocketSession socket){Client c=clients.remove(socket.getId());seen.remove(socket.getId());previews.remove(socket.getId());release(socket.getId());close(socket);if(c!=null)presence(c.ticket().house());}
 @Override public synchronized void afterConnectionClosed(WebSocketSession socket,CloseStatus status){disconnect(socket);}
 @Override public synchronized void handleTransportError(WebSocketSession socket,Throwable exception){disconnect(socket);}
 @Scheduled(fixedDelay=1000) public synchronized void sweep() {
  var before=new HashSet<String>();leases.values().forEach(l->before.add(l.house()));expire();
  tickets.values().removeIf(t->t.expires()<System.currentTimeMillis());
  for(var c:List.copyOf(clients.values()))if(!c.socket().isOpen()||System.currentTimeMillis()-seen.getOrDefault(c.socket().getId(),0L)>20000||!valid(c.ticket()))disconnect(c.socket());
  before.forEach(this::presence);
 }
}
