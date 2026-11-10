package com.naranjip.web;
import com.naranjip.application.CoupleService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api")
public class CoupleController {
 private final CoupleService service;
 private final AuthController auth;
 public CoupleController(CoupleService service,AuthController auth) { this.service=service;this.auth=auth; }
 public record Input(@NotBlank @Size(max=7) String code,boolean agreeDelete,String existingHouseId) {}
 @GetMapping("/couple") public CoupleService.Status status(HttpServletRequest r) { return service.status(auth.accountId(r)); }
 @PostMapping("/houses") public CoupleService.Status create(HttpServletRequest r) { String id=auth.accountId(r);service.createHouse(id);return service.status(id); }
 @PostMapping("/houses/{id}/invite") public CoupleService.Code issue(@PathVariable String id,HttpServletRequest r) { return service.issue(auth.accountId(r),id); }
 @DeleteMapping("/houses/{id}/invite") public void revoke(@PathVariable String id,HttpServletRequest r) { service.revoke(auth.accountId(r),id); }
 @PostMapping("/invites/preview") public CoupleService.Preview preview(@Valid @RequestBody Input input,HttpServletRequest r) { return service.preview(auth.accountId(r),input.code()); }
 @PostMapping("/invites/accept") public CoupleService.Status accept(@Valid @RequestBody Input input,HttpServletRequest r) {
  String id=auth.accountId(r);service.accept(id,input.code(),input.agreeDelete(),input.existingHouseId());return service.status(id);
 }
}
