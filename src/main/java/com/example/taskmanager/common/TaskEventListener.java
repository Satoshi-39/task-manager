package com.example.taskmanager.common;

/**
 * タスクイベントリスナーインタフェース。
 *
 * <p>{@link TaskEventPublisher} にリスナーとして登録し、
 * タスクの作成・更新・削除イベントを受け取る。</p>
 *
 * Java Gold トピック:
 * <ul>
 *   <li>Observer パターンの Observer 側インタフェース</li>
 *   <li>CopyOnWriteArrayList でリスナーリストを管理する際の設計</li>
 *   <li>デフォルトメソッド（default）による部分実装の許可</li>
 * </ul>
 */
public interface TaskEventListener {

    /**
     * タスク作成イベントを処理する。
     *
     * @param event イベントデータ
     */
    default void onTaskCreated(TaskEvent event) {
        // デフォルトでは何もしない（必要なイベントだけオーバーライドすればよい）
    }

    /**
     * タスク更新イベントを処理する。
     *
     * @param event イベントデータ
     */
    default void onTaskUpdated(TaskEvent event) {
        // デフォルトでは何もしない
    }

    /**
     * タスク削除イベントを処理する。
     *
     * @param event イベントデータ
     */
    default void onTaskDeleted(TaskEvent event) {
        // デフォルトでは何もしない
    }
}
