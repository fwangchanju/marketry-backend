package kr.co.marketry.domain.banner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import kr.co.marketry.common.enums.Zone;
import kr.co.marketry.domain.banner.dto.BannerRotationSettingRequest;
import kr.co.marketry.domain.banner.entity.BannerCampaign;
import kr.co.marketry.domain.banner.entity.BannerRotationSetting;
import kr.co.marketry.domain.banner.enums.BannerType;
import kr.co.marketry.domain.banner.repository.BannerCampaignRepository;
import kr.co.marketry.domain.banner.repository.BannerRotationSettingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class BannerCampaignServiceTest {

    @Test
    void updateRotationSetting_5초_단위_간격을_저장한다() {
        BannerRotationSettingRepository settingRepository = mock(BannerRotationSettingRepository.class);
        when(settingRepository.save(org.mockito.ArgumentMatchers.any(BannerRotationSetting.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        BannerCampaignService service = new BannerCampaignService(
                mock(BannerCampaignRepository.class), settingRepository, mock(BannerImageProcessor.class));

        assertThat(service.updateRotationSetting(new BannerRotationSettingRequest(20))
                        .intervalSeconds())
                .isEqualTo(20);
    }

    @Test
    void updateRotationSetting_5초_단위가_아닌_값은_거부한다() {
        BannerCampaignService service = new BannerCampaignService(
                mock(BannerCampaignRepository.class),
                mock(BannerRotationSettingRepository.class),
                mock(BannerImageProcessor.class));

        assertThatThrownBy(() -> service.updateRotationSetting(new BannerRotationSettingRequest(22)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("배너 전환 간격을 다시 선택해 주세요.");
    }

    @Test
    void rotationIndex_설정한_간격마다_다음_배너로_순환한다() {
        assertThat(BannerCampaignService.rotationIndex(0, 10, 2)).isZero();
        assertThat(BannerCampaignService.rotationIndex(10_000, 10, 2)).isOne();
        assertThat(BannerCampaignService.rotationIndex(20_000, 10, 2)).isZero();
    }

    @Test
    void rotationIndex_배너가_세개면_순서대로_반복한다() {
        assertThat(BannerCampaignService.rotationIndex(0, 5, 3)).isZero();
        assertThat(BannerCampaignService.rotationIndex(5_000, 5, 3)).isOne();
        assertThat(BannerCampaignService.rotationIndex(10_000, 5, 3)).isEqualTo(2);
        assertThat(BannerCampaignService.rotationIndex(15_000, 5, 3)).isZero();
    }

    @Test
    void deleteCampaign_종료된_배너를_삭제한다() {
        BannerCampaignRepository repository = mock(BannerCampaignRepository.class);
        LocalDateTime now = LocalDateTime.now(Zone.KST.zoneId());
        BannerCampaign campaign = BannerCampaign.draft(
                BannerType.NOTICE, "공지", "내용", 100, false, null, null, null, now.minusDays(2), now.minusDays(1));
        campaign.approve();
        when(repository.findById(1L)).thenReturn(Optional.of(campaign));
        BannerCampaignService service = new BannerCampaignService(
                repository, mock(BannerRotationSettingRepository.class), mock(BannerImageProcessor.class));

        service.deleteCampaign(1L);

        verify(repository).delete(campaign);
    }

    @Test
    void deleteCampaign_게재중인_배너는_삭제할_수_없다() {
        BannerCampaignRepository repository = mock(BannerCampaignRepository.class);
        LocalDateTime now = LocalDateTime.now(Zone.KST.zoneId());
        BannerCampaign campaign = BannerCampaign.draft(
                BannerType.NOTICE, "공지", "내용", 100, false, null, null, null, now.minusDays(1), now.plusDays(1));
        campaign.approve();
        when(repository.findById(1L)).thenReturn(Optional.of(campaign));
        BannerCampaignService service = new BannerCampaignService(
                repository, mock(BannerRotationSettingRepository.class), mock(BannerImageProcessor.class));

        assertThatThrownBy(() -> service.deleteCampaign(1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("종료된 배너만 삭제할 수 있습니다.");
    }

    @Test
    void endCampaign_게재중인_배너를_즉시_종료한다() {
        BannerCampaignRepository repository = mock(BannerCampaignRepository.class);
        LocalDateTime now = LocalDateTime.now(Zone.KST.zoneId());
        BannerCampaign campaign = BannerCampaign.draft(
                BannerType.NOTICE, "공지", "내용", 100, false, null, null, null, now.minusDays(1), now.plusDays(1));
        campaign.approve();
        when(repository.findById(1L)).thenReturn(Optional.of(campaign));
        when(repository.save(campaign)).thenReturn(campaign);
        BannerCampaignService service = new BannerCampaignService(
                repository, mock(BannerRotationSettingRepository.class), mock(BannerImageProcessor.class));

        LocalDateTime beforeEnd = LocalDateTime.now(Zone.KST.zoneId());
        var endedCampaign = service.endCampaign(1L);
        LocalDateTime afterEnd = LocalDateTime.now(Zone.KST.zoneId());

        assertThat(endedCampaign.status()).isEqualTo("ENDED");
        assertThat(endedCampaign.endsAt()).isBetween(beforeEnd, afterEnd);
    }

    @Test
    void update_종료된_배너를_수정하면_미공개로_돌린다() {
        BannerCampaignRepository repository = mock(BannerCampaignRepository.class);
        LocalDateTime now = LocalDateTime.now(Zone.KST.zoneId());
        BannerCampaign campaign = BannerCampaign.draft(
                BannerType.NOTICE, "공지", "기존 내용", 100, false, null, null, null, now.minusDays(2), now.minusDays(1));
        campaign.approve();
        when(repository.findById(1L)).thenReturn(Optional.of(campaign));
        when(repository.save(campaign)).thenReturn(campaign);
        BannerCampaignService service = new BannerCampaignService(
                repository, mock(BannerRotationSettingRepository.class), mock(BannerImageProcessor.class));

        var updated = service.update(
                1L,
                "NOTICE",
                "새 공지",
                "새 내용",
                "",
                now.plusDays(2).withSecond(0).withNano(0).toString(),
                now.plusDays(9).withSecond(0).withNano(0).toString(),
                100,
                false,
                null);

        assertThat(updated.status()).isEqualTo("DRAFT");
        assertThat(campaign.getApprovedAt()).isNull();
    }

    @Test
    void cancelScheduled_예약된_배너를_미공개로_돌린다() {
        BannerCampaignRepository repository = mock(BannerCampaignRepository.class);
        LocalDateTime now = LocalDateTime.now(Zone.KST.zoneId());
        BannerCampaign campaign = BannerCampaign.draft(
                BannerType.NOTICE, "공지", "내용", 100, false, null, null, null, now.plusDays(1), now.plusDays(8));
        campaign.approve();
        when(repository.findById(1L)).thenReturn(Optional.of(campaign));
        when(repository.save(campaign)).thenReturn(campaign);
        BannerCampaignService service = new BannerCampaignService(
                repository, mock(BannerRotationSettingRepository.class), mock(BannerImageProcessor.class));

        var cancelled = service.cancelScheduled(1L);

        assertThat(cancelled.status()).isEqualTo("DRAFT");
        assertThat(campaign.getApprovedAt()).isNull();
    }

    @Test
    void cancelScheduled_게재중인_배너는_예약취소할_수_없다() {
        BannerCampaignRepository repository = mock(BannerCampaignRepository.class);
        LocalDateTime now = LocalDateTime.now(Zone.KST.zoneId());
        BannerCampaign campaign = BannerCampaign.draft(
                BannerType.NOTICE, "공지", "내용", 100, false, null, null, null, now.minusDays(1), now.plusDays(1));
        campaign.approve();
        when(repository.findById(1L)).thenReturn(Optional.of(campaign));
        BannerCampaignService service = new BannerCampaignService(
                repository, mock(BannerRotationSettingRepository.class), mock(BannerImageProcessor.class));

        assertThatThrownBy(() -> service.cancelScheduled(1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("예약된 배너만 예약을 취소할 수 있습니다.");
    }
}
