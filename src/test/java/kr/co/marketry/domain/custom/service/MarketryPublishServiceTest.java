package kr.co.marketry.domain.custom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import kr.co.marketry.common.exception.BadRequestException;
import kr.co.marketry.common.exception.NotFoundException;
import kr.co.marketry.domain.auth.enums.Role;
import kr.co.marketry.domain.auth.service.AuthenticatedUserPrincipal;
import kr.co.marketry.domain.custom.dto.SnapshotItem;
import kr.co.marketry.domain.custom.entity.CustomSnapshot;
import kr.co.marketry.domain.custom.repository.CustomSnapshotRepository;
import kr.co.marketry.domain.notification.properties.MarketryProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

class MarketryPublishServiceTest {

    private static final long ADMIN_ID = 7L;
    private static final long PUBLISHED_ID = 900000L;

    private final CustomSnapshotRepository snapshotRepository = Mockito.mock(CustomSnapshotRepository.class);
    private final CustomSectorTreeService treeService = Mockito.mock(CustomSectorTreeService.class);
    private final MarketryProperties properties = new MarketryProperties("http://localhost:8081", 1L, PUBLISHED_ID);
    private final MarketryPublishService service =
            new MarketryPublishService(snapshotRepository, treeService, properties);

    @BeforeEach
    void loginAsAdmin() {
        var principal = new AuthenticatedUserPrincipal(ADMIN_ID, Role.ADMIN);
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void publish_운영자_분류를_약칭_포함_그대로_발행_사용자_소유로_저장하고_복원한다() {
        when(treeService.serializeCurrentSnapshot(ADMIN_ID)).thenReturn("{\"raw\":true}");
        when(snapshotRepository.save(any(CustomSnapshot.class))).thenAnswer(invocation -> {
            CustomSnapshot snapshot = invocation.getArgument(0);
            ReflectionTestUtils.setField(snapshot, "id", 55L);
            return snapshot;
        });

        SnapshotItem item = service.publish("10월 8일 고정본");

        ArgumentCaptor<CustomSnapshot> saved = ArgumentCaptor.forClass(CustomSnapshot.class);
        verify(snapshotRepository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(PUBLISHED_ID);
        assertThat(saved.getValue().getLabel()).isEqualTo("10월 8일 고정본");
        assertThat(saved.getValue().getSnapshotJson()).isEqualTo("{\"raw\":true}");
        verify(treeService).restore("{\"raw\":true}", PUBLISHED_ID, 55L);
        assertThat(item.id()).isEqualTo(55L);
    }

    @Test
    void publish_운영자_본인_데이터는_복원하지_않는다() {
        when(treeService.serializeCurrentSnapshot(ADMIN_ID)).thenReturn("{}");
        when(snapshotRepository.save(any(CustomSnapshot.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.publish("고정본");

        verify(treeService, never()).restore(any(), org.mockito.ArgumentMatchers.eq(ADMIN_ID), any());
    }

    @Test
    void getVersions_발행_사용자_것만_현재_형식인_것만_돌려준다() {
        CustomSnapshot current = CustomSnapshot.create(PUBLISHED_ID, "새 버전", "{\"v\":2}");
        CustomSnapshot legacy = CustomSnapshot.create(PUBLISHED_ID, "옛 버전", "{\"v\":1}");
        when(snapshotRepository.findAllByUserIdOrderByCreatedAtDesc(PUBLISHED_ID))
                .thenReturn(List.of(current, legacy));
        when(treeService.isCurrentSnapshotFormat("{\"v\":2}")).thenReturn(true);
        when(treeService.isCurrentSnapshotFormat("{\"v\":1}")).thenReturn(false);

        assertThat(service.getVersions()).extracting(SnapshotItem::label).containsExactly("새 버전");
    }

    @Test
    void restoreVersion_없는_버전이면_NotFound() {
        when(snapshotRepository.findByIdAndUserId(3L, PUBLISHED_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.restoreVersion(3L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void restoreVersion_옛_형식이면_거부한다() {
        CustomSnapshot legacy = CustomSnapshot.create(PUBLISHED_ID, "옛 버전", "{\"v\":1}");
        when(snapshotRepository.findByIdAndUserId(3L, PUBLISHED_ID)).thenReturn(Optional.of(legacy));
        when(treeService.isCurrentSnapshotFormat("{\"v\":1}")).thenReturn(false);

        assertThatThrownBy(() -> service.restoreVersion(3L)).isInstanceOf(BadRequestException.class);
        verify(treeService, never()).restore(any(), any(), any());
    }

    @Test
    void restoreVersion_정상이면_그_버전을_발행_사용자에게_복원한다() {
        CustomSnapshot snapshot = CustomSnapshot.create(PUBLISHED_ID, "이전 고정본", "{\"v\":2}");
        when(snapshotRepository.findByIdAndUserId(3L, PUBLISHED_ID)).thenReturn(Optional.of(snapshot));
        when(treeService.isCurrentSnapshotFormat("{\"v\":2}")).thenReturn(true);

        service.restoreVersion(3L);

        verify(treeService).restore("{\"v\":2}", PUBLISHED_ID, 3L);
    }
}
