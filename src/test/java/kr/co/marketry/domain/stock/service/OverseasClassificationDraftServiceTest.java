package kr.co.marketry.domain.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import kr.co.marketry.common.exception.BadRequestException;
import kr.co.marketry.domain.stock.entity.OverseasClassificationBackup;
import kr.co.marketry.domain.stock.entity.OverseasClassificationCategory;
import kr.co.marketry.domain.stock.entity.OverseasClassificationCategoryDraft;
import kr.co.marketry.domain.stock.entity.OverseasStockClassification;
import kr.co.marketry.domain.stock.entity.OverseasStockClassificationDraft;
import kr.co.marketry.domain.stock.entity.OverseasStockClassificationId;
import kr.co.marketry.domain.stock.repository.OverseasClassificationBackupRepository;
import kr.co.marketry.domain.stock.repository.OverseasClassificationCategoryDraftRepository;
import kr.co.marketry.domain.stock.repository.OverseasClassificationCategoryRepository;
import kr.co.marketry.domain.stock.repository.OverseasStockClassificationDraftRepository;
import kr.co.marketry.domain.stock.repository.OverseasStockClassificationRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OverseasClassificationDraftServiceTest {
    private final OverseasClassificationCategoryDraftRepository draftCategories =
            mock(OverseasClassificationCategoryDraftRepository.class);
    private final OverseasStockClassificationDraftRepository draftStocks =
            mock(OverseasStockClassificationDraftRepository.class);
    private final OverseasClassificationCategoryRepository liveCategories =
            mock(OverseasClassificationCategoryRepository.class);
    private final OverseasStockClassificationRepository liveStocks = mock(OverseasStockClassificationRepository.class);
    private final OverseasClassificationBackupRepository backups = mock(OverseasClassificationBackupRepository.class);
    private final OverseasClassificationPublicationService publications =
            mock(OverseasClassificationPublicationService.class);
    private final OverseasClassificationDraftService service = new OverseasClassificationDraftService(
            draftCategories, draftStocks, liveCategories, liveStocks, backups, publications, new ObjectMapper());

    private OverseasClassificationCategory live(long id, Long parentId, String name) {
        OverseasClassificationCategory category = mock(OverseasClassificationCategory.class);
        when(category.getId()).thenReturn(id);
        when(category.getParentId()).thenReturn(parentId);
        when(category.getName()).thenReturn(name);
        return category;
    }

    private OverseasClassificationCategoryDraft draft(long id, Long liveId, Long parentId, String name) {
        OverseasClassificationCategoryDraft category = mock(OverseasClassificationCategoryDraft.class);
        when(category.getId()).thenReturn(id);
        when(category.getLiveId()).thenReturn(liveId);
        when(category.getParentId()).thenReturn(parentId);
        when(category.getName()).thenReturn(name);
        return category;
    }

    private OverseasStockClassification liveStock(String code, String sector, String industry) {
        OverseasStockClassification stock =
                OverseasStockClassification.create(new OverseasStockClassificationId("JAPAN", code));
        stock.updateSector(sector);
        stock.updateIndustry(industry);
        return stock;
    }

    private OverseasStockClassificationDraft draftStock(String code, String sector, String industry) {
        OverseasStockClassificationDraft stock =
                OverseasStockClassificationDraft.create(new OverseasStockClassificationId("JAPAN", code));
        stock.updateSector(sector);
        stock.updateIndustry(industry);
        return stock;
    }

    @Test
    void 초안이_없으면_운영_분류를_상위_업종부터_복사해_운영_업종_id를_기억한다() {
        when(draftCategories.existsByMarket("JAPAN")).thenReturn(false);
        // 자식이 목록 앞에 와도 부모를 먼저 복사해야 한다.
        var child = live(2, 1L, "반도체");
        var parent = live(1, null, "기술");
        var stock = liveStock("7203", "기술", "반도체");
        when(liveCategories.findAllByMarket("JAPAN")).thenReturn(List.of(child, parent));
        when(liveStocks.findAllByIdMarket("JAPAN")).thenReturn(List.of(stock));
        var saved = new java.util.ArrayList<OverseasClassificationCategoryDraft>();
        when(draftCategories.save(any())).thenAnswer(invocation -> {
            OverseasClassificationCategoryDraft draft = invocation.getArgument(0);
            saved.add(draft);
            return draft;
        });

        service.findDraft("JAPAN");

        assertThat(saved)
                .extracting(OverseasClassificationCategoryDraft::getName)
                .containsExactly("기술", "반도체");
        assertThat(saved)
                .extracting(OverseasClassificationCategoryDraft::getLiveId)
                .containsExactly(1L, 2L);
        verify(draftStocks).save(any(OverseasStockClassificationDraft.class));
    }

    @Test
    void 초안이_이미_있으면_운영_분류를_다시_복사하지_않는다() {
        when(draftCategories.existsByMarket("JAPAN")).thenReturn(true);

        service.findDraft("JAPAN");

        verify(liveCategories, never()).findAllByMarket("JAPAN");
        verify(draftCategories, never()).save(any());
    }

    @Test
    void 종목_배정은_초안에만_저장하고_운영_분류는_건드리지_않는다() {
        var sector = draft(1, null, null, "기술");
        var industry = draft(2, null, 1L, "반도체");
        when(draftCategories.findAllByMarket("JAPAN")).thenReturn(List.of(sector, industry));
        when(draftStocks.findById(any())).thenReturn(Optional.empty());

        service.assign("JAPAN", List.of("7203"), 2);

        ArgumentCaptor<OverseasStockClassificationDraft> saved =
                ArgumentCaptor.forClass(OverseasStockClassificationDraft.class);
        verify(draftStocks).save(saved.capture());
        assertThat(saved.getValue().getSectorName()).isEqualTo("기술");
        assertThat(saved.getValue().getIndustryName()).isEqualTo("반도체");
        verify(liveStocks, never()).save(any());
        verify(liveCategories, never()).save(any());
    }

    @Test
    void 업종_이름을_바꾸면_초안_종목의_분류_이름도_같이_바뀐다() {
        var sector = draft(1, 5L, null, "기술");
        when(draftCategories.findAllByMarket("JAPAN")).thenReturn(List.of(sector));
        var stock = draftStock("7203", "기술", "반도체");
        when(draftStocks.findAllByIdMarket("JAPAN")).thenReturn(List.of(stock));

        service.rename("JAPAN", 1, "IT");

        assertThat(stock.getSectorName()).isEqualTo("IT");
        assertThat(stock.getIndustryName()).isEqualTo("반도체");
        verify(sector).rename("IT");
    }

    @Test
    void 임시_이름_접두어로_시작하는_업종_이름은_만들_수_없다() {
        when(draftCategories.findAllByMarket("JAPAN")).thenReturn(List.of());

        assertThatThrownBy(() -> service.createCategory("JAPAN", null, "~임시")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void 반영하면_운영_업종_id는_그대로_고치고_새_업종만_만들며_빠진_업종은_지운다() {
        var liveTech = live(1, null, "기술");
        var liveChip = live(2, 1L, "반도체");
        var liveFinance = live(3, null, "금융");
        when(liveCategories.findAllByMarket("JAPAN")).thenReturn(List.of(liveTech, liveChip, liveFinance));
        when(liveStocks.findAllByIdMarket("JAPAN")).thenReturn(List.of(liveStock("7203", "기술", "반도체")));
        var draftTech = draft(11, 1L, null, "IT");
        var draftChip = draft(12, 2L, 11L, "반도체");
        var draftEnergy = draft(13, null, null, "에너지");
        when(draftCategories.findAllByMarket("JAPAN")).thenReturn(List.of(draftTech, draftChip, draftEnergy));
        when(draftStocks.findAllByIdMarket("JAPAN")).thenReturn(List.of(draftStock("7203", "IT", "반도체")));
        var created = live(99, null, "~new13");
        when(liveCategories.saveAndFlush(any())).thenReturn(created);
        when(backups.findById("JAPAN")).thenReturn(Optional.empty());

        service.publish("JAPAN");

        // 이름이 겹치는 순간이 없도록 운영 업종을 먼저 임시 이름으로 바꾼 뒤 최종 이름을 정한다.
        var order = inOrder(liveTech, liveCategories, created);
        order.verify(liveTech).rename("~1");
        order.verify(liveCategories).saveAndFlush(any());
        order.verify(liveTech).rename("IT");
        verify(liveChip).reparent(1L);
        verify(liveChip).rename("반도체");
        verify(created).rename("에너지");
        verify(liveCategories).delete(liveFinance);
        verify(liveCategories, never()).delete(liveTech);
        verify(liveCategories, never()).delete(liveChip);
        // 새로 만든 초안 업종은 운영 업종 id를 기억해 다음 반영 때 같은 행을 고친다.
        verify(draftEnergy).linkLive(99L);
        verify(draftTech).linkLive(1L);
        // 종목 분류는 초안 내용으로 통째로 바뀐다.
        ArgumentCaptor<OverseasStockClassification> stocks = ArgumentCaptor.forClass(OverseasStockClassification.class);
        verify(liveStocks).deleteAllByIdMarket("JAPAN");
        verify(liveStocks).save(stocks.capture());
        assertThat(stocks.getValue().getSectorName()).isEqualTo("IT");
        // 반영 직전 운영 분류는 백업한다.
        ArgumentCaptor<OverseasClassificationBackup> backup =
                ArgumentCaptor.forClass(OverseasClassificationBackup.class);
        verify(backups).save(backup.capture());
        verify(publications).recordPublished("JAPAN");
        assertThat(backup.getValue().getPayload()).contains("기술").contains("금융").contains("7203");
    }

    @Test
    void 초안이_운영_분류와_같으면_반영하지_않고_백업도_건드리지_않는다() {
        var liveTech = live(1, null, "기술");
        var liveChip = live(2, 1L, "반도체");
        when(liveCategories.findAllByMarket("JAPAN")).thenReturn(List.of(liveTech, liveChip));
        var liveStockRow = liveStock("7203", "기술", "반도체");
        when(liveStocks.findAllByIdMarket("JAPAN")).thenReturn(List.of(liveStockRow));
        var draftTech = draft(11, 1L, null, "기술");
        var draftChip = draft(12, 2L, 11L, "반도체");
        when(draftCategories.findAllByMarket("JAPAN")).thenReturn(List.of(draftTech, draftChip));
        var draftStockRow = draftStock("7203", "기술", "반도체");
        when(draftStocks.findAllByIdMarket("JAPAN")).thenReturn(List.of(draftStockRow));

        var result = service.publish("JAPAN");

        assertThat(result.changed()).isFalse();
        verify(backups, never()).save(any());
        verify(liveStocks, never()).deleteAllByIdMarket("JAPAN");
        verify(liveTech, never()).rename(any());
        verify(publications, never()).recordPublished(any());
    }

    @Test
    void 종목_분류만_달라도_반영한다() {
        var liveTech = live(1, null, "기술");
        when(liveCategories.findAllByMarket("JAPAN")).thenReturn(List.of(liveTech));
        var liveStockRow = liveStock("7203", "기술", null);
        when(liveStocks.findAllByIdMarket("JAPAN")).thenReturn(List.of(liveStockRow));
        var draftTech = draft(11, 1L, null, "기술");
        when(draftCategories.findAllByMarket("JAPAN")).thenReturn(List.of(draftTech));
        var draftStockRow = draftStock("7203", "기술", "반도체");
        when(draftStocks.findAllByIdMarket("JAPAN")).thenReturn(List.of(draftStockRow));
        when(backups.findById("JAPAN")).thenReturn(Optional.empty());

        var result = service.publish("JAPAN");

        assertThat(result.changed()).isTrue();
        verify(backups).save(any(OverseasClassificationBackup.class));
    }

    @Test
    void 업종_이름만_달라도_반영한다() {
        var liveTech = live(1, null, "기술");
        when(liveCategories.findAllByMarket("JAPAN")).thenReturn(List.of(liveTech));
        when(liveStocks.findAllByIdMarket("JAPAN")).thenReturn(List.of());
        var draftTech = draft(11, 1L, null, "IT");
        when(draftCategories.findAllByMarket("JAPAN")).thenReturn(List.of(draftTech));
        when(draftStocks.findAllByIdMarket("JAPAN")).thenReturn(List.of());
        when(backups.findById("JAPAN")).thenReturn(Optional.empty());

        assertThat(service.publish("JAPAN").changed()).isTrue();
        verify(liveTech).rename("IT");
    }

    @Test
    void 초안이_비어_있으면_반영할_수_없다() {
        when(draftCategories.findAllByMarket("JAPAN")).thenReturn(List.of());

        assertThatThrownBy(() -> service.publish("JAPAN")).isInstanceOf(BadRequestException.class);
        verify(backups, never()).save(any());
    }

    @Test
    void 백업이_없으면_되돌릴_수_없다() {
        when(backups.findById("JAPAN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rollback("JAPAN")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void 되돌리면_백업의_업종과_종목_분류로_운영을_고치고_백업을_지운다() {
        String payload =
                "{\"categories\":[{\"id\":1,\"parentId\":null,\"name\":\"기술\"}],\"stocks\":[{\"code\":\"7203\",\"sector\":\"기술\",\"industry\":null,\"subIndustry\":null}]}";
        var backup = OverseasClassificationBackup.create("JAPAN", payload, LocalDateTime.of(2026, 11, 19, 12, 0));
        when(backups.findById("JAPAN")).thenReturn(Optional.of(backup));
        var liveTech = live(1, null, "IT");
        var liveExtra = live(5, null, "에너지");
        when(liveCategories.findAllByMarket("JAPAN")).thenReturn(List.of(liveTech, liveExtra));

        service.rollback("JAPAN");

        verify(liveTech).rename("기술");
        verify(liveCategories).delete(liveExtra);
        ArgumentCaptor<OverseasStockClassification> stocks = ArgumentCaptor.forClass(OverseasStockClassification.class);
        verify(liveStocks).save(stocks.capture());
        assertThat(stocks.getValue().getSectorName()).isEqualTo("기술");
        verify(backups).delete(backup);
        verify(publications).recordPublished("JAPAN");
    }
}
