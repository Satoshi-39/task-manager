package com.example.taskmanager.repository;

import com.example.taskmanager.domain.enums.TaskStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JdbcTemplate を利用した直接SQLリポジトリ。
 * JPAでは表現しにくい集計クエリ等で使用する。
 *
 * Java Gold トピック:
 * - JDBC（JdbcTemplate + RowMapper）
 * - 関数型インタフェース（RowMapper はラムダで実装可能）
 */
@Repository
public class TaskJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    public TaskJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * ステータスごとのタスク数を集計する。
     */
    public Map<String, Long> countGroupByStatus() {
        String sql = "SELECT status, COUNT(*) AS cnt FROM tasks GROUP BY status ORDER BY status";

        return jdbcTemplate.query(sql, (ResultSet rs) -> {
            Map<String, Long> result = new LinkedHashMap<>();
            while (rs.next()) {
                result.put(rs.getString("status"), rs.getLong("cnt"));
            }
            return result;
        });
    }

    /**
     * 優先度ごとのタスク数を集計する。
     */
    public Map<String, Long> countGroupByPriority() {
        String sql = "SELECT priority, COUNT(*) AS cnt FROM tasks GROUP BY priority ORDER BY priority";

        return jdbcTemplate.query(sql, (ResultSet rs) -> {
            Map<String, Long> result = new LinkedHashMap<>();
            while (rs.next()) {
                result.put(rs.getString("priority"), rs.getLong("cnt"));
            }
            return result;
        });
    }

    /**
     * 期限切れタスク数を取得する。
     */
    public long countOverdue() {
        String sql = "SELECT COUNT(*) FROM tasks WHERE due_date < CURRENT_TIMESTAMP AND status NOT IN ('DONE', 'CANCELLED')";
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null ? count : 0L;
    }

    /**
     * RowMapperの例：タスクサマリー（ID, タイトル, ステータス）をMapで返す。
     */
    public java.util.List<Map<String, Object>> findTaskSummaries() {
        String sql = "SELECT id, title, status, priority FROM tasks ORDER BY id";

        RowMapper<Map<String, Object>> rowMapper = (ResultSet rs, int rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rs.getLong("id"));
            row.put("title", rs.getString("title"));
            row.put("status", rs.getString("status"));
            row.put("priority", rs.getString("priority"));
            return row;
        };

        return jdbcTemplate.query(sql, rowMapper);
    }
}
