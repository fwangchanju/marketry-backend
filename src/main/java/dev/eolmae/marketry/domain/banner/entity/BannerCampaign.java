package dev.eolmae.marketry.domain.banner.entity;

import dev.eolmae.marketry.common.enums.Zone;
import dev.eolmae.marketry.domain.banner.enums.BannerType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;

@Getter
@Entity
@Table(name = "banner_campaign")
public class BannerCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BannerType type;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(columnDefinition = "text")
    private String text;

    @Column(nullable = false)
    private int textScalePercent;

    @Column(nullable = false)
    private boolean textBold;

    @Column(length = 1000)
    private String linkUrl;

    @Column(columnDefinition = "bytea")
    private byte[] image;

    @Column(length = 32)
    private String imageContentType;

    @Column(nullable = false)
    private LocalDateTime startsAt;

    private LocalDateTime endsAt;

    private LocalDateTime approvedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected BannerCampaign() {}

    public static BannerCampaign draft(
            BannerType type,
            String title,
            String text,
            int textScalePercent,
            boolean textBold,
            String linkUrl,
            byte[] image,
            String imageContentType,
            LocalDateTime startsAt,
            LocalDateTime endsAt) {
        BannerCampaign campaign = new BannerCampaign();
        campaign.createdAt = LocalDateTime.now(Zone.KST.zoneId());
        campaign.update(type, title, text, textScalePercent, textBold, linkUrl, image, imageContentType, startsAt, endsAt);
        return campaign;
    }

    public void update(
            BannerType type,
            String title,
            String text,
            int textScalePercent,
            boolean textBold,
            String linkUrl,
            byte[] image,
            String imageContentType,
            LocalDateTime startsAt,
            LocalDateTime endsAt) {
        this.type = type;
        this.title = title;
        this.text = text;
        this.textScalePercent = textScalePercent;
        this.textBold = textBold;
        this.linkUrl = linkUrl;
        this.image = image;
        this.imageContentType = imageContentType;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.updatedAt = LocalDateTime.now(Zone.KST.zoneId());
    }

    public void approve() {
        this.approvedAt = LocalDateTime.now(Zone.KST.zoneId());
        this.updatedAt = this.approvedAt;
    }

    public void replaceEndAt(LocalDateTime endAt) {
        this.endsAt = endAt;
        this.updatedAt = LocalDateTime.now(Zone.KST.zoneId());
    }

    public void cancelApproval() {
        this.approvedAt = null;
        this.updatedAt = LocalDateTime.now(Zone.KST.zoneId());
    }
}
