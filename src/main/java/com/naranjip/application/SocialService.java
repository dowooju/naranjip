package com.naranjip.application;
import com.naranjip.application.SocialRepository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;
@Service @Transactional
public class SocialService {
 private final SocialRepository repository;private final HouseRepository houses;private final CoupleRepository members;
 public SocialService(SocialRepository repository,HouseRepository houses,CoupleRepository members) {this.repository=repository;this.houses=houses;this.members=members;}
 public boolean isAdmin(String id) {return id!=null&&repository.admin(UUID.fromString(id));}
 private void admin(String id) {if(!isAdmin(id))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"관리자만 사용할 수 있어요.");}
 public record Stats(long generalLikes,long themeLikes,long comments,boolean generalLiked,boolean themeLiked,boolean owner,Theme theme) {}
 public record CommentView(Comment comment,boolean canDelete) {}
 public record Page(List<CommentView> items,boolean hasMore) {}
 public record Ranked(Candidate house,Long rank) {}
 public record Ranking(Theme theme,List<Ranked> houses) {}
 public record AdminState(Theme current,List<Theme> history) {}
 public Theme theme() {return repository.theme(repository.active(false));}
 public AdminState adminState(String actor) {admin(actor);return new AdminState(theme(),repository.history());}
 public Theme activate(String actor,String title,String description,UUID expected) {
  admin(actor);UUID current=repository.active(true);
  if(!Objects.equals(current,expected))throw new PlacementException("다른 관리자가 콘셉트를 바꿨어요. 새로고침해 주세요.");
  if(title==null||title.isBlank()||title.length()>80||description==null||description.length()>1000)throw new PlacementException("제목은 1~80자, 안내문은 1,000자 이내로 입력해 주세요.");
  UUID id=UUID.randomUUID();repository.activate(id,title.strip(),description.strip(),UUID.fromString(actor));return repository.theme(id);
 }
 public Stats stats(String house,String actor) {
  houses.find(house);UUID id=UUID.fromString(house),user=actor==null?null:UUID.fromString(actor),theme=repository.active(false);
  return new Stats(repository.likes(id,null),theme==null?0:repository.likes(id,theme),repository.commentCount(id),repository.liked(id,user,null),theme!=null&&repository.liked(id,user,theme),actor!=null&&houses.isMember(house,actor),repository.theme(theme));
 }
 public Stats like(String house,String actor,boolean themed,UUID expectedTheme,boolean enabled) {
  UUID theme=themed?repository.active(true):null;
  if(themed&&(theme==null||!theme.equals(expectedTheme)))throw new PlacementException("이번 주 콘셉트가 변경됐어요. 집을 다시 불러와 주세요.");
  houses.find(house);
  if(houses.isMember(house,actor))throw new PlacementException("우리집에는 좋아요를 누를 수 없어요.");
  repository.like(UUID.fromString(house),UUID.fromString(actor),theme,enabled);return stats(house,actor);
 }
 public Page comments(String house,String actor,int page) {
  houses.find(house);if(page<0||page>10000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
  boolean owner=actor!=null&&houses.isMember(house,actor);
  var rows=repository.comments(UUID.fromString(house),page*20);
  return new Page(rows.stream().limit(20).map(c->new CommentView(c,owner||c.authorId().toString().equals(actor))).toList(),rows.size()>20);
 }
 public void comment(String house,String actor,String body) {
  houses.find(house);
  if(body==null||body.isBlank()||body.length()>200)throw new PlacementException("코멘트는 1~200자로 입력해 주세요.");
  repository.addComment(UUID.fromString(house),UUID.fromString(actor),body.strip());
 }
 public void deleteComment(String actor,UUID id) {
  var comment=repository.comment(id);if(comment==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND);
  houses.find(comment.houseId().toString());
  if(!comment.authorId().toString().equals(actor)&&!houses.isMember(comment.houseId().toString(),actor))throw new ResponseStatusException(HttpStatus.FORBIDDEN);
  repository.deleteComment(id);
 }
 public Ranking rankings(boolean themed) {
  Theme active=theme();if(themed&&active==null)return new Ranking(null,List.of());
  var rows=repository.candidates(themed?active.id():null);var results=new ArrayList<Ranked>();long previous=-1,rank=0,index=0;
  for(var row:rows) {index++;if(row.likes()!=previous)rank=index;previous=row.likes();results.add(new Ranked(row,row.likes()==0?null:rank));}
  return new Ranking(active,results);
 }
 public String random(String actor) {
  var candidates=repository.visitable(actor==null?null:members.house(UUID.fromString(actor)));
  return candidates.isEmpty()?null:candidates.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(candidates.size())).toString();
 }
}
