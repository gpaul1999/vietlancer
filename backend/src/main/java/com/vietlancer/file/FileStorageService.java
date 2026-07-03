package com.vietlancer.file;

import com.vietlancer.common.ApiException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Lưu file lên đĩa local (`app.storage.dir`). Tên file = UUID (không đoán được),
 * chỉ nhận whitelist extension. Khi cần scale → thay bằng S3-compatible storage,
 * interface giữ nguyên.
 */
@Service
public class FileStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "webp", "gif",          // ảnh
            "pdf", "doc", "docx", "xls", "xlsx", "txt",    // tài liệu
            "zip", "rar");                                 // nén

    private final Path storageDir;

    public FileStorageService(@Value("${app.storage.dir:./uploads}") String dir) {
        this.storageDir = Path.of(dir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(storageDir);
        } catch (IOException e) {
            throw new IllegalStateException("Không tạo được thư mục lưu file: " + storageDir, e);
        }
    }

    /** Lưu file, trả về tên file đã sinh (UUID.ext). */
    public StoredFile store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("File rỗng");
        }
        var original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        var dotIndex = original.lastIndexOf('.');
        var extension = dotIndex < 0 ? "" : original.substring(dotIndex + 1).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw ApiException.badRequest(
                    "Định dạng ." + extension + " không được hỗ trợ. Cho phép: " + ALLOWED_EXTENSIONS);
        }

        var storedName = UUID.randomUUID() + "." + extension;
        var target = storageDir.resolve(storedName).normalize();
        if (!target.startsWith(storageDir)) {
            throw ApiException.badRequest("Tên file không hợp lệ");
        }
        try {
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Không lưu được file", e);
        }
        return new StoredFile(storedName, original, file.getSize());
    }

    public Path dir() {
        return storageDir;
    }

    public record StoredFile(String storedName, String originalName, long size) {}
}
