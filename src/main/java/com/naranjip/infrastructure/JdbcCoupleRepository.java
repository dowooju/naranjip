package com.naranjip.infrastructure;
import com.naranjip.application.CoupleRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.sql.*;
import java.util.*;
@Repository
public class JdbcCoupleRepository implements CoupleRepository {
 private final JdbcTemplate jdbc;
 public JdbcCoupleRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
 public Profile profile(UUID id) {
  return jdbc.query("SELECT id,login_id,created_at FROM accounts WHERE id=?",(rs,n)->new Profile(id,rs.getString("login_id"),rs.getTimestamp("created_at").toInstant().toString()),id).stream().findFirst().orElse(null);
 }
 public UUID house(UUID account) {
  return jdbc.query("SELECT house_id FROM house_members WHERE account_id=?",(rs,n)->rs.getObject(1,UUID.class),account).stream().findFirst().orElse(null);
 }
 public List<Member> members(UUID house) {
  return jdbc.query("SELECT a.id,a.login_id,a.created_at,m.joined_at FROM house_members m JOIN accounts a ON a.id=m.account_id WHERE m.house_id=? ORDER BY m.joined_at,a.id",
   (rs,n)->new Member(new Profile(rs.getObject("id",UUID.class),rs.getString("login_id"),rs.getTimestamp("created_at").toInstant().toString()),rs.getTimestamp("joined_at").toInstant().toString()),house);
 }
 public void lockAccounts(UUID... ids) { lock("accounts",ids); }
 public void lockHouses(UUID... ids) { lock("houses",ids); }
 private void lock(String table,UUID[] ids) {
  Arrays.stream(ids).filter(Objects::nonNull).distinct().sorted().forEach(id->jdbc.queryForList("SELECT id FROM "+table+" WHERE id=? FOR UPDATE",id));
 }
 private Invite map(ResultSet rs,int n) throws SQLException {
  return new Invite(rs.getObject("id",UUID.class),rs.getString("code"),rs.getObject("house_id",UUID.class),rs.getObject("issued_by",UUID.class),rs.getTimestamp("expires_at").toInstant(),instant(rs.getTimestamp("consumed_at")),instant(rs.getTimestamp("revoked_at")));
 }
 private Instant instant(Timestamp t) { return t==null?null:t.toInstant(); }
 public Invite invite(String code) { return jdbc.query("SELECT * FROM invites WHERE code=?",this::map,code).stream().findFirst().orElse(null); }
 public Invite activeInvite(UUID house,Instant now) {
  return jdbc.query("SELECT * FROM invites WHERE house_id=? AND consumed_at IS NULL AND revoked_at IS NULL AND expires_at>? ORDER BY expires_at DESC",this::map,house,Timestamp.from(now)).stream().findFirst().orElse(null);
 }
 public void revoke(UUID house,Instant now) { jdbc.update("UPDATE invites SET revoked_at=? WHERE house_id=? AND consumed_at IS NULL AND revoked_at IS NULL",Timestamp.from(now),house); }
 public boolean codeExists(String code) { return jdbc.queryForObject("SELECT COUNT(*) FROM invites WHERE code=?",Integer.class,code)>0; }
 public void insertInvite(Invite i) { jdbc.update("INSERT INTO invites(id,code,house_id,issued_by,expires_at) VALUES (?,?,?,?,?)",i.id(),i.code(),i.houseId(),i.issuedBy(),Timestamp.from(i.expiresAt())); }
 public void consume(UUID invite,Instant now) { jdbc.update("UPDATE invites SET consumed_at=? WHERE id=?",Timestamp.from(now),invite); }
 public void join(UUID house,UUID account) { jdbc.update("INSERT INTO house_members(account_id,house_id) VALUES (?,?)",account,house); }
 public void deleteHouse(UUID house) { jdbc.update("DELETE FROM houses WHERE id=?",house); }
}
