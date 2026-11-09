package com.naranjip.infrastructure;
import com.naranjip.application.AccountRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcAccountRepository implements AccountRepository {
    private final JdbcTemplate jdbc;
    public JdbcAccountRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Optional<Account> findByLoginId(String loginId) {
        return jdbc.query("SELECT id, login_id, password_hash FROM accounts WHERE login_id = ?",
            (rs, row) -> new Account(rs.getObject("id", UUID.class), rs.getString("login_id"), rs.getString("password_hash")), loginId)
            .stream().findFirst();
    }
    public void insert(Account account, String encryptedEmail, String emailLookupHash) {
        jdbc.update("INSERT INTO accounts(id, login_id, password_hash, email_ciphertext, email_lookup_hash) VALUES (?, ?, ?, ?, ?)",
            account.id(), account.loginId(), account.passwordHash(), encryptedEmail, emailLookupHash);
    }
}
