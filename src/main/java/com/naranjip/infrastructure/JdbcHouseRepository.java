package com.naranjip.infrastructure;

import com.naranjip.application.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.Comparator;
import java.util.UUID;

@Repository
public class JdbcHouseRepository implements HouseRepository {
    private final JdbcTemplate jdbc;
    public JdbcHouseRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override @Transactional
    public HouseState create(String id, String owner) {
        UUID key = UUID.fromString(id);
        // Serialize first-house creation for concurrent logins to the same account.
        var accounts = jdbc.queryForList("SELECT id FROM accounts WHERE id = ? FOR UPDATE", key);
        if (accounts.isEmpty()) throw new PlacementException("회원을 찾을 수 없어요.");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM houses WHERE id = ?", Integer.class, key) == 0) {
            jdbc.update("INSERT INTO houses(id, owner_id) VALUES (?, ?)", key, key);
        }
        return find(id);
    }

    @Override @Transactional public HouseState find(String id) {
        UUID key = UUID.fromString(id);
        // Caller holds a transaction throughout validation + save. Locks work across app instances.
        var rows = jdbc.query("SELECT id, owner_id, revision FROM houses WHERE id=? FOR UPDATE",
            (rs, index) -> {
                String owner = jdbc.queryForObject("SELECT login_id FROM accounts WHERE id=?", String.class, rs.getObject("owner_id",UUID.class));
                HouseState state = new HouseState(rs.getObject("id",UUID.class).toString(),owner);
                state.revision = rs.getLong("revision"); return state;
            }, key);
        if (rows.isEmpty()) throw new PlacementException("집을 찾을 수 없어요. 다시 로그인해 주세요.");
        HouseState house = rows.getFirst();
        house.furniture.addAll(jdbc.query("SELECT * FROM placed_furniture WHERE house_id=? ORDER BY sort_order",
            (rs,index) -> new HouseState.PlacedFurniture(rs.getObject("id",UUID.class).toString(), rs.getString("type_id"),
                rs.getInt("x"),rs.getInt("y"),rs.getBoolean("rotation"),rs.getString("wall"),
                rs.getObject("parent_id")==null?null:rs.getObject("parent_id",UUID.class).toString(),rs.getInt("slot")),key));
        return house;
    }

    @Override @Transactional public void save(HouseState house) {
        UUID key=UUID.fromString(house.id);
        int changed=jdbc.update("UPDATE houses SET revision=?, updated_at=CURRENT_TIMESTAMP WHERE id=? AND revision=?",
            house.revision,key,house.revision-1);
        if(changed!=1)throw new PlacementException("다른 창에서 집이 바뀌었어요. 최신 배치를 다시 불러옵니다.");
        // At most 150 objects. Replace one layout atomically; a failure rolls back the entire transaction.
        jdbc.update("DELETE FROM placed_furniture WHERE house_id=?",key);
        var ordered=house.furniture.stream().sorted(Comparator.comparing(item -> item.parentId()!=null)).toList();
        for(var item:ordered) {
            jdbc.update("INSERT INTO placed_furniture(id,house_id,type_id,x,y,rotation,wall,parent_id,slot,sort_order) VALUES (?,?,?,?,?,?,?,?,?,?)",
                UUID.fromString(item.id()),key,item.typeId(),item.x(),item.y(),item.rotation(),item.wall(),
                item.parentId()==null?null:UUID.fromString(item.parentId()),item.slot(),house.furniture.indexOf(item));
        }
    }
    @Override @Transactional public void remove(String id) { jdbc.update("DELETE FROM houses WHERE id=?",UUID.fromString(id)); }
}
