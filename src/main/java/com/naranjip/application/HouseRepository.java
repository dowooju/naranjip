package com.naranjip.application;

public interface HouseRepository {
    HouseState create(String id, String owner);
    HouseState find(String id);
    void remove(String id);
}
