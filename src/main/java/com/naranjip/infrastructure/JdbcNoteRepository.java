package com.naranjip.infrastructure;
import com.naranjip.application.NoteRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.UUID;
@Repository
public class JdbcNoteRepository implements NoteRepository {
 private final JdbcTemplate jdbc;
 public JdbcNoteRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public Note find(UUID id) {
  return jdbc.query("SELECT * FROM note_contents WHERE furniture_id=?",(r,n)->new Note(id.toString(),r.getString("body"),r.getBoolean("is_public"),r.getLong("revision")),id)
   .stream().findFirst().orElse(new Note(id.toString(),"",false,0));
 }
 public void save(UUID id,String body,boolean isPublic,long revision) {
  if(revision==1)jdbc.update("INSERT INTO note_contents(furniture_id,body,is_public,revision) VALUES(?,?,?,?)",id,body,isPublic,revision);
  else jdbc.update("UPDATE note_contents SET body=?,is_public=?,revision=?,updated_at=CURRENT_TIMESTAMP WHERE furniture_id=?",body,isPublic,revision,id);
 }
}
