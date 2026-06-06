package com.example.server.dto;

import java.io.Serializable;

//必须实现Serializable接口，否则不能在网络上传输
public class AnalysisTaskMsg implements Serializable {
    private Long mediaId;
    private String action;
    private boolean force;

    public AnalysisTaskMsg() {}

    public AnalysisTaskMsg(Long mediaId, String action) {
        this.mediaId = mediaId;
        this.action = action;
    }

    public AnalysisTaskMsg(Long mediaId, String action, boolean force) {
        this.mediaId = mediaId;
        this.action = action;
        this.force = force;
    }

    public Long getMediaId() { return mediaId; }
    public void setMediaId(Long mediaId) { this.mediaId = mediaId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public boolean isForce() { return force; }
    public void setForce(boolean force) { this.force = force; }
}