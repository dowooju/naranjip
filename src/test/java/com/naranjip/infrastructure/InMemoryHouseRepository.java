package com.naranjip.infrastructure;

import com.naranjip.application.*;

import java.util.concurrent.ConcurrentHashMap;


public class InMemoryHouseRepository implements HouseRepository {
    private final ConcurrentHashMap<String, HouseState> houses = new ConcurrentHashMap<>();
    @Override public synchronized HouseState create(String id, String owner) {
        HouseState existing = houses.get(id);
        if (existing != null) { existing.touchedAt = System.currentTimeMillis(); return existing; }
        long cutoff = System.currentTimeMillis() - 2 * 60 * 60 * 1000;
        houses.entrySet().removeIf(entry -> entry.getValue().touchedAt < cutoff);
        if (houses.size() >= 1000) throw new PlacementException("체험 중인 집이 많아요. 잠시 후 다시 시도해 주세요.");
        HouseState house = new HouseState(id, owner);
        houses.put(id, house);
        return house;
    }
    @Override public HouseState find(String id) {
        HouseState house = houses.get(id);
        if (house == null) throw new PlacementException("집이 만료됐어요. 다시 로그인해 주세요.");
        house.touchedAt = System.currentTimeMillis();
        return house;
    }
    @Override public void save(HouseState house) {}
    @Override public boolean isMember(String houseId,String accountId) { return houseId.equals(accountId); }
    @Override public void remove(String id) { houses.remove(id); }
}
