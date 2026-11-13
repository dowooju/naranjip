package com.naranjip.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import java.util.List;
import static com.naranjip.application.HouseState.*;

@Service
@Transactional
public class FurnitureService {
    private final HouseRepository repository;
    private final FurnitureCatalog catalog;
    public FurnitureService(HouseRepository repository, FurnitureCatalog catalog) {
        this.repository = repository; this.catalog = catalog;
    }
    public record Placement(String typeId, int x, int y, boolean rotation, String wall, String parentId, long revision) {}
    private void authorizeMember(String houseId,String accountId) {
        repository.find(houseId); // Lock the house until validation and mutation commit.
        if(!repository.isMember(houseId,accountId))throw new PlacementException("현재 소속된 집이 아니에요. 다시 불러와 주세요.");
    }
    public Snapshot readForMember(String houseId,String accountId) { authorizeMember(houseId,accountId);return read(houseId); }
    public Snapshot placeForMember(String houseId,String accountId,String furnitureId,Placement input) {
        authorizeMember(houseId,accountId);return place(houseId,furnitureId,input);
    }
    public Snapshot deleteForMember(String houseId,String accountId,String id,long revision,boolean cascade) {
        authorizeMember(houseId,accountId);return delete(houseId,id,revision,cascade);
    }
    public Snapshot read(String houseId) {
        return snapshot(repository.find(houseId));
    }
    public Snapshot place(String houseId, String furnitureId, Placement request) {
        HouseState house = repository.find(houseId);
        checkRevision(house, request.revision());
        PlacedFurniture old = furnitureId == null ? null : require(house, furnitureId);
        String typeId = old == null ? request.typeId() : old.typeId();
        FurnitureCatalog.Type type = catalog.require(typeId);
        if (old == null && house.furniture.size() >= 150) throw new PlacementException("한 집에는 최대 150개까지 놓을 수 있어요.");
        int slot = -1;
        int x = request.x(), y = request.y();
        String wall = null, parentId = null;
        if (type.kind().equals("attachment")) {
            PlacedFurniture parent = require(house, request.parentId());
            String expected = typeId.equals("frame") ? "desk" : "fridge";
            if (!parent.typeId().equals(expected)) throw new PlacementException("액자는 책상, 쪽지는 냉장고에만 붙일 수 있어요.");
            int limit = typeId.equals("frame") ? 2 : 3;
            for (int candidate = 0; candidate < limit; candidate++) {
                final int selected = candidate;
                boolean taken = house.furniture.stream().anyMatch(item -> item.parentId() != null
                    && item.parentId().equals(parent.id()) && item.slot() == selected && !item.id().equals(furnitureId));
                if (!taken) { slot = candidate; break; }
            }
            if (slot < 0) throw new PlacementException("붙일 자리가 없어요.");
            parentId = parent.id(); x = 0; y = 0;
        } else {
            int width = width(type, request.rotation()), height = height(type, request.rotation());
            if (x < 0 || y < 0 || x > 8 - width || y > 8 - height) throw new PlacementException("방 바깥에는 놓을 수 없어요.");
            if (type.kind().equals("wall")) {
                wall = request.wall();
                if (!("left".equals(wall) && x == 0 || "right".equals(wall) && y == 0)) {
                    throw new PlacementException("문은 벽에 붙여 놓아 주세요.");
                }
                if (request.rotation() != wall.equals("left")) throw new PlacementException("문 방향은 벽에 맞춰 자동으로 정해져요.");
            }
            for (PlacedFurniture item : house.furniture) {
                if (item.id().equals(furnitureId)) continue;
                FurnitureCatalog.Type other = catalog.require(item.typeId());
                if (other.kind().equals("attachment")) continue;
                if (type.kind().equals("wall") && other.kind().equals("wall")) {
                    int start = wall.equals("left") ? y : x;
                    int otherStart = wall.equals("left") ? item.y() : item.x();
                    if (wall.equals(item.wall()) && start < otherStart + other.width() && start + type.width() > otherStart) {
                        throw new PlacementException("이미 문이 있는 자리예요.");
                    }
                    continue;
                }
                if (type.kind().equals("wall") || other.kind().equals("wall")) continue;
                if (type.kind().equals("rug") != other.kind().equals("rug")) continue;
                if (x < item.x() + width(other, item.rotation()) && x + width > item.x()
                    && y < item.y() + height(other, item.rotation()) && y + height > item.y()) {
                    throw new PlacementException("다른 가구와 겹치는 자리예요.");
                }
            }
        }
        PlacedFurniture placed = new PlacedFurniture(old == null ? UUID.randomUUID().toString() : old.id(),
            typeId, x, y, type.kind().equals("attachment") ? false : request.rotation(), wall, parentId, slot);
        if (old != null) house.furniture.set(house.furniture.indexOf(old), placed);
        else house.furniture.add(placed);
        house.revision++;
        repository.save(house);
        return snapshot(house);
    }
    public Snapshot delete(String houseId, String id, long revision, boolean cascade) {
        HouseState house = repository.find(houseId);
        checkRevision(house, revision);
        require(house, id);
        if (!cascade && house.furniture.stream().anyMatch(item -> id.equals(item.parentId()))) {
            throw new PlacementException("부착된 액자와 쪽지도 함께 삭제됩니다. 확인이 필요해요.");
        }
        house.furniture.removeIf(item -> item.id().equals(id) || id.equals(item.parentId()));
        house.revision++;
        repository.save(house);
        return snapshot(house);
    }
    private void checkRevision(HouseState house, long revision) {
        if (revision != house.revision) throw new PlacementException("다른 창에서 집이 바뀌었어요. 최신 배치를 다시 불러옵니다.");
    }
    private PlacedFurniture require(HouseState house, String id) {
        return house.furniture.stream().filter(item -> item.id().equals(id)).findFirst()
            .orElseThrow(() -> new PlacementException("가구를 찾을 수 없어요."));
    }
    private Snapshot snapshot(HouseState house) { return new Snapshot(house.id, house.owner, 8, 8, house.revision, List.copyOf(house.furniture)); }
    public static int width(FurnitureCatalog.Type type, boolean rotation) { return !rotation ? type.width() : type.height(); }
    public static int height(FurnitureCatalog.Type type, boolean rotation) { return !rotation ? type.height() : type.width(); }
}
