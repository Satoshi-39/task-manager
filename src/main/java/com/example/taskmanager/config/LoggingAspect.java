package com.example.taskmanager.config;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Controller / Service 層を対象としたロギングアスペクト。
 *
 * <p>Java Gold トピック — AOP の主要概念:
 * <ul>
 *   <li><b>Join Point（結合点）</b> — AOP が差し込まれうる個々のポイント。
 *       Spring AOP ではメソッドの実行そのもの（例: {@code TaskService.createTask()} が呼ばれた瞬間）。</li>
 *   <li><b>Pointcut（ポイントカット）</b> — Join Point を選別する条件式。
 *       {@code @Pointcut} と {@code execution(...)} 式でパッケージ・クラス・メソッド単位で指定する。
 *       このクラスでは {@link #controllerMethods()} と {@link #serviceMethods()} が該当。</li>
 *   <li><b>Advice（アドバイス）</b> — 選ばれた Join Point で実行する処理。
 *       {@code @Around} は実行前後を包括的に制御し、
 *       {@link ProceedingJoinPoint#proceed()} を呼ばないとメソッドが実行されない。
 *       このクラスでは {@link #logAround(ProceedingJoinPoint)} が該当。</li>
 *   <li>{@code @Aspect} — AspectJ スタイルでアスペクトを定義するアノテーション。
 *       Spring AOP はプロキシベースで動作し、Bean のメソッド呼び出しをインターセプトする。</li>
 *   <li>{@link ProceedingJoinPoint} — Around アドバイスで渡される結合点オブジェクト。
 *       メソッドのシグネチャ・引数の取得や、{@code proceed()} による実行制御を提供する。</li>
 * </ul>
 *
 * <p><b>注意: 自己呼び出し（self-invocation）には AOP が適用されない。</b><br>
 * Spring AOP はプロキシベースのため、同一クラス内で {@code this.method()} を呼ぶと
 * プロキシを経由せず直接呼び出される。そのためアスペクトが適用されない。
 * <pre>{@code
 * // TaskService 内の例
 * public Task createTask(TaskCreateRequest request) {
 *     // ↓ this 経由なのでプロキシを通らず、AOP が効かない
 *     Task existing = this.getTask(id);
 * }
 * }</pre>
 * AspectJ のコンパイル時ウィービングではこの制約はないが、Spring AOP では仕様上の制限となる。
 */
@Aspect
@Component
public class LoggingAspect {

    private static final Logger logger = LoggerFactory.getLogger(LoggingAspect.class);

    /** 実行時間の WARN 閾値 (ミリ秒) */
    private static final long SLOW_THRESHOLD_MS = 500;

    /**
     * Controller 層の全 public メソッドを対象とするポイントカット。
     */
    @Pointcut("execution(* com.example.taskmanager.controller..*.*(..))")
    public void controllerMethods() {
        // Pointcut 定義のみ — メソッド本体は空
    }

    /**
     * Service 層の全 public メソッドを対象とするポイントカット。
     */
    @Pointcut("execution(* com.example.taskmanager.service..*.*(..))")
    public void serviceMethods() {
        // Pointcut 定義のみ — メソッド本体は空
    }

    /**
     * Controller・Service メソッドの実行をログ記録する Around アドバイス。
     *
     * <p>以下を記録する:
     * <ul>
     *   <li>メソッド開始 — メソッド名と引数 (DEBUG)</li>
     *   <li>メソッド終了 — 実行時間 (INFO / 閾値超過時は WARN)</li>
     *   <li>例外発生 — 例外クラス名とメッセージ (ERROR)</li>
     * </ul>
     *
     * @param joinPoint 結合点 (対象メソッドの実行情報)
     * @return 対象メソッドの戻り値
     * @throws Throwable 対象メソッドがスローした例外 (そのまま再スロー)
     */
    @Around("controllerMethods() || serviceMethods()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        String args = Arrays.stream(joinPoint.getArgs())
                .map(arg -> arg == null ? "null" : arg.toString())
                .collect(Collectors.joining(", "));

        logger.debug("[AOP] START: {}.{}({})", className, methodName, args);

        long startTime = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long elapsed = System.currentTimeMillis() - startTime;

            if (elapsed >= SLOW_THRESHOLD_MS) {
                logger.warn("[AOP] SLOW: {}.{} → {}ms (threshold: {}ms)",
                        className, methodName, elapsed, SLOW_THRESHOLD_MS);
            } else {
                logger.info("[AOP] END: {}.{} → {}ms", className, methodName, elapsed);
            }

            return result;
        } catch (Throwable ex) {
            long elapsed = System.currentTimeMillis() - startTime;
            logger.error("[AOP] ERROR: {}.{} → {}ms — {}: {}",
                    className, methodName, elapsed,
                    ex.getClass().getSimpleName(), ex.getMessage());
            throw ex;
        }
    }
}
