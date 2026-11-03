package dev.eolmae.marketry.domain.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 기능은 /api/admin/ 아래에만 둔다는 규칙을 고정한다. AuthConfiguration이 ADMIN 권한을 거는 곳이 /api/admin/** 뿐이라,
 * 관리자용 API를 다른 주소에 만들면 아무 권한 검사 없이 열린다. 새 컨트롤러가 생기면 아래 둘 중 하나로 바로 걸린다.
 */
class AdminApiLocationTest {

    private static final String ADMIN_PREFIX = "/api/admin";

    // 일반 회원·비로그인에게 열려 있다고 확인한 주소 묶음이다. 새 컨트롤러가 여기에 없으면 이 테스트가 실패한다 —
    // 관리자용이면 /api/admin/ 아래로 옮기고, 일반 공개용이면 보안 규칙(AuthConfiguration)을 확인한 뒤 여기에 추가한다.
    private static final Set<String> KNOWN_NON_ADMIN_PREFIXES = Set.of(
            "/api/auth",
            "/api/profile",
            "/api/map",
            "/api/custom",
            "/api/banners",
            "/api/default-preferences",
            "/api/metrics/client-events");

    // 시세·요약 조회처럼 /api 바로 아래에 개별 주소를 두는 컨트롤러는 "/api" 그대로일 때만 인정한다(/api/xxx로 시작한다고 모두 통과시키지 않는다).
    private static final String ROOT_API = "/api";

    @Test
    void 이름이_Admin으로_시작하는_컨트롤러는_api_admin_아래에_있다() {
        List<String> violations = new ArrayList<>();
        for (Class<?> controller : scanControllers()) {
            if (controller.getSimpleName().startsWith("Admin") && !isUnderAdmin(basePath(controller))) {
                violations.add(controller.getSimpleName() + " → " + basePath(controller));
            }
        }

        assertThat(violations)
                .as("관리자 컨트롤러는 /api/admin/ 아래에만 둔다(그 밖에서는 ADMIN 권한 검사가 걸리지 않는다)")
                .isEmpty();
    }

    @Test
    void 모든_컨트롤러는_확인된_주소_묶음_또는_api_admin_아래에_있다() {
        List<String> unknown = new ArrayList<>();
        for (Class<?> controller : scanControllers()) {
            String base = basePath(controller);
            if (!isUnderAdmin(base)
                    && !base.equals(ROOT_API)
                    && KNOWN_NON_ADMIN_PREFIXES.stream().noneMatch(prefix -> isUnder(base, prefix))) {
                unknown.add(controller.getSimpleName() + " → " + (base.isEmpty() ? "(주소 없음)" : base));
            }
        }

        assertThat(unknown)
                .as("새 컨트롤러 주소가 확인되지 않았다. 관리자용이면 /api/admin/ 아래로, 공개용이면 보안 규칙을 확인한 뒤 KNOWN_NON_ADMIN_PREFIXES에 추가한다")
                .isEmpty();
    }

    @Test
    void 관리자_컨트롤러가_하나_이상_있다() {
        long adminControllers = scanControllers().stream()
                .filter(controller -> isUnderAdmin(basePath(controller)))
                .count();

        assertThat(adminControllers).as("검사 대상이 비어 있으면 위 규칙이 아무것도 보지 않는 것이다").isGreaterThan(0);
    }

    private static List<Class<?>> scanControllers() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<Class<?>> controllers = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents("dev.eolmae.marketry")) {
            try {
                controllers.add(Class.forName(definition.getBeanClassName()));
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
        }
        return controllers;
    }

    private static String basePath(Class<?> controller) {
        RequestMapping mapping = controller.getAnnotation(RequestMapping.class);
        if (mapping == null) {
            return "";
        }
        String[] paths = mapping.value().length > 0 ? mapping.value() : mapping.path();
        return paths.length > 0 ? paths[0] : "";
    }

    private static boolean isUnderAdmin(String path) {
        return isUnder(path, ADMIN_PREFIX);
    }

    private static boolean isUnder(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }
}
