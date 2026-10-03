package com.substation.ops.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 通知确认已读请求体：只能由通知接收本人确认自己那一行。
 */
@Getter
@Setter
@NoArgsConstructor
public class NotificationReadRequest {

    @NotBlank(message = "当前值班员姓名不能为空")
    private String person;
}
