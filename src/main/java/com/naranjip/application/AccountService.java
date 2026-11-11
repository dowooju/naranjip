package com.naranjip.application;
import com.naranjip.infrastructure.EmailCrypto;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class AccountService {
    private final AccountRepository accounts;
    private final EmailCrypto emailCrypto;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(12);
    private final String dummyHash = passwords.encode(UUID.randomUUID().toString());
    public AccountService(AccountRepository accounts, EmailCrypto emailCrypto) {
        this.accounts = accounts; this.emailCrypto = emailCrypto;
    }
    public AccountRepository.Account register(String loginId, String email, String password) {
        validatePassword(password, true);
        String normalizedEmail = email.strip().toLowerCase(Locale.ROOT);
        var account = new AccountRepository.Account(UUID.randomUUID(), normalize(loginId), passwords.encode(password));
        try { accounts.insert(account, emailCrypto.encrypt(normalizedEmail), emailCrypto.lookup(normalizedEmail)); }
        catch (DuplicateKeyException e) { throw new AuthException(409, "이미 사용 중인 아이디 또는 이메일이에요.", Map.of()); }
        return account;
    }
    public AccountRepository.Account login(String loginId, String password) {
        validatePassword(password, false);
        var found = accounts.findByLoginId(normalize(loginId));
        boolean matches = passwords.matches(password, found.map(AccountRepository.Account::passwordHash).orElse(dummyHash));
        if (!matches || found.isEmpty()) throw new AuthException(401, "아이디 또는 비밀번호가 올바르지 않아요.", Map.of());
        return found.get();
    }
    /** 규칙을 검사한 뒤 저장용 해시를 만든다. */
    public String hashPassword(String password) { validatePassword(password, true); return passwords.encode(password); }
    public boolean matches(String password, String hash) { return passwords.matches(password, hash); }
    private void validatePassword(String password, boolean register) {
        if (password.indexOf('\0') >= 0 || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new AuthException(400, "비밀번호를 확인해 주세요.", Map.of("password", "비밀번호는 UTF-8 기준 72바이트 이내로 입력해 주세요."));
        if (register && password.length() < 8)
            throw new AuthException(400, "비밀번호를 확인해 주세요.", Map.of("password", "비밀번호는 8자 이상 입력해 주세요."));
    }
    private String normalize(String value) { return value.strip().toLowerCase(Locale.ROOT); }
}
