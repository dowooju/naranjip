package com.naranjip;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.naranjip.application.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class HousePersistenceTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired FurnitureService furniture;
    @Autowired JdbcTemplate jdbc;
    record Account(String name, String house, MockHttpSession session) {}
    final String password = "PersistenceTest123!";
    Account register() throws Exception {
        String name="p"+UUID.randomUUID().toString().replace("-","").substring(0,12);
        var result=mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json")
            .content(json.writeValueAsString(Map.of("loginId",name,"email",name+"@example.com","password",password))))
            .andExpect(status().isCreated()).andReturn();
        return new Account(name,json.readTree(result.getResponse().getContentAsString()).at("/user/houseId").asText(),
            (MockHttpSession)result.getRequest().getSession(false));
    }
    HouseState.Snapshot add(String house,String type,int x,int y,boolean rotation,String wall,String parent) {
        return furniture.place(house,null,new FurnitureService.Placement(type,x,y,rotation,wall,parent,furniture.read(house).revision()));
    }
    @Test void loginRestoresBooleanDirectionsWallsAttachmentsAndOrder() throws Exception {
        var account=register(); String house=account.house();
        String desk=add(house,"desk",1,2,false,null,null).furniture().getLast().id();
        add(house,"frame",0,0,false,null,desk);
        add(house,"frame",0,0,false,null,desk);
        String fridge=add(house,"fridge",6,6,true,null,null).furniture().getLast().id();
        add(house,"note",0,0,false,null,fridge);
        add(house,"door",0,3,true,"left",null);
        add(house,"rug",2,2,false,null,null);
        var expected=furniture.place(house,desk,new FurnitureService.Placement("desk",3,2,true,null,null,furniture.read(house).revision()));
        assertEquals(7,jdbc.queryForObject("SELECT COUNT(*) FROM placed_furniture WHERE house_id=?",Integer.class,UUID.fromString(house)));
        assertTrue(jdbc.queryForObject("SELECT rotation FROM placed_furniture WHERE id=?",Boolean.class,UUID.fromString(desk)));
        mvc.perform(post("/api/auth/logout").session(account.session()).with(csrf())).andExpect(status().isNoContent());
        var login=mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
            .content(json.writeValueAsString(Map.of("loginId",account.name(),"password",password))))
            .andExpect(status().isOk()).andReturn();
        var session=(MockHttpSession)login.getRequest().getSession(false);
        var restored=mvc.perform(get("/api/houses/"+house).session(session)).andExpect(status().isOk()).andReturn();
        assertEquals(expected,json.readValue(restored.getResponse().getContentAsString(),HouseState.Snapshot.class));
        var other=register();
        mvc.perform(get("/api/houses/"+house).session(other.session())).andExpect(status().isNotFound());
        mvc.perform(delete("/api/houses/"+house+"/furniture/"+desk+"?revision="+expected.revision())
            .session(other.session()).with(csrf())).andExpect(status().isNotFound());
        for(String invalid:new String[]{"180","1","\"true\""}) {
            mvc.perform(post("/api/houses/"+house+"/furniture").session(session).with(csrf()).contentType("application/json")
                .content("{\"typeId\":\"chair\",\"x\":7,\"y\":7,\"rotation\":"+invalid+",\"revision\":"+expected.revision()+"}"))
                .andExpect(status().isBadRequest());
        }
        assertEquals(expected,furniture.read(house));
    }
    @Test void failedMoveAndUnconfirmedDeletePreserveDatabaseAndCascadeDeletesAttachments() throws Exception {
        String house=register().house();
        String desk=add(house,"desk",1,2,false,null,null).furniture().getLast().id();
        var before=add(house,"frame",0,0,false,null,desk);
        assertThrows(PlacementException.class,()->furniture.place(house,desk,new FurnitureService.Placement("desk",7,7,true,null,null,before.revision())));
        assertEquals(before,furniture.read(house));
        assertThrows(PlacementException.class,()->furniture.delete(house,desk,before.revision(),false));
        assertEquals(before,furniture.read(house));
        assertTrue(furniture.delete(house,desk,before.revision(),true).furniture().isEmpty());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM placed_furniture WHERE house_id=?",Integer.class,UUID.fromString(house)));
    }
    @Test void databaseTransactionsRejectOneOfTwoConcurrentWriters() throws Exception {
        String house=register().house();
        var gate=new CountDownLatch(1); var successes=new AtomicInteger(); var conflicts=new AtomicInteger();
        try(var executor=Executors.newFixedThreadPool(2)) {
            Callable<Void> contender=()->{
                gate.await();
                try {
                    furniture.place(house,null,new FurnitureService.Placement("chair",2,2,false,null,null,0));
                    successes.incrementAndGet();
                } catch(PlacementException expected) { conflicts.incrementAndGet(); }
                return null;
            };
            var first=executor.submit(contender); var second=executor.submit(contender); gate.countDown();
            first.get(10,TimeUnit.SECONDS); second.get(10,TimeUnit.SECONDS);
        }
        assertEquals(1,successes.get()); assertEquals(1,conflicts.get());
        assertEquals(1,furniture.read(house).revision());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM placed_furniture WHERE house_id=?",Integer.class,UUID.fromString(house)));
    }
}
