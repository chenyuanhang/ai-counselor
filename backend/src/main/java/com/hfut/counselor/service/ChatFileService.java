package com.hfut.counselor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hfut.counselor.dto.ChatFileDto;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatFileService {
    private static final int MAX_FILES_PER_ROUND = 10;
    private static final long MAX_FILE_SIZE = 100L * 1024L * 1024L;
    private static final Set<String> ALLOWED_EXTENSIONS = Collections.unmodifiableSet(new HashSet<String>(
            Arrays.asList("docx", "xls", "xlsx", "csv", "pdf", "txt", "zip", "html")
    ));

    private final ObjectMapper objectMapper;
    private final ConcurrentMap<String, ChatFileRecord> fileCache = new ConcurrentHashMap<String, ChatFileRecord>();
    private final WebClient webClient = WebClient.builder()
            .exchangeStrategies(ExchangeStrategies.builder()
                    .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(128 * 1024 * 1024))
                    .build())
            .build();

    @Value("${app.agent.base-url}")
    private String agentBaseUrl;

    @Value("${app.agent.timeout-seconds:300}")
    private long timeoutSeconds;

    public List<ChatFileDto> uploadAndExtract(String userId, MultipartFile[] files) {
        validateFiles(files);

        List<AgentUploadedFile> uploadedFiles = uploadToAgent(files);
        if (uploadedFiles.size() != files.length) {
            throw new IllegalStateException("文件上传返回数量与请求数量不一致");
        }

        List<ChatFileDto> result = new ArrayList<ChatFileDto>();
        for (int i = 0; i < files.length; i++) {
            MultipartFile source = files[i];
            AgentUploadedFile uploaded = uploadedFiles.get(i);
            ExtractedFile extracted = extractAgentFile(uploaded, source, i);

            ChatFileRecord record = new ChatFileRecord();
            record.setId(UUID.randomUUID().toString());
            record.setOwnerUserId(userId);
            record.setName(firstNonBlank(extracted.getFileName(), uploaded.getName(), source.getOriginalFilename()));
            record.setOriginalName(firstNonBlank(source.getOriginalFilename(), record.getName()));
            record.setUrl(uploaded.getUrl());
            record.setType(resolveType(record.getName()));
            record.setSize(extracted.getSize() != null ? extracted.getSize() : uploaded.getSize());
            record.setContent(extracted.getContent() == null ? "" : extracted.getContent());
            fileCache.put(record.getId(), record);

            result.add(toDto(record));
        }
        return result;
    }

    public List<ChatFileRecord> requireOwnedFiles(String userId, List<String> fileIds) {
        List<ChatFileRecord> result = new ArrayList<ChatFileRecord>();
        if (fileIds == null || fileIds.isEmpty()) {
            return result;
        }
        if (fileIds.size() > MAX_FILES_PER_ROUND) {
            throw new IllegalArgumentException("每轮对话最多上传 10 个文件");
        }

        for (String fileId : fileIds) {
            if (!StringUtils.hasText(fileId)) {
                continue;
            }
            ChatFileRecord record = fileCache.get(fileId);
            if (record == null || !userId.equals(record.getOwnerUserId())) {
                throw new IllegalArgumentException("文件已失效，请重新上传");
            }
            result.add(record);
        }
        return result;
    }

    public String toAttachmentJson(List<ChatFileRecord> files) {
        if (files == null || files.isEmpty()) {
            return null;
        }
        try {
            List<ChatFileDto> attachments = new ArrayList<ChatFileDto>();
            for (ChatFileRecord file : files) {
                attachments.add(toDto(file));
            }
            return objectMapper.writeValueAsString(attachments);
        } catch (Exception e) {
            log.warn("Failed to serialize chat attachments", e);
            return null;
        }
    }

    public String buildFilePrompt(List<ChatFileRecord> files) {
        if (files == null || files.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (ChatFileRecord file : files) {
            builder.append("文件〖")
                    .append(file.getOriginalName() == null ? file.getName() : file.getOriginalName())
                    .append("〗:\n")
                    .append(file.getContent() == null ? "" : file.getContent())
                    .append("\n\n");
        }
        return builder.toString();
    }

    private List<AgentUploadedFile> uploadToAgent(MultipartFile[] files) {
        try {
            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            for (MultipartFile file : files) {
                builder.part("files", new MultipartInputStreamResource(file))
                        .filename(firstNonBlank(file.getOriginalFilename(), file.getName()))
                        .contentType(resolveMediaType(file));
            }

            String responseText = webClient.post()
                    .uri(trimRight(agentBaseUrl) + "/feign/chat/uploadFile")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();

            JsonNode root = objectMapper.readTree(responseText);
            assertAgentSuccess(root, "文件上传失败");
            JsonNode data = root.path("data");
            if (!data.isArray()) {
                throw new IllegalStateException("文件上传响应格式异常");
            }

            List<AgentUploadedFile> result = new ArrayList<AgentUploadedFile>();
            for (JsonNode item : data) {
                AgentUploadedFile uploaded = new AgentUploadedFile();
                uploaded.setName(text(item, "name"));
                uploaded.setUrl(text(item, "url"));
                uploaded.setSize(longValue(item, "size"));
                result.add(uploaded);
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("文件上传失败：" + e.getMessage(), e);
        }
    }

    private ExtractedFile extractAgentFile(AgentUploadedFile uploaded, MultipartFile source, int index) {
        try {
            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            long uid = System.currentTimeMillis() + index;
            long size = uploaded.getSize() == null ? source.getSize() : uploaded.getSize();
            String uploadedName = firstNonBlank(uploaded.getName(), source.getOriginalFilename(), source.getName());
            String originalName = firstNonBlank(source.getOriginalFilename(), uploadedName);
            String url = uploaded.getUrl() == null ? "" : uploaded.getUrl();

            builder.part("uid", String.valueOf(uid));
            builder.part("fileName", uploadedName);
            builder.part("originalFileName", originalName);
            builder.part("filePath", url);
            builder.part("fullPath", url);
            builder.part("downloadUrl", url);
            builder.part("size", String.valueOf(size));

            String responseText = webClient.post()
                    .uri(trimRight(agentBaseUrl) + "/feign/chat/extractFile")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();

            JsonNode root = objectMapper.readTree(responseText);
            assertAgentSuccess(root, "文件解析失败");
            JsonNode data = root.path("data");

            ExtractedFile extracted = new ExtractedFile();
            extracted.setFileName(firstNonBlank(text(data, "fileName"), uploadedName));
            extracted.setContent(text(data, "content"));
            extracted.setSize(longValue(data, "size"));
            return extracted;
        } catch (Exception e) {
            throw new IllegalStateException("文件解析失败：" + e.getMessage(), e);
        }
    }

    private void validateFiles(MultipartFile[] files) {
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("请先选择文件");
        }
        if (files.length > MAX_FILES_PER_ROUND) {
            throw new IllegalArgumentException("每轮对话最多上传 10 个文件");
        }

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException("不能上传空文件");
            }
            if (file.getSize() > MAX_FILE_SIZE) {
                throw new IllegalArgumentException("单个文件大小不能超过 100M");
            }
            String name = firstNonBlank(file.getOriginalFilename(), file.getName());
            String type = resolveType(name);
            if (!ALLOWED_EXTENSIONS.contains(type)) {
                throw new IllegalArgumentException("仅支持 docx、xls、xlsx、csv、pdf、txt、zip、html 文件");
            }
        }
    }

    private void assertAgentSuccess(JsonNode root, String fallback) {
        if (root == null || root.isMissingNode()) {
            throw new IllegalStateException(fallback);
        }
        if (root.has("code") && root.path("code").asInt() != 200) {
            String message = root.path("message").asText(fallback);
            throw new IllegalStateException(message);
        }
    }

    private ChatFileDto toDto(ChatFileRecord record) {
        ChatFileDto dto = new ChatFileDto();
        dto.setId(record.getId());
        dto.setName(record.getName());
        dto.setOriginalName(record.getOriginalName());
        dto.setUrl(record.getUrl());
        dto.setType(record.getType());
        dto.setSize(record.getSize());
        return dto;
    }

    private MediaType resolveMediaType(MultipartFile file) {
        if (StringUtils.hasText(file.getContentType())) {
            try {
                return MediaType.parseMediaType(file.getContentType());
            } catch (Exception ignored) {
            }
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private String resolveType(String name) {
        if (!StringUtils.hasText(name) || !name.contains(".")) {
            return "file";
        }
        return name.substring(name.lastIndexOf('.') + 1).toLowerCase();
    }

    private Long longValue(JsonNode node, String fieldName) {
        if (node == null || !node.hasNonNull(fieldName)) {
            return null;
        }
        JsonNode value = node.get(fieldName);
        if (value.isNumber()) {
            return value.asLong();
        }
        String text = value.asText("");
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String text(JsonNode node, String fieldName) {
        if (node == null || !node.hasNonNull(fieldName)) {
            return null;
        }
        return node.get(fieldName).asText();
    }

    private String trimRight(String value) {
        if (value == null) {
            return "";
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private static class MultipartInputStreamResource extends InputStreamResource {
        private final String filename;
        private final long contentLength;

        MultipartInputStreamResource(MultipartFile file) throws IOException {
            super(file.getInputStream());
            this.filename = file.getOriginalFilename();
            this.contentLength = file.getSize();
        }

        MultipartInputStreamResource(InputStream inputStream, String filename, long contentLength) {
            super(inputStream);
            this.filename = filename;
            this.contentLength = contentLength;
        }

        @Override
        public String getFilename() {
            return filename;
        }

        @Override
        public long contentLength() {
            return contentLength;
        }
    }

    @Data
    public static class ChatFileRecord {
        private String id;
        private String ownerUserId;
        private String name;
        private String originalName;
        private String url;
        private String type;
        private Long size;
        private String content;
    }

    @Data
    private static class AgentUploadedFile {
        private String name;
        private String url;
        private Long size;
    }

    @Data
    private static class ExtractedFile {
        private String fileName;
        private String content;
        private Long size;
    }
}
