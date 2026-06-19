package org.montgoemymoon.hermesscheduler.task;

import io.netty.util.HashedWheelTimer;
import org.quartz.CronExpression;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TaskScheduler {
    private static TaskScheduler instance;
    private TaskDAO taskDAO = new TaskDAO();
    private HashedWheelTimer timer = new HashedWheelTimer(100, TimeUnit.MILLISECONDS, 512);
    private ScheduledExecutorService loader = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean leaderMode = false;

    public static TaskScheduler getInstance() { return instance; }

    public void startScheduling() {
        if (leaderMode) return;
        leaderMode = true;
        // 每隔 5 秒从数据库加载即将执行的任务
        loader.scheduleAtFixedRate(this::loadAndScheduleTasks, 0, 5, TimeUnit.SECONDS);
    }

    public void stopScheduling() {
        leaderMode = false;
    }

    private void loadAndScheduleTasks() {
        if (!leaderMode) return;
        long now = System.currentTimeMillis();
        long limit = now + 60_000; // 预加载未来 1 分钟的任务
        List<Task> tasks = TaskDAO.getTasksByNextTime(now, limit);
        for (Task task : tasks) {
            if (task.getNextExecuteTime() <= now) {
                // 立即执行
                executeTask(task);
            } else {
                // 加入时间轮，延迟执行
                timer.newTimeout(timeout -> executeTask(task),
                        task.getNextExecuteTime() - now, TimeUnit.MILLISECONDS);
            }
        }
    }

    private void executeTask(Task task) {
        if (!leaderMode) return;
        try {
            Runnable runnable = (Runnable) Class.forName(task.getTaskClass()).getDeclaredConstructor().newInstance();
            runnable.run();
            long nextTime = computeNextTime(task);
            if (nextTime > 0) {
                taskDAO.updateNextExecuteTime(task.getId(), nextTime);  // 实例调用
            } else {
                taskDAO.deleteTask(task.getId());  // 实例调用
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private long computeNextTime(Task task) {
        if (task.getCronExpression() != null && !task.getCronExpression().isEmpty()) {
            try {
                CronExpression cron = new CronExpression(task.getCronExpression());
                Date next = cron.getNextValidTimeAfter(new Date());
                return next != null ? next.getTime() : -1;
            } catch (Exception e) {
                e.printStackTrace();
                return -1;
            }
        } else if (task.getDelaySeconds() != null && task.getDelaySeconds() > 0) {
            return System.currentTimeMillis() + task.getDelaySeconds() * 1000L;
        }
        return -1;
    }
}
