package com.landfall.storage;

import static org.junit.jupiter.api.Assertions.*;

import com.landfall.common.api.BusinessException;
import com.landfall.common.api.ErrorCode;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class LocalDiskStorageAdapterTest {

    private LocalDiskStorageAdapter storageAdapter;

    @BeforeEach
    void setUp() {
        // 测试时把文件存到 target/test-uploads 目录
        storageAdapter = new LocalDiskStorageAdapter("./target/test-uploads");
    }

    @Test
    void shouldThrowExceptionWhenFileIsEmpty() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "test.jpg", "image/jpeg", new byte[0]);
        BusinessException ex = assertThrows(BusinessException.class, () -> storageAdapter.upload(emptyFile, "images"));
        assertEquals(ErrorCode.FILE_EMPTY, ex.getErrorCode()); // 注意：getErrorCode 视你的异常类写法而定
    }

    @Test
    void shouldThrowExceptionWhenExtensionNotAllowed() {
        MockMultipartFile txtFile = new MockMultipartFile("file", "test.txt", "text/plain", "hello".getBytes());
        BusinessException ex = assertThrows(BusinessException.class, () -> storageAdapter.upload(txtFile, "images"));
        assertEquals(ErrorCode.INVALID_FILE_EXTENSION, ex.getErrorCode());
    }

    @Test
    void shouldThrowExceptionWhenMagicNumberMismatch() {
        // 伪造一个 .jpg，但内容是纯文本（不是真正的 jpg 二进制）
        MockMultipartFile fakeJpg = new MockMultipartFile(
                "file", "fake.jpg", "image/jpeg", "not an image".getBytes(StandardCharsets.UTF_8));
        BusinessException ex = assertThrows(BusinessException.class, () -> storageAdapter.upload(fakeJpg, "images"));
        assertEquals(ErrorCode.INVALID_FILE_HEADER, ex.getErrorCode());
    }

    @Test
    void shouldUploadRealJpgSuccessfully() {
        // 构造真实的 JPG 文件头: FF D8 FF E0
        byte[] jpgHeader = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46};
        MockMultipartFile validJpg = new MockMultipartFile("file", "real.jpg", "image/jpeg", jpgHeader);

        StorageResponse response = storageAdapter.upload(validJpg, "images");

        assertNotNull(response);
        assertNotNull(response.assetId());
        assertTrue(response.url().startsWith("/images/"));
    }
}
