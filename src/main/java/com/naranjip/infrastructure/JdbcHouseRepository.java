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
        var existing=jdbc.queryForList("SELECT house_id FROM house_members WHERE account_id=?",UUID.class,key);
        if(!existing.isEmpty())return find(existing.getFirst().toString());
        jdbc.update("INSERT INTO houses(id) VALUES (?)",key);
        jdbc.update("INSERT INTO house_members(account_id,house_id) VALUES (?,?)",key,key);
        return find(id);
    }

    @Override @Transactional public HouseState find(String id) {
        UUID key = UUID.fromString(id);
        // Caller holds a transaction throughout validation + save. Locks work across app instances.
        var rows = jdbc.query("SELECT id, revision FROM houses WHERE id=? FOR UPDATE",
            (rs, index) -> {
                String owner = String.join(" ♡ ",jdbc.queryForList("SELECT a.login_id FROM house_members m JOIN accounts a ON a.id=m.account_id WHERE m.house_id=? ORDER BY m.joined_at,a.id",String.class,key));
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
        // Retain furniture identities so attached content survives moves and rotations.
        var retained=house.furniture.stream().map(item->UUID.fromString(item.id())).collect(java.util.stream.Collectors.toSet());
        for(UUID old:jdbc.queryForList("SELECT id FROM placed_furniture WHERE house_id=?",UUID.class,key)) {
            if(!retained.contains(old))jdbc.update("DELETE FROM placed_furniture WHERE id=? AND house_id=?",old,key);
        }
        var ordered=house.furniture.stream().sorted(Comparator.comparing(item -> item.parentId()!=null)).toList();
        for(var item:ordered) {
            int updated=jdbc.update("UPDATE placed_furniture SET x=?,y=?,rotation=?,wall=?,parent_id=?,slot=?,sort_order=? WHERE id=? AND house_id=?",
                item.x(),item.y(),item.rotation(),item.wall(),item.parentId()==null?null:UUID.fromString(item.parentId()),item.slot(),house.furniture.indexOf(item),UUID.fromString(item.id()),key);
            if(updated==0)jdbc.update("INSERT INTO placed_furniture(id,house_id,type_id,x,y,rotation,wall,parent_id,slot,sort_order) VALUES (?,?,?,?,?,?,?,?,?,?)",
                UUID.fromString(item.id()),key,item.typeId(),item.x(),item.y(),item.rotation(),item.wall(),
                item.parentId()==null?null:UUID.fromString(item.parentId()),item.slot(),house.furniture.indexOf(item));
        }
    }
    @Override @Transactional public void remove(String id) { jdbc.update("DELETE FROM houses WHERE id=?",UUID.fromString(id)); }
    @Override public boolean isMember(String houseId,String accountId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM house_members WHERE house_id=? AND account_id=?",Integer.class,UUID.fromString(houseId),UUID.fromString(accountId))>0;
    }
}
