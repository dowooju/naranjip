package com.naranjip;

import com.naranjip.application.*;
import com.naranjip.infrastructure.InMemoryHouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class FurnitureServiceTest {
    FurnitureService service;
    @BeforeEach void setup() {
        var repository = new InMemoryHouseRepository();
        repository.create("test", "tester");
        service = new FurnitureService(repository, new FurnitureCatalog());
    }
    FurnitureService.Placement input(String type, int x, int y, int rotation, String wall, String parent) {
        return new FurnitureService.Placement(type,x,y,rotation,wall,parent,service.read("test").revision());
    }
    HouseState.Snapshot add(String type,int x,int y) { return service.place("test",null,input(type,x,y,0,null,null)); }

    @Test void placementCollisionAndFailedMovePreserveSavedPosition() {
        var first = add("sofa",1,1).furniture().getFirst();
        assertThrows(PlacementException.class,()->add("chair",2,1));
        assertThrows(PlacementException.class,()->service.place("test",first.id(),input("sofa",7,7,0,null,null)));
        assertEquals(first,service.read("test").furniture().getFirst());
        assertThrows(PlacementException.class,()->add("chair",-1,0));
        assertThrows(PlacementException.class,()->add("sofa",Integer.MAX_VALUE,0));
        assertThrows(PlacementException.class,()->add("unknown",0,0));
        var moved=service.place("test",first.id(),input("sofa",5,2,90,null,null));
        assertEquals(90,moved.furniture().getFirst().rotation());
        assertThrows(PlacementException.class,()->add("chair",5,3));
    }
    @Test void rugsAllowFurnitureButNotOtherRugs() {
        add("rug",2,2); add("desk",2,2);
        assertEquals(2,service.read("test").furniture().size());
        assertThrows(PlacementException.class,()->add("rug",3,2));
    }
    @Test void doorsRequireWallAndCorrectOrientation() {
        assertThrows(PlacementException.class,()->add("door",2,2));
        service.place("test",null,input("door",0,3,90,"left",null));
        service.place("test",null,input("door",3,0,0,"right",null));
        assertThrows(PlacementException.class,()->service.place("test",null,input("door",0,3,90,"left",null)));
        assertThrows(PlacementException.class,()->service.place("test",null,input("door",0,1,0,"left",null)));
    }
    @Test void attachmentsHaveLimitsAndDeleteRequiresConfirmation() {
        String desk=add("desk",1,2).furniture().getFirst().id();
        service.place("test",null,input("frame",0,0,0,null,desk));
        service.place("test",null,input("frame",0,0,0,null,desk));
        assertThrows(PlacementException.class,()->service.place("test",null,input("frame",0,0,0,null,desk)));
        assertThrows(PlacementException.class,()->service.place("test",null,input("note",0,0,0,null,desk)));
        var moved=service.place("test",desk,input("desk",4,4,90,null,null));
        assertEquals(2,moved.furniture().stream().filter(item->desk.equals(item.parentId())).count());
        assertThrows(PlacementException.class,()->service.delete("test",desk,moved.revision(),false));
        assertEquals(0,service.delete("test",desk,moved.revision(),true).furniture().size());
        String fridge=add("fridge",0,0).furniture().getFirst().id();
        for(int i=0;i<3;i++)service.place("test",null,input("note",0,0,0,null,fridge));
        assertThrows(PlacementException.class,()->service.place("test",null,input("note",0,0,0,null,fridge)));
    }
    @Test void staleRevisionDoesNotOverwriteNewerChanges() {
        add("chair",0,0);
        assertThrows(PlacementException.class,()->service.place("test",null,new FurnitureService.Placement("chair",2,2,0,null,null,0)));
        assertEquals(1,service.read("test").furniture().size());
    }
    @Test void simultaneousPlacementCommitsOnlyOneRequest() throws Exception {
        var gate=new CountDownLatch(1); var successes=new AtomicInteger();
        try(var executor=Executors.newFixedThreadPool(2)) {
            Callable<Void> contender=()->{gate.await();try{service.place("test",null,new FurnitureService.Placement("chair",2,2,0,null,null,0));successes.incrementAndGet();}catch(PlacementException expected){}return null;};
            var first=executor.submit(contender);var second=executor.submit(contender);gate.countDown();
            first.get(5,TimeUnit.SECONDS);second.get(5,TimeUnit.SECONDS);
        }
        assertEquals(1,successes.get());assertEquals(1,service.read("test").furniture().size());
    }
}
