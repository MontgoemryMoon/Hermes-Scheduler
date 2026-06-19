package org.montgoemymoon.hermesscheduler.task;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class TaskDAO {
    public void insertTask(Task task) {
        String sql = "INSERT INTO scheduled_task (name, cron_expression, delay_seconds, task_class, status, next_execute_time) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, task.getName());
            ps.setString(2, task.getCronExpression());
            if (task.getDelaySeconds() != null) {
                ps.setInt(3, task.getDelaySeconds());
            } else {
                ps.setNull(3, Types.INTEGER);
            }
            ps.setString(4, task.getTaskClass());
            ps.setString(5, task.getStatus().name());
            ps.setLong(6, task.getNextExecuteTime());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    task.setId(rs.getLong(1));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // 根据ID查询任务
    public Task getTaskById(Long id) {
        String sql = "SELECT id, name, cron_expression, delay_seconds, task_class, status, next_execute_time, create_time FROM scheduled_task WHERE id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRowToTask(rs);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // 获取所有任务（未逻辑删除的）
    public List<Task> getAllTasks() {
        List<Task> list = new ArrayList<>();
        String sql = "SELECT id, name, cron_expression, delay_seconds, task_class, status, next_execute_time, create_time FROM scheduled_task WHERE status != 'DELETED' ORDER BY next_execute_time";
        try (Connection conn = DBUtil.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapRowToTask(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    // 获取下次执行时间在 [start, end] 范围内且状态为 RUNNING 的任务
    public static List<Task> getTasksByNextTime(long start, long end) {
        List<Task> list = new ArrayList<>();
        String sql = "SELECT id, name, cron_expression, delay_seconds, task_class, status, next_execute_time, create_time FROM scheduled_task WHERE status = 'RUNNING' AND next_execute_time BETWEEN ? AND ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, start);
            ps.setLong(2, end);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToTask(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    // 更新下次执行时间
    public void updateNextExecuteTime(Long taskId, long nextTime) {
        String sql = "UPDATE scheduled_task SET next_execute_time = ? WHERE id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, nextTime);
            ps.setLong(2, taskId);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // 更新任务状态（暂停/恢复）
    public void updateTaskStatus(Long taskId, TaskStatus status) {
        String sql = "UPDATE scheduled_task SET status = ? WHERE id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setLong(2, taskId);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // 逻辑删除任务
    public void deleteTask(Long taskId) {
        updateTaskStatus(taskId, TaskStatus.DELETED);
    }

    // 删除物理记录（慎用）
    public void physicallyDeleteTask(Long taskId) {
        String sql = "DELETE FROM scheduled_task WHERE id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, taskId);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // 结果集映射
    private static Task mapRowToTask(ResultSet rs) throws SQLException {
        Task task = new Task();
        task.setId(rs.getLong("id"));
        task.setName(rs.getString("name"));
        task.setCronExpression(rs.getString("cron_expression"));
        int delay = rs.getInt("delay_seconds");
        if (!rs.wasNull()) {
            task.setDelaySeconds(delay);
        }
        task.setTaskClass(rs.getString("task_class"));
        task.setStatus(TaskStatus.valueOf(rs.getString("status")));
        task.setNextExecuteTime(rs.getLong("next_execute_time"));
        task.setCreateTime(rs.getTimestamp("create_time"));
        return task;
    }
}
