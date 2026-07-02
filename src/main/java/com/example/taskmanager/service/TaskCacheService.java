package com.example.taskmanager.service;

import com.example.taskmanager.domain.entity.Task;
import com.example.taskmanager.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ConcurrentHashMap を使用したタスクのインメモリキャッシュ。
 *
 * <p>頻繁にアクセスされるタスクをキャッシュし、DB アクセスを削減する。
 * キャッシュミス時は DB から取得してキャッシュに格納する。</p>
 *
 * Java Gold トピック:
 * <ul>
 *   <li>ConcurrentHashMap — スレッドセーフな HashMap 実装</li>
 *   <li>computeIfAbsent — キーが存在しない場合にのみ値を計算して格納</li>
 *   <li>部分ロック（セグメントロック）による高い並行性</li>
 * </ul>
 *
 * <h3>ConcurrentHashMap vs synchronized(HashMap)</h3>
 * <pre>
 * synchronized(HashMap):
 *   Map 全体を1つのロックで保護 → 同時に1スレッドしかアクセスできない
 *   Thread A: synchronized(map) { map.get("key1") }  ← ロック中
 *   Thread B: synchronized(map) { map.get("key2") }  ← Thread A のロック解放を待つ
 *
 * ConcurrentHashMap:
 *   内部的にセグメント（バケット）ごとにロック → 異なるキーなら同時アクセス可能
 *   Thread A: map.get("key1")  ← セグメント1のロック
 *   Thread B: map.get("key2")  ← セグメント2のロック（別セグメントなので並行OK）
 * </pre>
 *
 * <h3>computeIfAbsent の学習ポイント</h3>
 * <pre>
 * // 非スレッドセーフな「なければ入れる」パターン
 * if (!map.containsKey(key)) {       // ← ここと
 *     map.put(key, loadFromDB(key)); // ← ここの間に別スレッドが割り込む可能性
 * }
 *
 * // ConcurrentHashMap の computeIfAbsent ならアトミック
 * map.computeIfAbsent(key, k -> loadFromDB(k));  // チェック＋格納がアトミック
 * </pre>
 */
@Service
public class TaskCacheService {

    private static final Logger log = LoggerFactory.getLogger(TaskCacheService.class);

    private final ConcurrentHashMap<Long, Task> cache = new ConcurrentHashMap<>();
    private final TaskRepository taskRepository;

    public TaskCacheService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    /**
     * キャッシュからタスクを取得する。キャッシュミス時は DB から取得して格納する。
     *
     * <p>{@link ConcurrentHashMap#computeIfAbsent} を使用することで、
     * 「キーの存在チェック → DB 取得 → キャッシュ格納」をアトミックに実行する。
     * 複数スレッドが同時に同じキーで呼び出しても、DB クエリは1回だけ実行される。</p>
     *
     * @param id タスクID
     * @return タスク（Optional）。DB にも存在しない場合は空
     */
    public Optional<Task> getOrLoad(Long id) {
        Task cached = cache.computeIfAbsent(id, key -> {
            log.info("[Cache] MISS - Loading task {} from DB", key);
            return taskRepository.findById(key).orElse(null);
        });

        if (cached != null) {
            log.debug("[Cache] HIT - Task {} found in cache", id);
        }

        return Optional.ofNullable(cached);
    }

    /**
     * タスクをキャッシュに格納する。
     *
     * <p>タスクの作成・更新時に呼び出し、キャッシュを最新の状態に保つ。
     * ConcurrentHashMap の put はスレッドセーフ。</p>
     *
     * @param id   タスクID
     * @param task タスク
     */
    public void put(Long id, Task task) {
        cache.put(id, task);
        log.debug("[Cache] PUT - Task {} cached", id);
    }

    /**
     * タスクをキャッシュから除去する。
     *
     * <p>タスク削除時に呼び出し、古いキャッシュが残らないようにする。</p>
     *
     * @param id タスクID
     */
    public void evict(Long id) {
        Task removed = cache.remove(id);
        if (removed != null) {
            log.info("[Cache] EVICT - Task {} removed from cache", id);
        }
    }

    /**
     * キャッシュを全クリアする。
     */
    public void clear() {
        int size = cache.size();
        cache.clear();
        log.info("[Cache] CLEAR - {} entries removed", size);
    }

    /**
     * キャッシュの統計情報を返す（学習・デバッグ用）。
     *
     * @return キャッシュサイズ
     */
    public int getCacheSize() {
        return cache.size();
    }
}
