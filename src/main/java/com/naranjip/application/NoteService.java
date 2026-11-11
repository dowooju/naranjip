package com.naranjip.application;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;
@Service @Transactional
public class NoteService {
 private final HouseRepository houses;private final NoteRepository notes;
 public NoteService(HouseRepository houses,NoteRepository notes){this.houses=houses;this.notes=notes;}
 public record Preview(String id,String preview,boolean isPublic) {}
 public List<Preview> list(String house,String actor) {
  var state=houses.find(house);boolean member=actor!=null&&houses.isMember(house,actor);
  return state.furniture.stream().filter(f->f.typeId().equals("note")).map(f->notes.find(UUID.fromString(f.id())))
   .filter(n->member||n.isPublic()).map(n->new Preview(n.id(),n.body().lines().findFirst().orElse("").codePoints().limit(40).collect(StringBuilder::new,StringBuilder::appendCodePoint,StringBuilder::append).toString(),n.isPublic())).toList();
 }
 public NoteRepository.Note read(String house,String id,String actor) {
  requireNote(houses.find(house),id);
  var note=notes.find(UUID.fromString(id));
  if(!note.isPublic()&&(actor==null||!houses.isMember(house,actor)))throw new ResponseStatusException(HttpStatus.NOT_FOUND);
  return note;
 }
 public NoteRepository.Note save(String house,String id,String actor,String body,boolean isPublic,long revision) {
  var state=houses.find(house);
  if(!houses.isMember(house,actor))throw new ResponseStatusException(HttpStatus.FORBIDDEN);
  requireNote(state,id);
  if(body==null||body.length()>200)throw new PlacementException("쪽지는 200자 이내로 작성해 주세요.");
  var current=notes.find(UUID.fromString(id));
  if(current.revision()!=revision)throw new PlacementException("다른 창에서 쪽지가 바뀌었어요. 최신 내용을 불러온 뒤 다시 작성해 주세요.");
  notes.save(UUID.fromString(id),body,isPublic,revision+1);
  state.revision++;houses.save(state);
  return notes.find(UUID.fromString(id));
 }
 private void requireNote(HouseState house,String id) {
  if(house.furniture.stream().noneMatch(f->f.id().equals(id)&&f.typeId().equals("note")))
   throw new ResponseStatusException(HttpStatus.NOT_FOUND);
 }
}
