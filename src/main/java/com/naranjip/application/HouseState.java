package com.naranjip.application;

import java.util.ArrayList;
import java.util.List;

/** Mutable state is only accessed under the house monitor in FurnitureService. */
public final class HouseState {
    public final String id;
    public final String owner;
    public long revision;
    public volatile long touchedAt = System.currentTimeMillis();
    public final List<PlacedFurniture> furniture = new ArrayList<>();
    public HouseState(String id, String owner) { this.id = id; this.owner = owner; }
    public record PlacedFurniture(String id, String typeId, int x, int y, int rotation,
                                  String wall, String parentId, int slot) {}
    public record Snapshot(String id, String ownerName, int width, int height, long revision,
                           List<PlacedFurniture> furniture) {}
}
