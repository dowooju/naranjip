package com.naranjip.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/** 앱 하나 안에서만 공유하는 저장소. 테스트(app.live-store=memory)에서 Redis 없이 같은 규칙을 검증한다. */
@Component
@ConditionalOnProperty(name="app.live-store", havingValue="memory")
public class MemoryLiveStore implements LiveStore {
    private final Map<String, LiveEditing.Ticket> tickets = new HashMap<>();
    private final Map<String, Lease> leases = new HashMap<>();
    private final Map<String, Member> members = new HashMap<>();
    private final List<Delivery> deliveries = new CopyOnWriteArrayList<>();

    private static String key(String house, String id) { return house + ":" + id; }
    private static long now() { return System.currentTimeMillis(); }

    @Override public synchronized void putTicket(String id, LiveEditing.Ticket ticket) {
        tickets.values().removeIf(t -> t.expires() < now());
        if (tickets.size() > 1000) throw new com.naranjip.application.PlacementException("잠시 후 다시 연결해 주세요.");
        tickets.put(id, ticket);
    }
    @Override public synchronized LiveEditing.Ticket takeTicket(String id) {
        var t = tickets.remove(id);
        return t == null || t.expires() < now() ? null : t;
    }
    @Override public synchronized boolean acquire(Lease lease) {
        var current = leases.get(key(lease.house(), lease.root()));
        if (current != null && !current.client().equals(lease.client()) && current.expires() > now()) return false;
        leases.put(key(lease.house(), lease.root()), lease);
        return true;
    }
    @Override public synchronized void release(Lease lease) {
        leases.computeIfPresent(key(lease.house(), lease.root()), (k, l) -> l.client().equals(lease.client()) ? null : l);
    }
    @Override public synchronized Lease lease(String house, String root) {
        var l = leases.get(key(house, root));
        return l == null || l.expires() <= now() ? null : l;
    }
    @Override public synchronized List<Lease> leases(String house) {
        return leases.values().stream().filter(l -> l.house().equals(house) && l.expires() > now()).toList();
    }
    @Override public synchronized void join(Member member) { members.put(key(member.house(), member.client()), member); }
    @Override public synchronized void leave(String house, String client) { members.remove(key(house, client)); }
    @Override public synchronized Member member(String house, String client) {
        var m = members.get(key(house, client));
        return m == null || m.expires() <= now() ? null : m;
    }
    @Override public synchronized List<Member> members(String house) {
        return members.values().stream().filter(m -> m.house().equals(house) && m.expires() > now()).toList();
    }
    @Override public synchronized boolean purge(String house, long now) {
        boolean a = leases.values().removeIf(l -> l.house().equals(house) && l.expires() <= now);
        boolean b = members.values().removeIf(m -> m.house().equals(house) && m.expires() <= now);
        return a || b;
    }
    @Override public void publish(String house, String payload, String excluded) {
        for (var d : deliveries) d.deliver(house, payload, excluded);
    }
    @Override public void listen(Delivery delivery) { deliveries.add(delivery); }
}
