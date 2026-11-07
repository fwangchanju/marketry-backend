package kr.co.marketry.domain.notification.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

    private final TelegramProperties properties;

    @Qualifier("telegramRestClient")
    private final RestClient restClient;

    private final ObjectMapper objectMapper;

    public void sendMessage(String chatId, String text) {
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
            throw toSendException(e);
        }
    }

    public void sendPhoto(String chatId, byte[] imageData, String caption) {
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
            throw toSendException(e);
        }
    }

    // 여러 장을 앨범(하나의 메시지)으로 발송 — 각 사진을 하나로 합치는 게 아니라, 별개의 사진 여러 장을
    // 텔레그램 알림 1번으로 묶어 보낸다. 캡션은 첫 번째 사진에만 붙인다 — 대부분의 텔레그램 클라이언트가
    // 앨범의 캡션으로 첫 사진 것만 보여준다.
    public void sendMediaGroup(String chatId, List<byte[]> images, String caption) {
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
            throw toSendException(e);
        }
    }

    // RestClient 예외 메시지에는 요청 URI가 들어 있고 거기에 봇 토큰이 박혀 있다. 원본 예외를 cause로 달면
    // 예외 로그의 스택트레이스와 텔레그램 알림 본문(getCauseMessage)에 토큰이 그대로 새어나간다. 그래서
    // cause 없이 원본 타입명과 마스킹한 메시지만 갖는 전용 예외로 갈아끼운다.
    private TelegramSendException toSendException(Exception e) {
        return new TelegramSendException(
                e.getClass().getSimpleName(), SecretMasker.mask(e.getMessage(), properties.botToken()));
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
