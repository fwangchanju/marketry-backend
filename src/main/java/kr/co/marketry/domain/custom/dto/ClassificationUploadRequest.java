package kr.co.marketry.domain.custom.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ClassificationUploadRequest(@NotEmpty List<@Valid Assignment> assignments) {

    public record Assignment(
            @NotEmpty @Size(max = 3) List<@NotBlank @Size(max = 50) String> path,
            @NotEmpty List<@NotBlank @Size(max = 20) String> stockCodes) {}
}
