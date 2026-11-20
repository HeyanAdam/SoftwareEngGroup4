package com.group4.kaoyan.dto;

public class DocParseTaskDTO {
    private String fileUrl;
    private Long docId;
    private String fileName;

    // 无参构造，MQ反序列化必需
    public DocParseTaskDTO() {}

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String value) {
        this.fileUrl = value;
    }

    public Long getDocId() {
        return docId;
    }

    public void setDocId(Long value) {
        this.docId = value;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String value) {
        this.fileName = value;
    }
}
