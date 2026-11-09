package com.naranjip;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(properties="app.demo-auth=true")
@AutoConfigureMockMvc
class DemoFlowTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test void loginHouseReloadAndLogout() throws Exception {
        mvc.perform(get("/api/me")).andExpect(jsonPath("$.authenticated").value(false));
        var result = mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"loginId\":\"나란\",\"password\":\"anything\"}"))
                .andExpect(status().isOk()).andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);
        String id = json.readTree(result.getResponse().getContentAsString()).at("/user/houseId").asText();
        mvc.perform(get("/api/me").session(session)).andExpect(jsonPath("$.user.displayName").value("나란"));
        mvc.perform(get("/api/houses/" + id).session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.width").value(8)).andExpect(jsonPath("$.furniture").isEmpty());
        mvc.perform(get("/api/houses/other").session(session)).andExpect(status().isNotFound());
        mvc.perform(get("/api/houses/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/logout").session(session)).andExpect(status().isNoContent());
        mvc.perform(get("/api/me")).andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test void registrationAndValidation() throws Exception {
        mvc.perform(post("/api/auth/register").contentType("application/json").content("{\"loginId\":\"새친구\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.authenticated").value(true));
        mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"loginId\":\"  \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/rankings")).andExpect(jsonPath("$.houses").isEmpty());
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
        mvc.perform(get("/")).andExpect(status().isOk());
    }
}
