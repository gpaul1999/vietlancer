package com.vietlancer.file;

import com.vietlancer.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;

    public record UploadResponse(String url, String originalName, long size) {}

    /** Upload file (đăng nhập mới dùng được) → trả URL công khai để gắn vào avatar/chat. */
    @PostMapping
    public UploadResponse upload(@AuthenticationPrincipal User user, @RequestParam("file") MultipartFile file) {
        var stored = fileStorageService.store(file);
        var url = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/files/")
                .path(stored.storedName())
                .toUriString();
        return new UploadResponse(url, stored.originalName(), stored.size());
    }
}
