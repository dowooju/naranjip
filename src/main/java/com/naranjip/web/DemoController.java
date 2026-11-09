package com.naranjip.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.io.Serializable;
import java.util.Map;
import java.util.UUID;
import com.naranjip.application.HouseRepository;
import com.naranjip.application.FurnitureService;
import com.naranjip.application.HouseState;

/** Temporary session-only prototype. No account database, password storage, or real authentication. */
@RestController
@RequestMapping("/api")
public class DemoController {
    private final boolean demoAuth;
    private final HouseRepository houses;
    private final FurnitureService furniture;
    public DemoController(@Value("${app.demo-auth:false}") boolean demoAuth, HouseRepository houses, FurnitureService furniture) {
        this.demoAuth = demoAuth; this.houses = houses; this.furniture = furniture;
    }

    public record LoginInput(@NotBlank @Size(max=40) String loginId) {}
    public record Resident(String displayName, String houseId) implements Serializable {}
    public record Me(boolean authenticated, boolean demoMode, Resident user) {}

    @GetMapping("/me")
    public Me me(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Resident resident = session == null ? null : (Resident) session.getAttribute("resident");
        return new Me(resident != null, demoAuth, resident);
    }

    @PostMapping({"/auth/login", "/auth/register"})
    public Me login(@Valid @RequestBody LoginInput input, HttpServletRequest request) {
        if (!demoAuth) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Demo login disabled");
        HttpSession previous = request.getSession(false);
        if (previous != null) {
            Resident old = (Resident) previous.getAttribute("resident");
            if (old != null) houses.remove(old.houseId());
            previous.invalidate();
        }
        Resident resident = new Resident(input.loginId().strip(), UUID.randomUUID().toString());
        houses.create(resident.houseId(), resident.displayName());
        request.getSession(true).setAttribute("resident", resident);
        return new Me(true, true, resident);
    }

    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            Resident old = (Resident) session.getAttribute("resident");
            if (old != null) houses.remove(old.houseId());
            session.invalidate();
        }
    }

    @GetMapping("/houses/{id}")
    public HouseState.Snapshot house(@PathVariable String id, HttpServletRequest request) {
        Resident resident = me(request).user();
        if (resident == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if (!resident.houseId().equals(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return furniture.read(id);
    }

    @GetMapping("/rankings")
    public Map<String, Object> rankings() {
        return Map.of("status", "coming-soon", "houses", java.util.List.of());
    }
}
