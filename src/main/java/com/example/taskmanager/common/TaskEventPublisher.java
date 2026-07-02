package com.example.taskmanager.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * タスクイベントの発行と、リスナーへの通知を行う。
 *
 * <p>リスナーリストに {@link CopyOnWriteArrayList} を使用することで、
 * イベント発行（リスト走査）中にリスナーの追加・削除が行われても
 * {@link java.util.ConcurrentModificationException} が発生しない。</p>
 *
 * Java Gold トピック:
 * <ul>
 *   <li>CopyOnWriteArrayList — 書き込み時にコピーを作成するスレッドセーフなリスト</li>
 *   <li>読みが多く書きが少ないユースケースに最適（リスナー登録は稀、イベント発行は頻繁）</li>
 *   <li>イテレーション中の ConcurrentModificationException を回避</li>
 *   <li>Observer パターンの Subject 側実装</li>
 * </ul>
 *
 * <h3>CopyOnWriteArrayList の動作原理</h3>
 * <pre>
 * 読み取り（イテレーション）:
 *   内部配列をそのまま走査 → ロック不要で高速
 *
 * 書き込み（add / remove）:
 *   1. 内部配列のコピーを作成
 *   2. コピーに対して変更を適用
 *   3. 参照を新しい配列に切り替え
 *   → 書き込みのたびにコピーが発生するため、書き込みが多いと遅い
 *
 * このクラスでの適合性:
 *   リスナー登録（add）   → アプリ起動時に数回（稀）
 *   イベント発行（iterate）→ タスク操作のたびに（頻繁）
 *   → 読みが圧倒的に多いので CopyOnWriteArrayList が適する
 * </pre>
 */
@Component
public class TaskEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(TaskEventPublisher.class);

    /**
     * CopyOnWriteArrayList: リスナーの登録・解除は稀、走査（イベント発行）は頻繁。
     * 通常の ArrayList + synchronized では走査中の追加・削除で例外が発生する。
     */
    private final CopyOnWriteArrayList<TaskEventListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * Spring が管理する全ての {@link TaskEventListener} Bean を自動登録する。
     *
     * <p>コンストラクタインジェクションで {@code List<TaskEventListener>} を受け取ると、
     * Spring が全ての実装 Bean を自動的にリストに集めてくれる。
     * これを CopyOnWriteArrayList に追加することで、起動時にリスナーが一括登録される。</p>
     *
     * @param eventListeners Spring 管理下の TaskEventListener 実装 Bean のリスト
     */
    public TaskEventPublisher(List<TaskEventListener> eventListeners) {
        listeners.addAll(eventListeners);
        log.info("[EventPublisher] {} listener(s) auto-registered: {}", eventListeners.size(),
                eventListeners.stream()
                        .map(l -> l.getClass().getSimpleName())
                        .toList());
    }

    /**
     * リスナーを登録する。
     *
     * <p>CopyOnWriteArrayList の add は内部配列のコピーを作成するため、
     * この操作中にイベント発行（走査）が行われても安全。</p>
     *
     * @param listener 登録するリスナー
     */
    public void addListener(TaskEventListener listener) {
        listeners.add(listener);
        log.info("[EventPublisher] Listener registered: {}", listener.getClass().getSimpleName());
    }

    /**
     * リスナーを解除する。
     *
     * @param listener 解除するリスナー
     */
    public void removeListener(TaskEventListener listener) {
        listeners.remove(listener);
        log.info("[EventPublisher] Listener removed: {}", listener.getClass().getSimpleName());
    }

    /**
     * イベントを全リスナーに発行する。
     *
     * <p>CopyOnWriteArrayList の走査はスナップショットで行われるため、
     * 走査中にリスナーが追加・削除されても ConcurrentModificationException は発生しない。</p>
     *
     * @param event 発行するイベント
     */
    public void publish(TaskEvent event) {
        log.info("[EventPublisher] Publishing event: {}", event);

        for (TaskEventListener listener : listeners) {
            try {
                switch (event.getEventType()) {
                    case CREATED -> listener.onTaskCreated(event);
                    case UPDATED -> listener.onTaskUpdated(event);
                    case DELETED -> listener.onTaskDeleted(event);
                }
            } catch (Exception e) {
                // 1つのリスナーの例外が他のリスナーに影響しないようにする
                log.error("[EventPublisher] Listener {} threw exception: {}",
                        listener.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
    }

    /**
     * 登録されているリスナー数を返す（学習・デバッグ用）。
     *
     * @return リスナー数
     */
    public int getListenerCount() {
        return listeners.size();
    }

    /**
     * 登録されているリスナーの不変リストを返す（学習・デバッグ用）。
     *
     * @return リスナーリスト（不変）
     */
    public List<TaskEventListener> getListeners() {
        // CopyOnWriteArrayList のイテレータはスナップショットなので
        // List.copyOf で不変リストとして返しても安全
        return List.copyOf(listeners);
    }
}
