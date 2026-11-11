package com.naranjip.web;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.http.server.*;
import org.springframework.web.util.UriComponentsBuilder;
import java.util.Map;
@Configuration @EnableWebSocket @EnableScheduling
public class LiveConfig implements WebSocketConfigurer {
 private final LiveEditing live;
 public LiveConfig(LiveEditing live){this.live=live;}
 @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
  registry.addHandler(live,"/ws/edit").addInterceptors(new HandshakeInterceptor(){
   public boolean beforeHandshake(ServerHttpRequest request,ServerHttpResponse response,WebSocketHandler handler,Map<String,Object> attributes){
    if(!(request instanceof ServletServerHttpRequest servlet))return false;
    var session=servlet.getServletRequest().getSession(false);
    String ticket=UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("ticket");
    return session!=null&&ticket!=null&&live.consume(ticket,session,attributes);
   }
   public void afterHandshake(ServerHttpRequest request,ServerHttpResponse response,WebSocketHandler handler,Exception exception){}
  }); // Spring's default same-origin policy is intentionally retained.
 }
}
