package org.montgoemymoon.hermesscheduler.task;

import java.util.Date;

public class Task {
    private Long id;
    private String name;
    private String cronExpression;
    private Integer delaySeconds;
    private String taskClass;
    private TaskStatus status;
    private long nextExecuteTime;
    private Date createTime;

    // 全参构造、无参构造、getter/setter...
    // 为了简洁，这里只展示关键 getter/setter
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCronExpression() { return cronExpression; }
    public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }
    public Integer getDelaySeconds() { return delaySeconds; }
    public void setDelaySeconds(Integer delaySeconds) { this.delaySeconds = delaySeconds; }
    public String getTaskClass() { return taskClass; }
    public void setTaskClass(String taskClass) { this.taskClass = taskClass; }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
    public long getNextExecuteTime() { return nextExecuteTime; }
    public void setNextExecuteTime(long nextExecuteTime) { this.nextExecuteTime = nextExecuteTime; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
}
