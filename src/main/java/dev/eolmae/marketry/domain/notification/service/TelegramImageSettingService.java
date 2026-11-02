package dev.eolmae.marketry.domain.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.eolmae.marketry.common.exception.BadRequestException;
import dev.eolmae.marketry.common.exception.ErrorCode;
import dev.eolmae.marketry.domain.notification.entity.TelegramImageSetting;
import dev.eolmae.marketry.domain.notification.repository.TelegramImageSettingRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TelegramImageSettingService {
    private final TelegramImageSettingRepository repository;
    private final ObjectMapper objectMapper;

    public record Response(Map<String, Object> values, LocalDateTime updatedAt) {}

    @Transactional(readOnly = true)
    public Response find() {
        return repository.findById(1L).map(this::response).orElse(new Response(null, null));
    }

    @Transactional
    public Response update(Map<String, Object> values) {
        if (!(values.get("preferences") instanceof Map<?, ?>) || !(values.get("excludedSectors") instanceof List<?>)) {
            throw new BadRequestException(ErrorCode.INVALID_INPUT);
        }
        try {
            String payload = objectMapper.writeValueAsString(values);
            if (payload.length() > 100_000) throw new BadRequestException(ErrorCode.INVALID_INPUT);
            TelegramImageSetting setting = repository.findById(1L).orElseGet(TelegramImageSetting::create);
            setting.update(payload);
            return response(repository.save(setting));
        } catch (JsonProcessingException e) {
            throw new BadRequestException(ErrorCode.INVALID_INPUT, e);
        }
    }

    private Response response(TelegramImageSetting setting) {
        try {
            return new Response(
                    objectMapper.readValue(setting.getPayload(), new TypeReference<>() {}), setting.getUpdatedAt());
        } catch (JsonProcessingException e) {
            throw new BadRequestException(ErrorCode.INVALID_INPUT, e);
        }
    }
}
