package com.naranjip.application;
import java.util.UUID;
public interface NoteRepository {
 record Note(String id,String body,boolean isPublic,long revision) {}
 Note find(UUID furnitureId);
 void save(UUID furnitureId,String body,boolean isPublic,long revision);
}
