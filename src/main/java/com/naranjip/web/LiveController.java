package com.naranjip.web;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
public class LiveController {
 private final LiveEditing live;private final AuthController auth;
 public LiveController(LiveEditing live,AuthController auth){this.live=live;this.auth=auth;}
 @PostMapping("/api/live/ticket") public Map<String,String> ticket(@RequestParam String houseId,HttpServletRequest request){
  String actor=auth.accountId(request);
  return Map.of("ticket",live.ticket(houseId,actor,auth.me(request).user().displayName(),request.getSession(false)));
 }
}
