package kr.co.marketry.domain.banner.repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import kr.co.marketry.domain.banner.entity.BannerCampaign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BannerCampaignRepository extends JpaRepository<BannerCampaign, Long> {
    List<BannerCampaign> findAllByOrderByStartsAtDesc();

    Optional<BannerCampaign> findFirstByApprovedAtIsNotNullAndStartsAtAfterOrderByStartsAtAsc(LocalDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select campaign from BannerCampaign campaign where campaign.approvedAt is not null")
    List<BannerCampaign> findApprovedForUpdate();

    @Query("""
            select campaign from BannerCampaign campaign
            where campaign.approvedAt is not null
              and campaign.startsAt <= :now
              and (campaign.endsAt is null or campaign.endsAt > :now)
            order by campaign.startsAt desc, campaign.id desc
            """)
    List<BannerCampaign> findActive(@Param("now") LocalDateTime now);

    default Optional<BannerCampaign> findCurrent(LocalDateTime now) {
        return findActive(now).stream().findFirst();
    }
}
