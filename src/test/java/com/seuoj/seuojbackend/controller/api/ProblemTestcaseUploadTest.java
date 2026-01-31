package com.seuoj.seuojbackend.controller.api;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.seuoj.seuojbackend.client.JudgeClient;
import com.seuoj.seuojbackend.common.AuthStatus;
import com.seuoj.seuojbackend.entity.Problem;
import com.seuoj.seuojbackend.interceptor.UserContextHolder;
import com.seuoj.seuojbackend.mapper.ProblemMapper;
import com.seuoj.seuojbackend.mapper.UserRoleRelMapper;
import com.seuoj.seuojbackend.util.JwtUtil;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ProblemTestcaseUploadTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProblemMapper problemMapper;

    @MockBean
    private JudgeClient judgeClient;

    @MockBean
    private UserRoleRelMapper userRoleRelMapper;

    @MockBean
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        Problem problem = new Problem();
        problem.setPid("P1000");
        when(problemMapper.selectOne(any())).thenReturn(problem);
        when(userRoleRelMapper.getRoleCodesByUserId(1L)).thenReturn(List.of("ADMIN"));
        when(jwtUtil.parseUserId(any())).thenReturn(1L);
        doNothing().when(judgeClient).uploadProblemData(any());
        UserContextHolder.set(com.seuoj.seuojbackend.interceptor.UserContext.of(1L, AuthStatus.AUTHENTICATED));
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void uploadZipWithInvalidUtf8ShouldFail() throws Exception {
        byte[] zipBytes = buildZipBytes(new Entry("1.in", new byte[]{(byte) 0xC3, (byte) 0x28}),
                new Entry("1.ans", "ok".getBytes(StandardCharsets.UTF_8)));
        MockMultipartFile file = new MockMultipartFile("file", "data.zip", MediaType.APPLICATION_OCTET_STREAM_VALUE, zipBytes);

        mockMvc.perform(multipart("/api/problem/testcases/{pid}", "P1000")
                        .file(file)
                        .param("format", "zip")
                        .param("name_rule", "1 1.in 1.ans")
                        .header("Authorization", "Bearer testtoken"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("UTF-8")));
    }

    @Test
    void uploadZipButFormatTarShouldFail() throws Exception {
        byte[] zipBytes = buildZipBytes(new Entry("1.in", "1".getBytes(StandardCharsets.UTF_8)),
                new Entry("1.ans", "1".getBytes(StandardCharsets.UTF_8)));
        MockMultipartFile file = new MockMultipartFile("file", "data.zip", MediaType.APPLICATION_OCTET_STREAM_VALUE, zipBytes);

        mockMvc.perform(multipart("/api/problem/testcases/{pid}", "P1000")
                        .file(file)
                        .param("format", "tar")
                        .param("name_rule", "1 1.in 1.ans")
                        .header("Authorization", "Bearer testtoken"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("文件格式与内容不匹配"));
    }

    @Test
    void uploadTarShouldSucceed() throws Exception {
        byte[] tarBytes = buildTarBytes(new Entry("1.in", "1".getBytes(StandardCharsets.UTF_8)),
                new Entry("1.ans", "1".getBytes(StandardCharsets.UTF_8)));
        MockMultipartFile file = new MockMultipartFile("file", "data.tar", MediaType.APPLICATION_OCTET_STREAM_VALUE, tarBytes);

        mockMvc.perform(multipart("/api/problem/testcases/{pid}", "P1000")
                        .file(file)
                        .param("format", "tar")
                        .param("name_rule", "1 1.in 1.ans")
                        .header("Authorization", "Bearer testtoken"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void uploadTarGzShouldSucceed() throws Exception {
        byte[] tarGzBytes = buildTarGzBytes(new Entry("1.in", "1".getBytes(StandardCharsets.UTF_8)),
                new Entry("1.ans", "1".getBytes(StandardCharsets.UTF_8)));
        MockMultipartFile file = new MockMultipartFile("file", "data.tar.gz", MediaType.APPLICATION_OCTET_STREAM_VALUE, tarGzBytes);

        mockMvc.perform(multipart("/api/problem/testcases/{pid}", "P1000")
                        .file(file)
                        .param("format", "tar.gz")
                        .param("name_rule", "1 1.in 1.ans")
                        .header("Authorization", "Bearer testtoken"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private static byte[] buildZipBytes(Entry... entries) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos, StandardCharsets.UTF_8)) {
            for (Entry entry : entries) {
                ZipEntry zipEntry = new ZipEntry(entry.name());
                zos.putNextEntry(zipEntry);
                zos.write(entry.bytes());
                zos.closeEntry();
            }
        }
        return bos.toByteArray();
    }

    private static byte[] buildTarBytes(Entry... entries) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tos = new TarArchiveOutputStream(bos, StandardCharsets.UTF_8.name())) {
            for (Entry entry : entries) {
                TarArchiveEntry tarEntry = new TarArchiveEntry(entry.name());
                tarEntry.setSize(entry.bytes().length);
                tos.putArchiveEntry(tarEntry);
                tos.write(entry.bytes());
                tos.closeArchiveEntry();
            }
            tos.finish();
        }
        return bos.toByteArray();
    }

    private static byte[] buildTarGzBytes(Entry... entries) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bos);
             TarArchiveOutputStream tos = new TarArchiveOutputStream(gzip, StandardCharsets.UTF_8.name())) {
            for (Entry entry : entries) {
                TarArchiveEntry tarEntry = new TarArchiveEntry(entry.name());
                tarEntry.setSize(entry.bytes().length);
                tos.putArchiveEntry(tarEntry);
                tos.write(entry.bytes());
                tos.closeArchiveEntry();
            }
            tos.finish();
        }
        return bos.toByteArray();
    }

    private record Entry(String name, byte[] bytes) {
    }
}
