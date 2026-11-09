package com.naranjip.web;

import com.naranjip.application.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class FurnitureController {
    private final DemoController auth;
    private final FurnitureService service;
    private final FurnitureCatalog catalog;
    private final AssetStorage assets;
    public FurnitureController(DemoController auth, FurnitureService service, FurnitureCatalog catalog, AssetStorage assets) {
        this.auth = auth; this.service = service; this.catalog = catalog; this.assets = assets;
    }
    @GetMapping("/catalog") public Map<String, Object> catalog() {
        return Map.of("items", catalog.all(), "atlasUrl", "/api/assets/furniture-atlas.png", "columns", 4, "rows", 3);
    }
    @GetMapping(value="/assets/furniture-atlas.png", produces=MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<InputStreamResource> atlas() throws IOException {
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(new InputStreamResource(assets.open("furniture-atlas.png")));
    }
    @PostMapping("/houses/{houseId}/furniture")
    public HouseState.Snapshot add(@PathVariable String houseId, @RequestBody FurnitureService.Placement placement, HttpServletRequest request) {
        authorize(houseId, request); return service.place(houseId, null, placement);
    }
    @PutMapping("/houses/{houseId}/furniture/{id}")
    public HouseState.Snapshot move(@PathVariable String houseId, @PathVariable String id, @RequestBody FurnitureService.Placement placement, HttpServletRequest request) {
        authorize(houseId, request); return service.place(houseId, id, placement);
    }
    @DeleteMapping("/houses/{houseId}/furniture/{id}")
    public HouseState.Snapshot delete(@PathVariable String houseId, @PathVariable String id, @RequestParam long revision,
            @RequestParam(defaultValue="false") boolean cascade, HttpServletRequest request) {
        authorize(houseId, request); return service.delete(houseId, id, revision, cascade);
    }
    private void authorize(String houseId, HttpServletRequest request) {
        DemoController.Resident user = auth.me(request).user();
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if (!user.houseId().equals(houseId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
