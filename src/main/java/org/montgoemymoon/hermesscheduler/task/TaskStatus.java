package org.montgoemymoon.hermesscheduler.task;

public enum TaskStatus {
    RUNNING,   // 正常运行中
    PAUSED,    // 已暂停，不会调度执行
    DELETED    // 逻辑删除，不再调度
}