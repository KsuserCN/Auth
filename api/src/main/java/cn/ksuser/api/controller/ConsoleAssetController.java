package cn.ksuser.api.controller;

import cn.ksuser.api.config.AppProperties;
import cn.ksuser.api.dto.ApiResponse;
import cn.ksuser.api.exception.Oauth2Exception;
import cn.ksuser.api.result.ApiResultCode;
import cn.ksuser.api.service.ConsoleSessionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Uploads image assets used by official applications managed through Console. */
@RestController
@RequestMapping("/console/official-applications")
public class ConsoleAssetController {
    private static final long MAX_IMAGE_SIZE_BYTES = 3L * 1024 * 1024;
    private static final String STORAGE_DIRECTORY = "static/official-applications";

    private final ConsoleSessionService sessions;
    private final AppProperties properties;

    public ConsoleAssetController(ConsoleSessionService sessions, AppProperties properties) {
        this.sessions = sessions;
        this.properties = properties;
    }

    @PostMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, String>> uploadLogo(@RequestPart("file") MultipartFile file,
                                                        Authentication authentication) {
        ConsoleSessionService.ConsolePrincipal principal = principal(authentication);
        sessions.requireFresh(principal);

        if (file == null || file.isEmpty()) throw uploadError(HttpStatus.BAD_REQUEST, ApiResultCode.AUTH_122);
        if (file.getSize() > MAX_IMAGE_SIZE_BYTES) throw uploadError(HttpStatus.BAD_REQUEST, ApiResultCode.AUTH_123);

        String contentType = file.getContentType();
        String extension = extensionFor(contentType);
        if (extension == null) throw uploadError(HttpStatus.BAD_REQUEST, ApiResultCode.AUTH_125);

        try {
            byte[] bytes = file.getBytes();
            if (!matchesImageSignature(bytes, extension)) {
                throw uploadError(HttpStatus.BAD_REQUEST, ApiResultCode.AUTH_125);
            }

            Path storageDirectory = Paths.get(STORAGE_DIRECTORY).toAbsolutePath().normalize();
            Files.createDirectories(storageDirectory);
            String filename = UUID.randomUUID().toString().replace("-", "") + extension;
            Path target = storageDirectory.resolve(filename).normalize();
            if (!target.startsWith(storageDirectory)) throw new IllegalStateException("Invalid image path");
            Files.write(target, bytes);

            String host = properties.isDebug() ? "http://localhost:8000" : "https://api.ksuser.cn";
            return new ApiResponse<>(200, ApiResultCode.REQUEST_SUCCESS,
                Map.of("logoUrl", host + "/static/official-applications/" + filename));
        } catch (IOException ex) {
            throw uploadError(HttpStatus.INTERNAL_SERVER_ERROR, ApiResultCode.INTERNAL_ERROR);
        }
    }

    private static String extensionFor(String contentType) {
        if (contentType == null) return null;
        String normalized = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> null;
        };
    }

    private static boolean matchesImageSignature(byte[] bytes, String extension) {
        if (".jpg".equals(extension)) {
            return bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8
                && (bytes[2] & 0xff) == 0xff;
        }
        if (".png".equals(extension)) {
            byte[] signature = {(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a};
            return startsWith(bytes, signature);
        }
        if (".gif".equals(extension)) {
            return startsWith(bytes, new byte[]{'G', 'I', 'F', '8'})
                && bytes.length >= 6 && (bytes[4] == '7' || bytes[4] == '9') && bytes[5] == 'a';
        }
        if (".webp".equals(extension)) {
            return bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F'
                && bytes[3] == 'F' && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
        }
        return false;
    }

    private static boolean startsWith(byte[] value, byte[] prefix) {
        if (value.length < prefix.length) return false;
        for (int index = 0; index < prefix.length; index++) {
            if (value[index] != prefix[index]) return false;
        }
        return true;
    }

    private static ConsoleSessionService.ConsolePrincipal principal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof ConsoleSessionService.ConsolePrincipal principal)) {
            throw uploadError(HttpStatus.UNAUTHORIZED, ApiResultCode.CONSOLE_SESSION_INVALID);
        }
        return principal;
    }

    private static Oauth2Exception uploadError(HttpStatus status, ApiResultCode code) {
        return new Oauth2Exception(status, "invalid_request", code);
    }
}
