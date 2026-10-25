package dev.eolmae.marketry.domain.banner.service;

import dev.eolmae.marketry.common.enums.Zone;
import dev.eolmae.marketry.domain.banner.dto.ActiveBannerResponse;
import dev.eolmae.marketry.domain.banner.dto.BannerCampaignResponse;
import dev.eolmae.marketry.domain.banner.dto.BannerRotationSettingRequest;
import dev.eolmae.marketry.domain.banner.dto.BannerRotationSettingResponse;
import dev.eolmae.marketry.domain.banner.entity.BannerCampaign;
import dev.eolmae.marketry.domain.banner.entity.BannerRotationSetting;
import dev.eolmae.marketry.domain.banner.enums.BannerType;
import dev.eolmae.marketry.domain.banner.repository.BannerCampaignRepository;
import dev.eolmae.marketry.domain.banner.repository.BannerRotationSettingRepository;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Comparator;
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
    private static final int DEFAULT_ROTATION_INTERVAL_SECONDS = 10;
    private static final List<Integer> ROTATION_INTERVAL_OPTIONS = List.of(5, 10, 15, 30, 60);

    private final BannerCampaignRepository repository;
    private final BannerRotationSettingRepository rotationSettingRepository;
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
            String type,
            String title,
            String text,
            String linkUrl,
            String startsAt,
            String endsAt,
            int textScalePercent,
            boolean textBold,
            byte[] imageBytes) {
        DraftValues values =
                validate(type, title, text, linkUrl, startsAt, endsAt, textScalePercent, textBold, imageBytes, null);
        BannerCampaign campaign = BannerCampaign.draft(
                values.type(),
                values.title(),
                values.text(),
                values.textScalePercent(),
                values.textBold(),
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
            int textScalePercent,
            boolean textBold,
            byte[] imageBytes) {
        BannerCampaign campaign = find(id);
        DraftValues values = validate(
                type, title, text, linkUrl, startsAt, endsAt, textScalePercent, textBold, imageBytes, campaign);
        campaign.update(
                values.type(),
                values.title(),
                values.text(),
                values.textScalePercent(),
                values.textBold(),
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
        List<BannerCampaign> activeCampaigns = repository.findActive(now);
        int intervalSeconds = rotationIntervalSeconds();
        if (activeCampaigns.isEmpty()) {
            LocalDateTime nextStart = repository
                    .findFirstByApprovedAtIsNotNullAndStartsAtAfterOrderByStartsAtAsc(now)
                    .map(BannerCampaign::getStartsAt)
                    .orElse(now.plusSeconds(intervalSeconds));
            return emptyWithRefreshAt(nextStart);
        }

        long intervalMillis = intervalSeconds * 1_000L;
        long nowMillis = now.atZone(Zone.KST.zoneId()).toInstant().toEpochMilli();
        long rotationSlot = Math.floorDiv(nowMillis, intervalMillis);
        int currentIndex = rotationIndex(nowMillis, intervalSeconds, activeCampaigns.size());
        BannerCampaign selected = activeCampaigns.get(currentIndex);

        LocalDateTime nextTransition = null;
        if (activeCampaigns.size() > 1) {
            long nextBoundaryMillis = (rotationSlot + 1) * intervalMillis;
            nextTransition = LocalDateTime.ofInstant(Instant.ofEpochMilli(nextBoundaryMillis), Zone.KST.zoneId());
        }
        LocalDateTime earliestEnd = activeCampaigns.stream()
                .map(BannerCampaign::getEndsAt)
                .filter(value -> value != null)
                .min(Comparator.naturalOrder())
                .orElse(null);
        nextTransition = earlier(nextTransition, earliestEnd);
        LocalDateTime nextStart = repository
                .findFirstByApprovedAtIsNotNullAndStartsAtAfterOrderByStartsAtAsc(now)
                .map(BannerCampaign::getStartsAt)
                .orElse(null);
        nextTransition = earlier(nextTransition, nextStart);
        if (nextTransition == null) nextTransition = now.plusSeconds(intervalSeconds);
        return toActiveResponse(selected, nextTransition);
    }

    @Transactional(readOnly = true)
    public BannerRotationSettingResponse rotationSetting() {
        BannerRotationSetting setting = rotationSettingRepository
                .findById(BannerRotationSetting.SINGLETON_ID)
                .orElse(null);
        return new BannerRotationSettingResponse(
                setting == null ? DEFAULT_ROTATION_INTERVAL_SECONDS : setting.getIntervalSeconds(),
                setting != null,
                setting == null ? null : setting.getUpdatedAt());
    }

    @Transactional
    public BannerRotationSettingResponse updateRotationSetting(BannerRotationSettingRequest request) {
        int intervalSeconds = request.intervalSeconds();
        if (!ROTATION_INTERVAL_OPTIONS.contains(intervalSeconds)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "배너 전환 간격을 다시 선택해 주세요.");
        }
        BannerRotationSetting setting = rotationSettingRepository
                .findById(BannerRotationSetting.SINGLETON_ID)
                .orElseGet(BannerRotationSetting::create);
        setting.update(intervalSeconds);
        return toRotationSettingResponse(rotationSettingRepository.save(setting));
    }

    @Transactional(readOnly = true)
    public ImagePayload adminImage(long id) {
        return image(find(id));
    }

    @Transactional(readOnly = true)
    public ImagePayload publicImage(long id) {
        BannerCampaign current = repository.findActive(now()).stream()
                .filter(campaign -> campaign.getId().equals(id))
                .findFirst()
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
            int textScalePercent,
            boolean textBold,
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
        if (textScalePercent != 80 && textScalePercent != 100 && textScalePercent != 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "글자 크기를 다시 선택해 주세요.");
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
                type,
                title,
                emptyToNull(text),
                textScalePercent,
                textBold,
                emptyToNull(linkUrl),
                startsAt,
                endsAt,
                image,
                contentType);
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
                nextRefreshAt,
                campaign.getTextScalePercent(),
                campaign.isTextBold());
    }

    private ActiveBannerResponse emptyWithRefreshAt(LocalDateTime nextRefreshAt) {
        return new ActiveBannerResponse(false, null, null, null, null, null, null, null, nextRefreshAt, 100, false);
    }

    private int rotationIntervalSeconds() {
        return rotationSettingRepository
                .findById(BannerRotationSetting.SINGLETON_ID)
                .map(BannerRotationSetting::getIntervalSeconds)
                .orElse(DEFAULT_ROTATION_INTERVAL_SECONDS);
    }

    static int rotationIndex(long epochMillis, int intervalSeconds, int campaignCount) {
        return (int) Math.floorMod(Math.floorDiv(epochMillis, intervalSeconds * 1_000L), campaignCount);
    }

    private BannerRotationSettingResponse toRotationSettingResponse(BannerRotationSetting setting) {
        return new BannerRotationSettingResponse(setting.getIntervalSeconds(), true, setting.getUpdatedAt());
    }

    private LocalDateTime earlier(LocalDateTime first, LocalDateTime second) {
        if (first == null) return second;
        if (second == null) return first;
        return first.isBefore(second) ? first : second;
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
                campaign.getImageContentType(),
                campaign.getTextScalePercent(),
                campaign.isTextBold());
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
            int textScalePercent,
            boolean textBold,
            String linkUrl,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            byte[] image,
            String imageContentType) {}
}
