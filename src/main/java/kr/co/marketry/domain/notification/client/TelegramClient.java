package kr.co.marketry.domain.notification.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import kr.co.marketry.common.util.SecretMasker;
import kr.co.marketry.domain.notification.dto.TelegramRequest;
import kr.co.marketry.domain.notification.exception.TelegramSendException;
import kr.co.marketry.domain.notification.properties.TelegramProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramClient {

    private static final String BASE_URL = "https://api.telegram.org";
    private static final int CAUSE_CHAIN_LIMIT = 4;

    private final TelegramProperties properties;

    @Qualifier("telegramRestClient")
    private final RestClient restClient;

    private final ObjectMapper objectMapper;

    public void sendMessage(String chatId, String text) {
        long startedAt = System.nanoTime();
        try {
            restClient
                    .post()
                    .uri(botUrl("/sendMessage"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(TelegramRequest.of(chatId, text))
                    .retrieve()
                    .toBodilessEntity();

            log.debug("텔레그램 메시지 발송 완료: chatId={}", chatId);
        } catch (Exception e) {
            throw toSendException(e, "sendMessage", startedAt, text.getBytes(StandardCharsets.UTF_8).length);
        }
    }

    public void sendPhoto(String chatId, byte[] imageData, String caption) {
        long startedAt = System.nanoTime();
        try {
            MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
            form.add("chat_id", chatId);
            form.add("photo", namedResource(imageData, "single_image.png"));
            if (caption != null && !caption.isBlank()) {
                form.add("caption", caption);
            }

            restClient
                    .post()
                    .uri(botUrl("/sendPhoto"))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();

            log.debug("텔레그램 사진 발송 완료: chatId={}", chatId);
        } catch (Exception e) {
            throw toSendException(e, "sendPhoto", startedAt, imageData.length);
        }
    }

    // 여러 장을 앨범(하나의 메시지)으로 발송 — 각 사진을 하나로 합치는 게 아니라, 별개의 사진 여러 장을
    // 텔레그램 알림 1번으로 묶어 보낸다. 캡션은 첫 번째 사진에만 붙인다 — 대부분의 텔레그램 클라이언트가
    // 앨범의 캡션으로 첫 사진 것만 보여준다.
    public void sendMediaGroup(String chatId, List<byte[]> images, String caption) {
        long startedAt = System.nanoTime();
        try {
            MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
            form.add("chat_id", chatId);

            ArrayNode media = objectMapper.createArrayNode();
            for (int i = 0; i < images.size(); i++) {
                String attachName = "photo" + i;
                ObjectNode item = objectMapper.createObjectNode();
                item.put("type", "photo");
                item.put("media", "attach://" + attachName);
                if (i == 0 && caption != null && !caption.isBlank()) {
                    item.put("caption", caption);
                }
                media.add(item);
                form.add(attachName, namedResource(images.get(i), attachName + ".png"));
            }
            form.add("media", objectMapper.writeValueAsString(media));

            restClient
                    .post()
                    .uri(botUrl("/sendMediaGroup"))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();

            log.debug("텔레그램 앨범 발송 완료: chatId={}, {}장", chatId, images.size());
        } catch (Exception e) {
            int imageBytes = 0;
            for (byte[] image : images) {
                imageBytes += image.length;
            }
            throw toSendException(e, "sendMediaGroup", startedAt, imageBytes);
        }
    }

    // RestClient 예외 메시지에는 요청 URI가 들어 있고 거기에 봇 토큰이 박혀 있다. 원본 예외를 cause로 달면
    // 예외 로그의 스택트레이스와 텔레그램 알림 본문(getCauseMessage)에 토큰이 그대로 새어나간다. 그래서
    // cause 없이 원본 타입명과 마스킹한 메시지만 갖는 전용 예외로 갈아끼운다.
    // 원본 예외를 버리는 대신 실패 원인을 좁힐 단서(소요 시간, 전송 크기, 원인 체인, 스레드)를 같은 마스킹을 거쳐 메시지에 붙이고
    // 서버 로그에도 남긴다. "Request cancelled"처럼 메시지만으로는 어디서 끊겼는지 알 수 없는 실패를 구분하기 위해서다.
    private TelegramSendException toSendException(Exception e, String endpoint, long startedAt, long payloadBytes) {
        String diagnostics = createDiagnostics(e, startedAt, payloadBytes);
        String maskedMessage = SecretMasker.mask(e.getMessage(), properties.botToken());
        log.warn("[텔레그램 발송 진단] | context : {}|{}", endpoint, diagnostics);
        return new TelegramSendException(e.getClass().getSimpleName(), maskedMessage + "\n진단: " + diagnostics);
    }

    private String createDiagnostics(Exception e, long startedAt, long payloadBytes) {
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;
        Thread thread = Thread.currentThread();
        String causeChain = createCauseChain(e);
        String diagnostics = "소요 " + elapsedMillis + "ms, 전송 " + payloadBytes + "B, 원인 체인 " + causeChain + ", 스레드 "
                + thread.getName() + ", 중단 표시 " + thread.isInterrupted();
        return SecretMasker.mask(diagnostics, properties.botToken());
    }

    // 원본 예외의 cause를 타입명(메시지) 형태로 이어 붙인다. 순환 참조와 긴 체인을 막으려고 개수를 제한한다.
    private String createCauseChain(Exception e) {
        StringBuilder chain = new StringBuilder();
        Throwable cause = e.getCause();
        int depth = 0;
        while (cause != null && depth < CAUSE_CHAIN_LIMIT) {
            if (depth > 0) {
                chain.append(" > ");
            }
            chain.append(cause.getClass().getSimpleName())
                    .append('(')
                    .append(cause.getMessage())
                    .append(')');
            cause = cause.getCause() == cause ? null : cause.getCause();
            depth++;
        }
        if (depth == 0) {
            return "없음";
        }
        return chain.toString();
    }

    private String botUrl(String endpoint) {
        return BASE_URL + "/bot" + properties.botToken() + endpoint;
    }

    private ByteArrayResource namedResource(byte[] data, String filename) {
        return new ByteArrayResource(data) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }
}
