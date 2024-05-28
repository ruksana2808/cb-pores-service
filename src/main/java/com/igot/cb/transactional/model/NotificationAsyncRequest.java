package com.igot.cb.transactional.model;

import java.util.List;
import java.util.Map;

public class NotificationAsyncRequest {
    private String type;
    private int priority;
    private Map<String, Object> action;
    private List<String> ids;
    private List<String> copyEmail;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public Map<String, Object> getAction() {
        return action;
    }

    public void setAction(Map<String, Object> action) {
        this.action = action;
    }

    public List<String> getIds() {
        return ids;
    }

    public void setIds(List<String> ids) {
        this.ids = ids;
    }

    public List<String> getCopyEmail() {
        return copyEmail;
    }

    public void setCopyEmail(List<String> copyEmail) {
        this.copyEmail = copyEmail;
    }
}
