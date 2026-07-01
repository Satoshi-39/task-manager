package com.example.taskmanager.repository;

import com.example.taskmanager.domain.entity.Task;
import com.example.taskmanager.domain.enums.TaskPriority;
import com.example.taskmanager.domain.enums.TaskStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA リポジトリ。
 *
 * Java Gold トピック:
 * - Optional<Task> 戻り値
 * - @Lock(PESSIMISTIC_WRITE) による悲観的ロック
 * - @Query によるカスタムJPQL
 */
public interface TaskRepository extends JpaRepository<Task, Long> {

    /**
     * IDで検索（悲観的ロック付き）。
     * 更新時の排他制御で利用。SELECT ... FOR UPDATE 相当。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Task t WHERE t.id = :id")
    Optional<Task> findByIdWithLock(@Param("id") Long id);

    List<Task> findByStatus(TaskStatus status);

    List<Task> findByPriority(TaskPriority priority);

    @Query("SELECT t FROM Task t WHERE t.status = :status AND t.priority = :priority")
    List<Task> findByStatusAndPriority(@Param("status") TaskStatus status,
                                       @Param("priority") TaskPriority priority);

    /**
     * 期限切れ（dueDate < 指定日時 かつ 未完了）のタスクを取得する。
     */
    @Query("SELECT t FROM Task t WHERE t.dueDate < :now AND t.status NOT IN ('DONE', 'CANCELLED')")
    List<Task> findOverdueTasks(@Param("now") LocalDateTime now);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.status = :status")
    long countByStatus(@Param("status") TaskStatus status);

    Optional<Task> findByTaskNumber(String taskNumber);

    List<Task> findByAssignedUserId(Long assignedUserId);
}
