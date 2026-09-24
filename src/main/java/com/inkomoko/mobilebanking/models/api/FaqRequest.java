package com.inkomoko.mobilebanking.models.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class FaqRequest {
    @NotNull
    private String language;
    @NotBlank
    @Size(max = 40)
    private String title;
    @NotBlank
    @Size(max = 300)
    private String body;
    private Integer displayOrder = 0;
    private Boolean active = Boolean.TRUE;
}
