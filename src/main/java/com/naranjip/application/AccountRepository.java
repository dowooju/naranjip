package com.naranjip.application;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository {
    record Account(UUID id, String loginId, String passwordHash) {}
    Optional<Account> findByLoginId(String loginId);
    void insert(Account account, String emailCiphertext, String emailLookupHash);
}
