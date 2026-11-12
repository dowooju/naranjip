package com.naranjip.web;

import java.util.List;

/**
 * 실시간 편집에서 앱 컨테이너끼리 함께 봐야 하는 상태.
 * 운영(Docker Compose)은 Redis에 두어 앱이 여러 개여도 같은 잠금·접속자·이벤트를 보고({@link RedisLiveStore}),
 * 테스트는 같은 규칙을 메모리로 흉내 낸다({@link MemoryLiveStore}). WebSocket 연결 자체는 각 앱이 들고 있다.
 */
public interface LiveStore {
    /** 접속권: HTTP로 발급한 앱과 WebSocket을 받는 앱이 다를 수 있다. */
    void putTicket(String id, LiveEditing.Ticket ticket);
    /** 한 번만 쓸 수 있도록 꺼내면서 지운다. 없거나 만료면 null. */
    LiveEditing.Ticket takeTicket(String id);

    /** 가구 묶음(root) 하나의 편집 잠금. client가 연결된 앱이 주기적으로 갱신한다. */
    record Lease(String client, String house, String root, String item, long expires) {}
    /** 비어 있거나, 만료됐거나, 이미 내 것이면 저장하고 true. 다른 사람 것이면 false. 원자적이어야 한다. */
    boolean acquire(Lease lease);
    /** 아직 내 것일 때만 지운다. */
    void release(Lease lease);
    /** 만료되지 않은 잠금. 없으면 null. */
    Lease lease(String house, String root);
    List<Lease> leases(String house);

    /** 집에 접속한 사람(다른 앱에 연결된 사람 포함). */
    record Member(String client, String house, String actor, String name, long expires) {}
    void join(Member member);
    void leave(String house, String client);
    Member member(String house, String client);
    List<Member> members(String house);
    /** 갱신이 끊긴(앱이 죽은) 잠금·접속자를 지운다. 지운 것이 있으면 true. */
    boolean purge(String house, long now);

    /** 집의 모든 접속자에게 보낼 메시지. 모든 앱(자기 자신 포함)의 {@link Delivery}로 전달된다. */
    void publish(String house, String payload, String excluded);
    interface Delivery { void deliver(String house, String payload, String excluded); }
    void listen(Delivery delivery);
}
