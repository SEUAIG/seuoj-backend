package com.seuoj.seuojbackend.vo.image;

import lombok.Data;

@Data
public class ImageUploadVO {
    private String imageKey;
    private String url;
    private String mimeType;
    private Long size;
}
