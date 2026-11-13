package com.naranjip.application;

import java.util.ArrayList;
import java.util.List;

/** A detached layout snapshot, validated and persisted inside one database transaction. */
public final class HouseState {
    public final String id;
    public final String owner;
    public long revision;
    public final List<PlacedFurniture> furniture = new ArrayList<>();
    /** DB에서 읽어 온 그대로의 가구. 저장할 때 이것과 비교해 바뀐 행만 쓴다. */
    public final List<PlacedFurniture> saved = new ArrayList<>();
    public HouseState(String id, String owner) { this.id = id; this.owner = owner; }
    public record PlacedFurniture(String id, String typeId, int x, int y, boolean rotation,
                                  String wall, String parentId, int slot) {}
    public record Snapshot(String id, String ownerName, int width, int height, long revision,
                           List<PlacedFurniture> furniture) {}
}
