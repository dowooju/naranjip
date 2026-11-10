package com.naranjip.application;
import java.util.*;
public interface SocialRepository {
 record Theme(UUID id,String title,String description,String activatedAt) {}
 record Comment(UUID id,UUID houseId,UUID authorId,String authorName,String authorHouseId,String body,String createdAt) {}
 record Candidate(String houseId,String residents,long likes,long comments) {}
 boolean admin(UUID account);
 UUID active(boolean lock);
 Theme theme(UUID id);
 List<Theme> history();
 void activate(UUID id,String title,String description,UUID author);
 long likes(UUID house,UUID theme);
 boolean liked(UUID house,UUID voter,UUID theme);
 void like(UUID house,UUID voter,UUID theme,boolean enabled);
 long commentCount(UUID house);
 List<Comment> comments(UUID house,int offset);
 Comment comment(UUID id);
 void addComment(UUID house,UUID author,String body);
 void deleteComment(UUID id);
 List<Candidate> candidates(UUID theme);
 List<UUID> visitable(UUID exclude);
}
