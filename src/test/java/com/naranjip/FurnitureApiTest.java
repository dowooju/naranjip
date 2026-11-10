package com.naranjip;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@SpringBootTest
@AutoConfigureMockMvc
class FurnitureApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Test void catalogAndOwnershipAndPersistence() throws Exception {
        mvc.perform(get("/api/catalog")).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(11));
        var login=mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content("{\"loginId\":\"decorator\",\"email\":\"decorator@example.com\",\"password\":\"TestPassword123!\"}")).andExpect(status().isCreated()).andReturn();
        var session=(MockHttpSession)login.getRequest().getSession(false);
        String id=json.readTree(login.getResponse().getContentAsString()).at("/user/houseId").asText();
        String route="/api/houses/"+id+"/furniture";
        String payload="{\"typeId\":\"chair\",\"x\":2,\"y\":2,\"rotation\":false,\"revision\":0}";
        mvc.perform(post(route).with(csrf()).contentType("application/json").content(payload)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/houses/someone-else/furniture").with(csrf()).session(session).contentType("application/json").content(payload)).andExpect(status().isNotFound());
        var placed=mvc.perform(post(route).with(csrf()).session(session).contentType("application/json").content(payload))
            .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(1)).andReturn();
        mvc.perform(get("/api/houses/"+id).session(session)).andExpect(jsonPath("$.furniture[0].typeId").value("chair"));
        mvc.perform(post(route).with(csrf()).session(session).contentType("application/json").content(payload)).andExpect(status().isConflict());
        String furnitureId=json.readTree(placed.getResponse().getContentAsString()).at("/furniture/0/id").asText();
        mvc.perform(delete(route+"/"+furnitureId+"?revision=1").with(csrf()).session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.furniture").isEmpty());
    }
}
