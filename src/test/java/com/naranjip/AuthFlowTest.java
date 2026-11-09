package com.naranjip;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.naranjip.infrastructure.EmailCrypto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired EmailCrypto crypto;
    String name() { return "u" + UUID.randomUUID().toString().replace("-","").substring(0,12); }
    String body(String id,String email,String password) throws Exception {
        return json.writeValueAsString(Map.of("loginId",id,"email",email,"password",password));
    }
    @Test void registrationStoresProtectedCredentialsAndLoginSurvivesLogout() throws Exception {
        String id=name(), email=id+"@example.com", password="MyStrongPass123!";
        var response=mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(body(id,email,password)))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.user.displayName").value(id)).andReturn();
        String text=response.getResponse().getContentAsString();
        assertFalse(text.contains(password)); assertFalse(text.contains(email)); assertFalse(text.contains("passwordHash"));
        var row=jdbc.queryForMap("SELECT * FROM accounts WHERE login_id=?",id);
        String hash=(String)row.get("password_hash");
        assertNotEquals(password,hash); assertTrue(hash.startsWith("$2a$12$"));
        assertTrue(new BCryptPasswordEncoder().matches(password,hash));
        assertFalse(((String)row.get("email_ciphertext")).contains(email));
        assertEquals(crypto.lookup(email),row.get("email_lookup_hash"));
        assertNotEquals(crypto.encrypt(email),crypto.encrypt(email));
        var session=(MockHttpSession)response.getRequest().getSession(false);
        String house=json.readTree(text).at("/user/houseId").asText();
        mvc.perform(get("/api/houses/"+house).session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/houses/other").session(session)).andExpect(status().isNotFound());
        mvc.perform(get("/api/houses/"+house)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/me")).andExpect(jsonPath("$.authenticated").value(false));
        var login=mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content(body(id.toUpperCase(),email,password)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.user.houseId").value(house)).andReturn();
        assertNotEquals(session.getId(),login.getRequest().getSession(false).getId());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content(body(id,email,"bad-password")))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("아이디 또는 비밀번호가 올바르지 않아요."));
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content(body(name(),email,password)))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("아이디 또는 비밀번호가 올바르지 않아요."));
    }
    @Test void requiredFieldsAndDuplicateAccountsAreRejected() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.loginId").exists()).andExpect(jsonPath("$.fields.password").exists());
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(body("ab","broken","short")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.email").exists()).andExpect(jsonPath("$.fields.password").exists());
        String id=name(),email=id+"@example.com",password="Password123!";
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(body(id,email,password))).andExpect(status().isCreated());
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(body(id.toUpperCase(),name()+"@example.com",password)))
            .andExpect(status().isConflict());
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(body(name(),email.toUpperCase(),password)))
            .andExpect(status().isConflict());
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(body(name(),"valid@example.com","한".repeat(25))))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.password").exists());
    }
    @Test void csrfIsRequiredAndAnonymousTokenCanBeObtained() throws Exception {
        mvc.perform(post("/api/auth/login").contentType("application/json").content(body("anything","a@b.com","anything")))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/csrf")).andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
        mvc.perform(get("/")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }
}
