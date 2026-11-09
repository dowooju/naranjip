package com.naranjip.application;

import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class FurnitureCatalog {
    public record Type(String id, String name, String kind, int width, int height,
                       int column, int row, int drawWidth, int drawHeight, String description) {}
    private final List<Type> types = List.of(
        new Type("chair", "하트 의자", "floor", 1, 1, 0, 0, 70, 96, "폭신한 분홍 의자 · 1×1"),
        new Type("desk", "리본 책상", "floor", 2, 1, 1, 0, 110, 100, "액자 2개를 올릴 수 있어요 · 2×1"),
        new Type("sofa", "러브 소파", "floor", 2, 1, 2, 0, 110, 110, "둘이 나란히 앉는 소파 · 2×1"),
        new Type("dining", "밀크 식탁", "floor", 2, 2, 3, 0, 145, 115, "함께하는 식탁 · 2×2"),
        new Type("fridge", "딸기 냉장고", "floor", 1, 1, 0, 1, 72, 130, "쪽지 3개를 붙일 수 있어요 · 1×1"),
        new Type("door", "설레는 문", "wall", 2, 1, 1, 1, 76, 150, "벽 두 칸의 가운데에 붙어요"),
        new Type("frame", "추억 액자", "attachment", 1, 1, 2, 1, 32, 40, "책상 위에 붙여요 · 사진 기능 준비 중"),
        new Type("note", "하트 쪽지", "attachment", 1, 1, 3, 1, 20, 25, "냉장고에 붙여요 · 내용 기능 준비 중"),
        new Type("plant-small", "초록 친구", "floor", 1, 1, 0, 2, 65, 90, "작은 화분 · 1×1"),
        new Type("plant-tall", "키다리 나무", "floor", 1, 1, 1, 2, 85, 145, "큰 화분 · 1×1"),
        new Type("rug", "레이스 러그", "rug", 3, 2, 2, 2, 165, 115, "가구 아래에 깔 수 있어요 · 3×2")
    );
    public List<Type> all() { return types; }
    public Type require(String id) {
        return types.stream().filter(type -> type.id().equals(id)).findFirst()
            .orElseThrow(() -> new PlacementException("알 수 없는 가구예요."));
    }
}
