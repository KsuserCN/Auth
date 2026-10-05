package cn.ksuser.api.dto;

import cn.ksuser.api.entity.UserPushDevice;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record PushDeviceRequest(
    @NotNull @Pattern(regexp = "(?:[0-9a-fA-F]{2}){32,256}") String deviceToken,
    @NotNull UserPushDevice.Environment environment,
    @NotNull UserPushDevice.Mode mode
) {}
