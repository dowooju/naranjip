package com.naranjip.application;
import com.naranjip.application.CoupleRepository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
@Service @Transactional
public class CoupleService {
 private final CoupleRepository repository;
 private final HouseRepository houses;
 private final SecureRandom random=new SecureRandom();
 public CoupleService(CoupleRepository repository,HouseRepository houses) { this.repository=repository;this.houses=houses; }
 public record Code(String code,String expiresAt) {}
 public record Status(Profile self,Profile partner,String houseId,boolean canEdit,String connectedAt,Code invite) {}
 public record Preview(String inviterName,String houseId,boolean deletesExistingHouse,String existingHouseId) {}
 @Transactional(readOnly=true)
 public Status status(String accountId) {
  UUID id=UUID.fromString(accountId),house=repository.house(id);
  var members=house==null?List.<Member>of():repository.members(house);
  var partner=members.stream().filter(m->!m.profile().id().equals(id)).findFirst().orElse(null);
  var invite=house!=null&&partner==null?repository.activeInvite(house,Instant.now()):null;
  return new Status(repository.profile(id),partner==null?null:partner.profile(),house==null?null:house.toString(),house!=null,
   partner==null?null:members.stream().map(Member::joinedAt).max(String::compareTo).orElse(null),invite==null?null:new Code(invite.code(),invite.expiresAt().toString()));
 }
 public void createHouse(String accountId) {
  UUID id=UUID.fromString(accountId);repository.lockAccounts(id);
  if(repository.house(id)!=null)throw new PlacementException("이미 우리집이 있어요.");
  houses.create(accountId,repository.profile(id).displayName());
 }
 public Code issue(String accountId,String houseId) {
  UUID id=UUID.fromString(accountId),house=parse(houseId);repository.lockAccounts(id);repository.lockHouses(house);
  if(!house.equals(repository.house(id)))throw new PlacementException("우리집에서만 초대 코드를 만들 수 있어요.");
  if(repository.members(house).size()!=1)throw new PlacementException("이미 두 사람이 함께 사는 집이에요.");
  Instant now=Instant.now();repository.revoke(house,now);
  String alphabet="ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789",code;
  do { StringBuilder value=new StringBuilder();for(int n=0;n<7;n++)value.append(alphabet.charAt(random.nextInt(alphabet.length())));code=value.toString(); } while(repository.codeExists(code));
  Instant expires=now.plus(Duration.ofHours(24));
  repository.insertInvite(new Invite(UUID.randomUUID(),code,house,id,expires,null,null));
  return new Code(code,expires.toString());
 }
 public void revoke(String accountId,String houseId) {
  UUID id=UUID.fromString(accountId),house=parse(houseId);repository.lockAccounts(id);repository.lockHouses(house);
  if(!house.equals(repository.house(id)))throw new PlacementException("우리집의 초대만 취소할 수 있어요.");
  repository.revoke(house,Instant.now());
 }
 public Preview preview(String accountId,String code) {
  UUID id=UUID.fromString(accountId);Invite invite=checked(id,code);UUID old=repository.house(id);
  return new Preview(repository.profile(invite.issuedBy()).displayName(),invite.houseId().toString(),old!=null,old==null?null:old.toString());
 }
 public void accept(String accountId,String code,boolean agreeDelete,String expectedHouseId) {
  UUID id=UUID.fromString(accountId);Invite initial=repository.invite(normalize(code));
  if(initial==null)throw invalid();
  repository.lockAccounts(id,initial.issuedBy());
  UUID old=repository.house(id);repository.lockHouses(old,initial.houseId());
  Invite invite=checked(id,code);
  if(!Objects.equals(old==null?null:old.toString(),expectedHouseId))throw new PlacementException("소속 집이 변경됐어요. 초대를 다시 확인해 주세요.");
  if(old!=null&&!agreeDelete)throw new PlacementException("기존의 방 삭제에 동의해야 초대를 수락할 수 있어요.");
  if(old!=null)repository.deleteHouse(old);
  repository.join(invite.houseId(),id);repository.consume(invite.id(),Instant.now());repository.revoke(invite.houseId(),Instant.now());
 }
 private Invite checked(UUID id,String code) {
  Invite invite=repository.invite(normalize(code));
  if(invite==null||invite.consumedAt()!=null||invite.revokedAt()!=null||!invite.expiresAt().isAfter(Instant.now()))throw invalid();
  if(invite.issuedBy().equals(id))throw new PlacementException("자신의 초대 코드는 사용할 수 없어요.");
  UUID old=repository.house(id);
  if(old!=null&&repository.members(old).size()>1)throw new PlacementException("이미 커플로 연결되어 있어요.");
  if(!invite.houseId().equals(repository.house(invite.issuedBy()))||repository.members(invite.houseId()).size()!=1)throw invalid();
  return invite;
 }
 private String normalize(String code) { String value=code==null?"":code.trim().toUpperCase(Locale.ROOT);if(!value.matches("[A-Z0-9]{7}"))throw invalid();return value; }
 private UUID parse(String value) { try{return UUID.fromString(value);}catch(IllegalArgumentException e){throw new PlacementException("집을 찾을 수 없어요.");} }
 private PlacementException invalid() { return new PlacementException("유효하지 않거나 만료된 초대 코드예요."); }
}
