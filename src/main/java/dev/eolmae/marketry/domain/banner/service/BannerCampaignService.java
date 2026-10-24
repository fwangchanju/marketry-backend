package dev.eolmae.marketry.domain.banner.service;

import dev.eolmae.marketry.common.enums.Zone;
import dev.eolmae.marketry.domain.banner.dto.ActiveBannerResponse;
import dev.eolmae.marketry.domain.banner.dto.BannerCampaignResponse;
import dev.eolmae.marketry.domain.banner.entity.BannerCampaign;
import dev.eolmae.marketry.domain.banner.enums.BannerType;
import dev.eolmae.marketry.domain.banner.repository.BannerCampaignRepository;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class BannerCampaignService {

    private static final int MAX_TITLE_LENGTH = 100;
    private static final int MAX_NOTICE_LENGTH = 200;

    private final BannerCampaignRepository repository;
    private final BannerImageProcessor imageProcessor;

    @Transactional(readOnly = true)
    public List<BannerCampaignResponse> list() {
        LocalDateTime now = now();
        return repository.findAllByOrderByStartsAtDesc().stream()
                .map(item -> toResponse(item, now))
                .toList();
    }

    @Transactional
    public BannerCampaignResponse create(
            String type, String title, String text, String linkUrl, String startsAt, String endsAt, byte[] imageBytes) {
        DraftValues values = validate(type, title, text, linkUrl, startsAt, endsAt, imageBytes, null);
        BannerCampaign campaign = BannerCampaign.draft(
                values.type(),
                values.title(),
                values.text(),
                values.linkUrl(),
                values.image(),
                values.imageContentType(),
                values.startsAt(),
                values.endsAt());
        return toResponse(repository.save(campaign), now());
    }

    @Transactional
    public BannerCampaignResponse update(
            long id,
            String type,
            String title,
            String text,
            String linkUrl,
            String startsAt,
            String endsAt,
            byte[] imageBytes) {
        BannerCampaign campaign = find(id);
        if (campaign.getApprovedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "승인된 배너는 수정할 수 없습니다. 새 배너로 등록해 주세요.");
        }
        DraftValues values = validate(type, title, text, linkUrl, startsAt, endsAt, imageBytes, campaign);
        campaign.update(
                values.type(),
                values.title(),
                values.text(),
                values.linkUrl(),
                values.image(),
                values.imageContentType(),
                values.startsAt(),
                values.endsAt());
        return toResponse(repository.save(campaign), now());
    }

    @Transactional
    public BannerCampaignResponse approve(long id) {
        BannerCampaign campaign = find(id);
        if (campaign.getApprovedAt() != null) {
            return toResponse(campaign, now());
        }
        LocalDateTime now = now();
        if (campaign.getStartsAt() == null
                || (campaign.getEndsAt() != null && !campaign.getEndsAt().isAfter(campaign.getStartsAt()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "게시 시작·종료 시각을 확인해 주세요.");
        }
        List<BannerCampaign> approved = repository.findApprovedForUpdate();
        for (BannerCampaign existing : approved) {
            if (!overlaps(campaign, existing)) continue;
            boolean existingRunsBeforeNew = existing.getStartsAt().isBefore(campaign.getStartsAt())
                    && (existing.getEndsAt() == null || existing.getEndsAt().isAfter(campaign.getStartsAt()));
            if (!existingRunsBeforeNew) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "다른 배너의 게시 시간과 겹칩니다. 시작·종료 시각을 조정해 주세요.");
            }
        }
        for (BannerCampaign existing : approved) {
            if (overlaps(campaign, existing)
                    && existing.getStartsAt().isBefore(campaign.getStartsAt())
                    && (existing.getEndsAt() == null || existing.getEndsAt().isAfter(campaign.getStartsAt()))) {
                existing.replaceEndAt(campaign.getStartsAt());
            }
        }
        campaign.approve();
        return toResponse(repository.save(campaign), now);
    }

    @Transactional
    public void deleteDraft(long id) {
        BannerCampaign campaign = find(id);
        if (campaign.getApprovedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "승인된 배너는 삭제할 수 없습니다.");
        }
        repository.delete(campaign);
    }

    @Transactional(readOnly = true)
    public ActiveBannerResponse active() {
        LocalDateTime now = now();
        return repository
                .findCurrent(now)
                .map(campaign -> toActiveResponse(campaign, campaign.getEndsAt()))
                .orElseGet(() -> repository
                        .findFirstByApprovedAtIsNotNullAndStartsAtAfterOrderByStartsAtAsc(now)
                        .map(campaign -> emptyWithRefreshAt(campaign.getStartsAt()))
                        .orElseGet(ActiveBannerResponse::empty));
    }

    @Transactional(readOnly = true)
    public ImagePayload adminImage(long id) {
        return image(find(id));
    }

    @Transactional(readOnly = true)
    public ImagePayload publicImage(long id) {
        BannerCampaign current = repository
                .findCurrent(now())
                .filter(campaign -> campaign.getId().equals(id))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return image(current);
    }

    private DraftValues validate(
            String rawType,
            String rawTitle,
            String rawText,
            String rawLinkUrl,
            String rawStartsAt,
            String rawEndsAt,
            byte[] imageBytes,
            BannerCampaign existing) {
        BannerType type;
        try {
            type = BannerType.valueOf(rawType);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "배너 종류를 선택해 주세요.");
        }
        String title = rawTitle == null ? "" : rawTitle.strip();
        String text = rawText == null ? "" : rawText.strip();
        String linkUrl = rawLinkUrl == null ? "" : rawLinkUrl.strip();
        if (title.isEmpty() || title.length() > MAX_TITLE_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "제목은 1~100자로 입력해 주세요.");
        }
        if (type == BannerType.NOTICE && (text.isEmpty() || text.length() > MAX_NOTICE_LENGTH)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "공지 문구는 1~200자로 입력해 주세요.");
        }
        if (type == BannerType.AD && text.length() > MAX_NOTICE_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "배너 설명은 200자 이하로 입력해 주세요.");
        }
        validateLink(linkUrl);
        LocalDateTime startsAt = parseDate(rawStartsAt, "게시 시작 시각");
        LocalDateTime endsAt = rawEndsAt == null || rawEndsAt.isBlank() ? null : parseDate(rawEndsAt, "게시 종료 시각");
        if (endsAt != null && !endsAt.isAfter(startsAt)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "종료 시각은 시작 시각보다 뒤여야 합니다.");
        }
        byte[] image = null;
        String contentType = null;
        if (type == BannerType.AD) {
            if (imageBytes != null && imageBytes.length > 0) {
                BannerImageProcessor.ProcessedBannerImage processed = imageProcessor.validate(imageBytes);
                image = processed.bytes();
                contentType = processed.contentType();
            } else if (existing != null) {
                image = existing.getImage();
                contentType = existing.getImageContentType();
            }
            if (image == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "광고 이미지를 올려 주세요.");
            }
        }
        return new DraftValues(
                type, title, emptyToNull(text), emptyToNull(linkUrl), startsAt, endsAt, image, contentType);
    }

    private void validateLink(String linkUrl) {
        if (linkUrl.isEmpty()) return;
        try {
            URI uri = URI.create(linkUrl);
            if (uri.isAbsolute()) {
                if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                        || uri.getHost() == null) {
                    throw new IllegalArgumentException();
                }
            } else if (!linkUrl.startsWith("/") || linkUrl.startsWith("//")) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "링크는 https:// 주소 또는 사이트 내부 경로로 입력해 주세요.");
        }
    }

    private LocalDateTime parseDate(String value, String label) {
        try {
            return LocalDateTime.parse(value);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + "을 선택해 주세요.");
        }
    }

    private boolean overlaps(BannerCampaign first, BannerCampaign second) {
        boolean firstBeforeSecondEnds =
                second.getEndsAt() == null || first.getStartsAt().isBefore(second.getEndsAt());
        boolean secondBeforeFirstEnds =
                first.getEndsAt() == null || second.getStartsAt().isBefore(first.getEndsAt());
        return firstBeforeSecondEnds && secondBeforeFirstEnds;
    }

    private BannerCampaign find(long id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private ImagePayload image(BannerCampaign campaign) {
        if (campaign.getImage() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return new ImagePayload(campaign.getImage(), campaign.getImageContentType());
    }

    private ActiveBannerResponse toActiveResponse(BannerCampaign campaign, LocalDateTime nextRefreshAt) {
        return new ActiveBannerResponse(
                true,
                campaign.getId(),
                campaign.getType(),
                campaign.getTitle(),
                campaign.getText(),
                campaign.getLinkUrl(),
                campaign.getImage() == null ? null : "/api/banners/" + campaign.getId() + "/image",
                campaign.getImageContentType(),
                nextRefreshAt);
    }

    private ActiveBannerResponse emptyWithRefreshAt(LocalDateTime nextRefreshAt) {
        return new ActiveBannerResponse(false, null, null, null, null, null, null, null, nextRefreshAt);
    }

    private BannerCampaignResponse toResponse(BannerCampaign campaign, LocalDateTime now) {
        String status = campaign.getApprovedAt() == null
                ? "DRAFT"
                : campaign.getEndsAt() != null && !campaign.getEndsAt().isAfter(now)
                        ? "ENDED"
                        : campaign.getStartsAt().isAfter(now) ? "SCHEDULED" : "LIVE";
        return new BannerCampaignResponse(
                campaign.getId(),
                campaign.getType(),
                campaign.getTitle(),
                campaign.getText(),
                campaign.getLinkUrl(),
                campaign.getStartsAt(),
                campaign.getEndsAt(),
                status,
                campaign.getUpdatedAt(),
                campaign.getImage() != null,
                campaign.getImageContentType());
    }

    private LocalDateTime now() {
        return LocalDateTime.now(Zone.KST.zoneId());
    }

    private String emptyToNull(String value) {
        return value.isEmpty() ? null : value;
    }

    @SuppressWarnings("ArrayRecordComponent")
    public record ImagePayload(byte[] bytes, String contentType) {}

    @SuppressWarnings("ArrayRecordComponent")
    private record DraftValues(
            BannerType type,
            String title,
            String text,
            String linkUrl,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            byte[] image,
            String imageContentType) {}
}
