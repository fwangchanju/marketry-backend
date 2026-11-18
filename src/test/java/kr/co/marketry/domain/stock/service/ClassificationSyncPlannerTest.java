package kr.co.marketry.domain.stock.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import kr.co.marketry.domain.stock.service.ClassificationSyncPlanner.DesiredCategory;
import kr.co.marketry.domain.stock.service.ClassificationSyncPlanner.LiveCategory;
import kr.co.marketry.domain.stock.service.ClassificationSyncPlanner.Plan;
import org.junit.jupiter.api.Test;

class ClassificationSyncPlannerTest {

    private static final List<LiveCategory> LIVE = List.of(
            new LiveCategory(1, null), new LiveCategory(2, 1L), new LiveCategory(3, 2L), new LiveCategory(4, null));

    @Test
    void 운영_업종을_가리키는_항목은_새로_만들지_않고_지우지도_않는다() {
        List<DesiredCategory> desired = List.of(
                new DesiredCategory(10, 1L, null, "금융"),
                new DesiredCategory(11, 2L, 10L, "은행"),
                new DesiredCategory(12, 3L, 11L, "지방은행"),
                new DesiredCategory(13, 4L, null, "반도체"));

        Plan plan = ClassificationSyncPlanner.plan(LIVE, desired);

        assertThat(plan.creations()).isEmpty();
        assertThat(plan.deletions()).isEmpty();
        assertThat(plan.temporaryRenames()).containsExactly(1L, 2L, 3L, 4L);
    }

    @Test
    void 가리키는_운영_업종이_없는_항목만_새로_만든다() {
        DesiredCategory created = new DesiredCategory(20, null, 10L, "신규");
        List<DesiredCategory> desired = List.of(
                new DesiredCategory(10, 1L, null, "금융"),
                new DesiredCategory(11, 2L, 10L, "은행"),
                new DesiredCategory(12, 3L, 11L, "지방은행"),
                new DesiredCategory(13, 4L, null, "반도체"),
                created);

        Plan plan = ClassificationSyncPlanner.plan(LIVE, desired);

        assertThat(plan.creations()).containsExactly(created);
        assertThat(plan.deletions()).isEmpty();
    }

    @Test
    void 이미_사라진_운영_업종을_가리키는_항목은_새로_만든다() {
        DesiredCategory stale = new DesiredCategory(30, 99L, null, "옛업종");

        Plan plan = ClassificationSyncPlanner.plan(LIVE, List.of(stale));

        assertThat(plan.creations()).containsExactly(stale);
    }

    @Test
    void 어느_항목도_가리키지_않는_운영_업종은_하위_업종이_먼저_오도록_깊은_순서로_지운다() {
        List<DesiredCategory> desired = List.of(new DesiredCategory(13, 4L, null, "반도체"));

        Plan plan = ClassificationSyncPlanner.plan(LIVE, desired);

        assertThat(plan.deletions()).containsExactly(3L, 2L, 1L);
    }

    @Test
    void 원하는_구성이_비어_있으면_운영_업종을_모두_지운다() {
        Plan plan = ClassificationSyncPlanner.plan(LIVE, List.of());

        assertThat(plan.creations()).isEmpty();
        assertThat(plan.deletions()).hasSize(4);
        assertThat(plan.deletions().get(0)).isEqualTo(3L);
    }
}
