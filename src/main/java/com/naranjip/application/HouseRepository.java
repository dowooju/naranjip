package com.naranjip.application;

public interface HouseRepository {
    HouseState create(String id, String owner);
    HouseState find(String id);
    void save(HouseState house);
    boolean isMember(String houseId,String accountId);
    void remove(String id);
}
