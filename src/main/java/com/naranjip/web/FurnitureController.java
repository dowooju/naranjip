package com.naranjip.web;

import com.naranjip.application.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class FurnitureController {
    private final AuthController auth;
    private final LiveEditing live;
    private final FurnitureService service;
    private final FurnitureCatalog catalog;
    public FurnitureController(AuthController auth, FurnitureService service, FurnitureCatalog catalog, LiveEditing live) {
        this.live=live; this.auth = auth; this.service = service; this.catalog = catalog;
    }
    @GetMapping("/catalog") public Map<String, Object> catalog() {
        return Map.of("items", catalog.all(), "atlasUrl", "/furniture-atlas.png", "columns", 4, "rows", 3);
    }
    @PostMapping("/houses/{houseId}/furniture")
    public HouseState.Snapshot add(@PathVariable String houseId, @RequestBody FurnitureService.Placement placement, HttpServletRequest request) {
        authorize(houseId, request); return save(houseId,null,placement,request);
    }
    @PutMapping("/houses/{houseId}/furniture/{id}")
    public HouseState.Snapshot move(@PathVariable String houseId, @PathVariable String id, @RequestBody FurnitureService.Placement placement, HttpServletRequest request) {
        authorize(houseId, request); return save(houseId,id,placement,request);
    }
    @DeleteMapping("/houses/{houseId}/furniture/{id}")
    public HouseState.Snapshot delete(@PathVariable String houseId, @PathVariable String id, @RequestParam long revision,
            @RequestParam(defaultValue="false") boolean cascade, HttpServletRequest request) {
        authorize(houseId, request);
        String actor=auth.accountId(request),client=request.getHeader("X-Live-Client");
        return live.mutate(houseId,actor,client,id,()->service.deleteForMember(houseId,actor,id,live.revision(houseId,actor,client,revision),cascade));
    }
    private HouseState.Snapshot save(String house,String id,FurnitureService.Placement p,HttpServletRequest request) {
        String actor=auth.accountId(request),client=request.getHeader("X-Live-Client");
        return live.mutate(house,actor,client,id==null?p.parentId():id,()->{
            live.available(house,actor,client,p.parentId());
            return service.placeForMember(house,actor,id,
                new FurnitureService.Placement(p.typeId(),p.x(),p.y(),p.rotation(),p.wall(),p.parentId(),live.revision(house,actor,client,p.revision())));
        });
    }
    private void authorize(String houseId, HttpServletRequest request) {
        AuthController.User user = auth.me(request).user();
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if (!houseId.equals(user.houseId())) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (!user.canEdit()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
}
