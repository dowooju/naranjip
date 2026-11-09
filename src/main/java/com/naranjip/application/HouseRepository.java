package com.naranjip.application;

public interface HouseRepository {
    HouseState create(String id, String owner);
    HouseState find(String id);
    void save(HouseState house);
    void remove(String id);
}
