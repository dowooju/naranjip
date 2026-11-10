package com.naranjip.web;
import com.naranjip.application.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.*;
@RestController @RequestMapping("/api")
public class SocialController {
 private final SocialService social;private final AuthController auth;private final FurnitureService furniture;
 public SocialController(SocialService social,AuthController auth,FurnitureService furniture) {this.social=social;this.auth=auth;this.furniture=furniture;}
 public record Vote(UUID themeId) {}
 public record Text(@NotBlank @Size(max=200) String body) {}
 public record ThemeInput(@NotBlank @Size(max=80) String title,@NotNull @Size(max=1000) String description,UUID expectedThemeId) {}
 private String optional(HttpServletRequest r) {return auth.optionalAccountId(r);}
 @GetMapping("/theme") public Map<String,Object> theme() {var result=new HashMap<String,Object>();result.put("current",social.theme());return result;}
 @GetMapping("/rankings") public SocialService.Ranking rankings(@RequestParam(defaultValue="general") String type) {
  if(!Set.of("general","theme").contains(type))throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST);
  return social.rankings(type.equals("theme"));
 }
 @GetMapping("/houses/random") public Map<String,Object> random(HttpServletRequest r) {var result=new HashMap<String,Object>();result.put("houseId",social.random(optional(r)));return result;}
 @GetMapping("/visits/{id}") public HouseState.Snapshot visit(@PathVariable UUID id) {return furniture.read(id.toString());}
 @GetMapping("/houses/{id}/social") public SocialService.Stats stats(@PathVariable UUID id,HttpServletRequest r) {return social.stats(id.toString(),optional(r));}
 @PutMapping("/houses/{id}/likes/{kind}") public SocialService.Stats like(@PathVariable UUID id,@PathVariable String kind,@RequestBody Vote vote,HttpServletRequest r) {return vote(id,kind,vote,r,true);}
 @DeleteMapping("/houses/{id}/likes/{kind}") public SocialService.Stats unlike(@PathVariable UUID id,@PathVariable String kind,@RequestBody Vote vote,HttpServletRequest r) {return vote(id,kind,vote,r,false);}
 private SocialService.Stats vote(UUID id,String kind,Vote vote,HttpServletRequest r,boolean enabled) {
  if(!Set.of("general","theme").contains(kind))throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST);
  return social.like(id.toString(),auth.accountId(r),kind.equals("theme"),vote.themeId(),enabled);
 }
 @GetMapping("/houses/{id}/guestbook") public SocialService.Page comments(@PathVariable UUID id,@RequestParam(defaultValue="0") int page,HttpServletRequest r) {return social.comments(id.toString(),optional(r),page);}
 @PostMapping("/houses/{id}/guestbook") @ResponseStatus(HttpStatus.CREATED)
 public void comment(@PathVariable UUID id,@Valid @RequestBody Text text,HttpServletRequest r) {social.comment(id.toString(),auth.accountId(r),text.body());}
 @DeleteMapping("/guestbook/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
 public void delete(@PathVariable UUID id,HttpServletRequest r) {social.deleteComment(auth.accountId(r),id);}
 @GetMapping("/admin/themes") public SocialService.AdminState admin(HttpServletRequest r) {return social.adminState(auth.accountId(r));}
 @PostMapping("/admin/themes") public SocialRepository.Theme activate(@Valid @RequestBody ThemeInput input,HttpServletRequest r) {
  return social.activate(auth.accountId(r),input.title(),input.description(),input.expectedThemeId());
 }
}
