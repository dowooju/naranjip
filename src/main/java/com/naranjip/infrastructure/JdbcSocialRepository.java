package com.naranjip.infrastructure;
import com.naranjip.application.SocialRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;
@Repository
public class JdbcSocialRepository implements SocialRepository {
 private final JdbcTemplate jdbc;
 public JdbcSocialRepository(JdbcTemplate jdbc) {this.jdbc=jdbc;}
 public boolean admin(UUID id) {return Boolean.TRUE.equals(jdbc.queryForObject("SELECT is_admin FROM accounts WHERE id=?",Boolean.class,id));}
 public UUID active(boolean lock) {return jdbc.queryForObject("SELECT theme_id FROM active_theme WHERE singleton_id=1"+(lock?" FOR UPDATE":""),UUID.class);}
 private Theme mapTheme(java.sql.ResultSet r,int n) throws java.sql.SQLException {return new Theme(r.getObject("id",UUID.class),r.getString("title"),r.getString("description"),r.getTimestamp("activated_at").toInstant().toString());}
 public Theme theme(UUID id) {return id==null?null:jdbc.query("SELECT * FROM themes WHERE id=?",this::mapTheme,id).stream().findFirst().orElse(null);}
 public List<Theme> history() {return jdbc.query("SELECT * FROM themes ORDER BY activated_at DESC,id DESC LIMIT 30",this::mapTheme);}
 public void activate(UUID id,String title,String description,UUID author) {
  jdbc.update("INSERT INTO themes(id,title,description,created_by) VALUES(?,?,?,?)",id,title,description,author);
  jdbc.update("UPDATE active_theme SET theme_id=? WHERE singleton_id=1",id);
 }
 public long likes(UUID house,UUID theme) {return theme==null?jdbc.queryForObject("SELECT COUNT(*) FROM general_likes WHERE house_id=?",Long.class,house):jdbc.queryForObject("SELECT COUNT(*) FROM theme_likes WHERE house_id=? AND theme_id=?",Long.class,house,theme);}
 public boolean liked(UUID house,UUID voter,UUID theme) {
  if(voter==null)return false;
  return (theme==null?jdbc.queryForObject("SELECT COUNT(*) FROM general_likes WHERE house_id=? AND voter_id=?",Long.class,house,voter):jdbc.queryForObject("SELECT COUNT(*) FROM theme_likes WHERE house_id=? AND voter_id=? AND theme_id=?",Long.class,house,voter,theme))>0;
 }
 public void like(UUID house,UUID voter,UUID theme,boolean enabled) {
  // Caller serializes all mutations of this house.
  if(liked(house,voter,theme)==enabled)return;
  if(theme==null) {
   if(enabled)jdbc.update("INSERT INTO general_likes(house_id,voter_id) VALUES(?,?)",house,voter);
   else jdbc.update("DELETE FROM general_likes WHERE house_id=? AND voter_id=?",house,voter);
  } else {
   if(enabled)jdbc.update("INSERT INTO theme_likes(theme_id,house_id,voter_id) VALUES(?,?,?)",theme,house,voter);
   else jdbc.update("DELETE FROM theme_likes WHERE theme_id=? AND house_id=? AND voter_id=?",theme,house,voter);
  }
 }
 public long commentCount(UUID house) {return jdbc.queryForObject("SELECT COUNT(*) FROM guestbook WHERE house_id=?",Long.class,house);}
 private static final String COMMENT_SQL="SELECT g.*,a.login_id,m.house_id AS author_house FROM guestbook g JOIN accounts a ON a.id=g.author_id LEFT JOIN house_members m ON m.account_id=a.id ";
 private Comment mapComment(java.sql.ResultSet r,int n) throws java.sql.SQLException {
  return new Comment(r.getObject("id",UUID.class),r.getObject("house_id",UUID.class),r.getObject("author_id",UUID.class),r.getString("login_id"),r.getString("author_house"),r.getString("body"),r.getTimestamp("created_at").toInstant().toString());
 }
 public List<Comment> comments(UUID house,int offset) {return jdbc.query(COMMENT_SQL+"WHERE g.house_id=? ORDER BY g.created_at DESC,g.id DESC LIMIT 21 OFFSET ?",this::mapComment,house,offset);}
 public Comment comment(UUID id) {return jdbc.query(COMMENT_SQL+"WHERE g.id=?",this::mapComment,id).stream().findFirst().orElse(null);}
 public void addComment(UUID house,UUID author,String body) {jdbc.update("INSERT INTO guestbook(id,house_id,author_id,body) VALUES(?,?,?,?)",UUID.randomUUID(),house,author,body);}
 public void deleteComment(UUID id) {jdbc.update("DELETE FROM guestbook WHERE id=?",id);}
 public List<Candidate> candidates(UUID theme) {
  String votes=theme==null?"(SELECT COUNT(*) FROM general_likes l WHERE l.house_id=h.id)":"(SELECT COUNT(*) FROM theme_likes l WHERE l.house_id=h.id AND l.theme_id=?)";
  String sql="SELECT h.id,"+votes+" AS votes,(SELECT COUNT(*) FROM guestbook g WHERE g.house_id=h.id) AS comments FROM houses h WHERE EXISTS(SELECT 1 FROM placed_furniture f WHERE f.house_id=h.id) ORDER BY votes DESC,h.id LIMIT 50";
  return jdbc.query(sql,(r,n)-> {
   UUID id=r.getObject("id",UUID.class);
   String names=String.join(" ♡ ",jdbc.queryForList("SELECT a.login_id FROM house_members m JOIN accounts a ON a.id=m.account_id WHERE m.house_id=? ORDER BY m.joined_at,a.id",String.class,id));
   return new Candidate(id.toString(),names,r.getLong("votes"),r.getLong("comments"));
  },theme==null?new Object[]{}:new Object[]{theme});
 }
 public List<UUID> visitable(UUID exclude) {
  return jdbc.query("SELECT h.id FROM houses h WHERE EXISTS(SELECT 1 FROM placed_furniture f WHERE f.house_id=h.id)"+(exclude==null?"":" AND h.id<>?"),(r,n)->r.getObject(1,UUID.class),exclude==null?new Object[]{}:new Object[]{exclude});
 }
}
