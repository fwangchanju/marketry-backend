package dev.eolmae.marketry.domain.banner.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BannerCampaignServiceTest {

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
}
