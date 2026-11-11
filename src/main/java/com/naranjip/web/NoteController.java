package com.naranjip.web;
import com.naranjip.application.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import java.util.*;
@RestController @RequestMapping("/api/houses/{houseId}/notes")
public class NoteController {
 private final NoteService notes;private final AuthController auth;
 public NoteController(NoteService notes,AuthController auth){this.notes=notes;this.auth=auth;}
 public record Input(@NotNull @Size(max=200) String body,boolean isPublic,@Min(0) long revision) {}
 @GetMapping public ResponseEntity<List<NoteService.Preview>> list(@PathVariable UUID houseId,HttpServletRequest r) {
  return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(notes.list(houseId.toString(),auth.optionalAccountId(r)));
 }
 @GetMapping("/{id}") public ResponseEntity<NoteRepository.Note> read(@PathVariable UUID houseId,@PathVariable UUID id,HttpServletRequest r) {
  return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(notes.read(houseId.toString(),id.toString(),auth.optionalAccountId(r)));
 }
 @PutMapping("/{id}") public NoteRepository.Note save(@PathVariable UUID houseId,@PathVariable UUID id,@Valid @RequestBody Input input,HttpServletRequest r) {
  return notes.save(houseId.toString(),id.toString(),auth.accountId(r),input.body(),input.isPublic(),input.revision());
 }
}
