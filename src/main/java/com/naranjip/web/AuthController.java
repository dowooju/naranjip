package com.naranjip.web;
import com.naranjip.application.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.io.Serializable;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final AccountService accounts;
    private final HouseRepository houses;
    private final FurnitureService furniture;
    private final CoupleService couples;
    private final SocialService social;
    public AuthController(AccountService accounts, HouseRepository houses, FurnitureService furniture, CoupleService couples, SocialService social) {
        this.accounts=accounts; this.houses=houses; this.furniture=furniture; this.couples=couples;this.social=social;
    }
    public record LoginInput(
        @NotBlank(message="아이디를 입력해 주세요.") @Size(max=40,message="아이디가 너무 길어요.") String loginId,
        @NotBlank(message="비밀번호를 입력해 주세요.") @Size(max=72,message="비밀번호가 너무 길어요.") String password) {}
    public record RegisterInput(
        @NotBlank(message="아이디를 입력해 주세요.") @Pattern(regexp="[a-zA-Z0-9_가-힣]{3,20}",message="아이디는 한글·영문·숫자·밑줄 3~20자로 입력해 주세요.") String loginId,
        @NotBlank(message="이메일을 입력해 주세요.") @Email(message="올바른 이메일 주소를 입력해 주세요.") @Size(max=254,message="이메일이 너무 길어요.") String email,
        @NotBlank(message="비밀번호를 입력해 주세요.") @Size(min=8,max=72,message="비밀번호는 8자 이상, 72바이트 이내로 입력해 주세요.") String password) {}
    public record Resident(String displayName, String houseId) implements Serializable {}
    public record User(String accountId,String displayName,String houseId,boolean canEdit,String partnerName,boolean admin) {}
    public record Me(boolean authenticated, User user) {}
    public String accountId(HttpServletRequest request) {
        String id=optionalAccountId(request);
        if(id==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return id;
    }
    public String optionalAccountId(HttpServletRequest request) {
        HttpSession session=request.getSession(false);
        Resident resident=session==null?null:(Resident)session.getAttribute("resident");
        return resident==null?null:resident.houseId();
    }

    @GetMapping("/csrf") public Map<String,String> csrf(CsrfToken token) {
        return Map.of("token",token.getToken(),"headerName",token.getHeaderName());
    }
    @GetMapping("/me") public Me me(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Resident resident = session == null ? null : (Resident)session.getAttribute("resident");
        if(resident==null)return new Me(false,null);
        var status=couples.status(resident.houseId());
        return new Me(true,new User(resident.houseId(),resident.displayName(),status.houseId(),status.canEdit(),status.partner()==null?null:status.partner().displayName(),social.isAdmin(resident.houseId())));
    }
    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Me register(@Valid @RequestBody RegisterInput input, HttpServletRequest request) {
        return establish(accounts.register(input.loginId(),input.email(),input.password()),request);
    }
    @PostMapping("/auth/login")
    public Me login(@Valid @RequestBody LoginInput input, HttpServletRequest request) {
        return establish(accounts.login(input.loginId(),input.password()),request);
    }
    private Me establish(AccountRepository.Account account,HttpServletRequest request) {
        HttpSession previous=request.getSession(false);
        if(previous!=null)previous.invalidate();
        Resident resident=new Resident(account.loginId(),account.id().toString());
        request.getSession(true).setAttribute("resident",resident);
        return me(request);
    }
    @PostMapping("/auth/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        HttpSession session=request.getSession(false);
        if(session!=null)session.invalidate();
    }
    @GetMapping("/houses/{id}") public HouseState.Snapshot house(@PathVariable String id,HttpServletRequest request) {
        User user=me(request).user();
        if(user==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if(!id.equals(user.houseId()))throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return furniture.readForMember(id,user.accountId());
    }
}
