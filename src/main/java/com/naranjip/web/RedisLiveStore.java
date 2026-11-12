package com.naranjip.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * 앱 컨테이너 여러 개가 실시간 편집 상태를 Redis로 공유한다(기본값).
 *
 * <pre>
 * live:ticket:{id}       문자열, 30초 뒤 자동 삭제   WebSocket 접속권(1회용)
 * live:leases:{집}       해시 root → 잠금 JSON       누가 어떤 가구를 편집 중인지
 * live:members:{집}      해시 client → 접속자 JSON   누가 이 집에 접속해 있는지
 * live:events            Pub/Sub 채널                 모든 앱에 보내는 브로드캐스트
 * </pre>
 * 잠금은 Lua 스크립트로 "확인 후 저장"을 한 번에 실행하므로, 두 앱이 동시에 같은 가구를 잡아도 한 명만 성공한다.
 */
@Component
@ConditionalOnProperty(name="app.live-store", havingValue="redis", matchIfMissing=true)
public class RedisLiveStore implements LiveStore {
    private static final String EVENTS = "live:events";
    /** 비어 있거나 만료됐거나 내 것이면 덮어쓴다. ARGV: root, client, 잠금 JSON, 현재 시각 */
    private static final DefaultRedisScript<Long> ACQUIRE = new DefaultRedisScript<>("""
        local v = redis.call('HGET', KEYS[1], ARGV[1])
        if v then
          local cur = cjson.decode(v)
          if cur.client ~= ARGV[2] and tonumber(cur.expires) > tonumber(ARGV[4]) then return 0 end
        end
        redis.call('HSET', KEYS[1], ARGV[1], ARGV[3])
        return 1""", Long.class);
    /** 아직 내 것일 때만 지운다. ARGV: root, client */
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>("""
        local v = redis.call('HGET', KEYS[1], ARGV[1])
        if v and cjson.decode(v).client == ARGV[2] then return redis.call('HDEL', KEYS[1], ARGV[1]) end
        return 0""", Long.class);
    /** 만료된 항목만 지운다. ARGV: 현재 시각 */
    private static final DefaultRedisScript<Long> PURGE = new DefaultRedisScript<>("""
        local all = redis.call('HGETALL', KEYS[1])
        local removed = 0
        for i = 1, #all, 2 do
          if tonumber(cjson.decode(all[i + 1]).expires) <= tonumber(ARGV[1]) then
            redis.call('HDEL', KEYS[1], all[i]); removed = removed + 1
          end
        end
        return removed""", Long.class);

    record TicketData(String house, String actor, String name, String sessionId, long expires) {}
    record Event(String house, String payload, String excluded) {}

    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final RedisMessageListenerContainer listener = new RedisMessageListenerContainer();

    public RedisLiveStore(StringRedisTemplate redis, RedisConnectionFactory connections, ObjectMapper json) {
        this.redis = redis; this.json = json;
        listener.setConnectionFactory(connections);
        listener.afterPropertiesSet();
    }

    private static String leaseKey(String house) { return "live:leases:" + house; }
    private static String memberKey(String house) { return "live:members:" + house; }
    private static long now() { return System.currentTimeMillis(); }
    private String write(Object value) {
        try { return json.writeValueAsString(value); } catch (JsonProcessingException e) { throw new IllegalStateException(e); }
    }
    private <T> T read(Object value, Class<T> type) {
        try { return value == null ? null : json.readValue(value.toString(), type); } catch (JsonProcessingException e) { return null; }
    }

    @Override public void putTicket(String id, LiveEditing.Ticket t) {
        var data = new TicketData(t.house(), t.actor(), t.name(), t.sessionId(), t.expires());
        redis.opsForValue().set("live:ticket:" + id, write(data), Duration.ofMillis(Math.max(1, t.expires() - now())));
    }
    @Override public LiveEditing.Ticket takeTicket(String id) {
        var d = read(redis.opsForValue().getAndDelete("live:ticket:" + id), TicketData.class);
        if (d == null || d.expires() < now()) return null;
        return new LiveEditing.Ticket(d.house(), d.actor(), d.name(), d.sessionId(), d.expires(), null);
    }

    @Override public boolean acquire(Lease lease) {
        Long ok = redis.execute(ACQUIRE, List.of(leaseKey(lease.house())), lease.root(), lease.client(), write(lease), String.valueOf(now()));
        return ok != null && ok == 1;
    }
    @Override public void release(Lease lease) {
        redis.execute(RELEASE, List.of(leaseKey(lease.house())), lease.root(), lease.client());
    }
    @Override public Lease lease(String house, String root) {
        var l = read(redis.opsForHash().get(leaseKey(house), root), Lease.class);
        return l == null || l.expires() <= now() ? null : l;
    }
    @Override public List<Lease> leases(String house) {
        long now = now();
        return redis.opsForHash().values(leaseKey(house)).stream().map(v -> read(v, Lease.class))
            .filter(l -> l != null && l.expires() > now).toList();
    }

    @Override public void join(Member m) { redis.opsForHash().put(memberKey(m.house()), m.client(), write(m)); }
    @Override public void leave(String house, String client) { redis.opsForHash().delete(memberKey(house), client); }
    @Override public Member member(String house, String client) {
        var m = read(redis.opsForHash().get(memberKey(house), client), Member.class);
        return m == null || m.expires() <= now() ? null : m;
    }
    @Override public List<Member> members(String house) {
        long now = now();
        return redis.opsForHash().values(memberKey(house)).stream().map(v -> read(v, Member.class))
            .filter(m -> m != null && m.expires() > now).toList();
    }
    @Override public boolean purge(String house, long now) {
        Long a = redis.execute(PURGE, List.of(leaseKey(house)), String.valueOf(now));
        Long b = redis.execute(PURGE, List.of(memberKey(house)), String.valueOf(now));
        return (a != null && a > 0) || (b != null && b > 0);
    }

    @Override public void publish(String house, String payload, String excluded) {
        redis.convertAndSend(EVENTS, write(new Event(house, payload, excluded)));
    }
    @Override public void listen(Delivery delivery) {
        listener.addMessageListener((message, pattern) -> {
            var e = read(new String(message.getBody(), StandardCharsets.UTF_8), Event.class);
            if (e != null) delivery.deliver(e.house(), e.payload(), e.excluded());
        }, new ChannelTopic(EVENTS));
        if (!listener.isRunning()) listener.start();
    }
    @PreDestroy void stop() throws Exception { listener.destroy(); }
}
