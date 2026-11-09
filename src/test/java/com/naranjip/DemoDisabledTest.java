package com.naranjip;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties="app.demo-auth=false")
@AutoConfigureMockMvc
class DemoDisabledTest {
    @Autowired MockMvc mvc;
    @Test void demoLoginRequiresOptIn() throws Exception {
        mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"loginId\":\"guest\"}"))
                .andExpect(status().isServiceUnavailable());
    }
}
