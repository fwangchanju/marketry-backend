package kr.co.marketry.domain.stock.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 원하는 업종 구성(초안 또는 백업)을 운영 업종에 맞추는 순서를 계산한다. DB를 쓰지 않는 순수 계산이다.
 *
 * <p>운영 업종 id는 사용자 설정(제외한 업종 id 등)에 저장돼 있어서, 원하는 구성이 가리키는 운영 업종(liveId)은 같은 행을 고쳐 쓴다. 가리키는 운영 업종이 없는 항목만 새로 만들고, 어느 항목도 가리키지 않는 운영 업종은 지운다.
 */
final class ClassificationSyncPlanner {

    record LiveCategory(long id, Long parentId) {}

    /** key는 원하는 구성 안에서 항목을 구분하는 값(초안 업종 id 등), parentKey는 부모 항목의 key다. */
    record DesiredCategory(long key, Long liveId, Long parentKey, String name) {}

    /**
     * temporaryRenames: 이름이 겹치는 순간이 생기지 않도록 먼저 임시 이름으로 바꿀 운영 업종(운영 업종 전부).
     * creations: 새로 만들 항목. deletions: 지울 운영 업종 id(하위 업종이 먼저 오도록 깊은 순서).
     */
    record Plan(List<Long> temporaryRenames, List<DesiredCategory> creations, List<Long> deletions) {}

    private ClassificationSyncPlanner() {}

    static Plan plan(List<LiveCategory> live, List<DesiredCategory> desired) {
        Map<Long, LiveCategory> liveById = new HashMap<>();
        for (LiveCategory category : live) {
            liveById.put(category.id(), category);
        }
        Set<Long> kept = new HashSet<>();
        List<DesiredCategory> creations = new ArrayList<>();
        for (DesiredCategory category : desired) {
            if (category.liveId() != null && liveById.containsKey(category.liveId())) {
                kept.add(category.liveId());
            } else {
                creations.add(category);
            }
        }
        List<Long> deletions = new ArrayList<>();
        for (LiveCategory category : live) {
            if (kept.contains(category.id())) {
                continue;
            }
            deletions.add(category.id());
        }
        deletions.sort(Comparator.comparingInt((Long id) -> depth(liveById, id)).reversed());
        List<Long> temporaryRenames = live.stream().map(LiveCategory::id).toList();
        return new Plan(temporaryRenames, creations, deletions);
    }

    private static int depth(Map<Long, LiveCategory> liveById, long id) {
        int depth = 0;
        for (Long parentId = liveById.get(id).parentId();
                parentId != null;
                parentId = liveById.get(parentId).parentId()) {
            depth++;
        }
        return depth;
    }
}
