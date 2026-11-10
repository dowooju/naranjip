package com.naranjip.application;
import java.time.Instant;
import java.util.*;
public interface CoupleRepository {
 record Profile(UUID id,String displayName,String joinedAt) {}
 record Member(Profile profile,String joinedAt) {}
 record Invite(UUID id,String code,UUID houseId,UUID issuedBy,Instant expiresAt,Instant consumedAt,Instant revokedAt) {}
 Profile profile(UUID id);
 UUID house(UUID account);
 List<Member> members(UUID house);
 void lockAccounts(UUID... accounts);
 void lockHouses(UUID... houses);
 Invite invite(String code);
 Invite activeInvite(UUID house,Instant now);
 void revoke(UUID house,Instant now);
 boolean codeExists(String code);
 void insertInvite(Invite invite);
 void consume(UUID invite,Instant now);
 void join(UUID house,UUID account);
 void deleteHouse(UUID house);
}
