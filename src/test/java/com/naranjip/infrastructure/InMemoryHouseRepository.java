package com.naranjip.infrastructure;

import com.naranjip.application.*;

import java.util.concurrent.ConcurrentHashMap;

/** 배치 규칙 단위 테스트용. JdbcHouseRepository처럼 읽을 때 복사본을 주고, 저장할 때 revision이 하나 앞서 있어야만 반영한다. */
public class InMemoryHouseRepository implements HouseRepository {
    private final ConcurrentHashMap<String, HouseState> houses = new ConcurrentHashMap<>();
    private static HouseState copy(HouseState source) {
        var copy = new HouseState(source.id, source.owner);
        copy.revision = source.revision;
        copy.furniture.addAll(source.furniture);
        return copy;
    }
    @Override public HouseState create(String id, String owner) {
        return copy(houses.computeIfAbsent(id, key -> new HouseState(key, owner)));
    }
    @Override public HouseState find(String id) {
        HouseState house = houses.get(id);
        if (house == null) throw new PlacementException("집을 찾을 수 없어요.");
        return copy(house);
    }
    @Override public void save(HouseState house) {
        houses.compute(house.id, (id, current) -> {
            if (current == null || current.revision != house.revision - 1) throw new PlacementException("다른 창에서 집이 바뀌었어요. 최신 배치를 다시 불러옵니다.");
            return copy(house);
        });
    }
    @Override public boolean isMember(String houseId,String accountId) { return houseId.equals(accountId); }
    @Override public void remove(String id) { houses.remove(id); }
}
