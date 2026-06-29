package com.example.taskmanager.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 汎用APIレスポンスラッパー。
 *
 * Java Gold トピック:
 * - Generics（型パラメータ, ワイルドカード, ファクトリメソッド）
 * - static ファクトリメソッド
 *
 * 境界型の学習例として、成功レスポンス用の successSerializable() メソッドでは
 * {@code <T extends Serializable>} を使い、型安全性を高めている。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> implements Serializable {

    private final boolean success;
    private final T data;
    private final String message;
    private final List<String> errors;
    private final LocalDateTime timestamp;

    private ApiResponse(boolean success, T data, String message, List<String> errors) {
        this.success = success;
        this.data = data;
        this.message = message;
        this.errors = errors;
        this.timestamp = LocalDateTime.now();
    }

    // --- 標準ファクトリメソッド ---

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, data, message, null);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, null, message, null);
    }

    public static <T> ApiResponse<T> error(String message, List<String> errors) {
        return new ApiResponse<>(false, null, message, errors);
    }

    /**
     * 境界型パラメータの学習用ファクトリメソッド。
     * Serializable を実装する型のみ受け付ける。
     */
    public static <T extends Serializable> ApiResponse<T> successSerializable(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    // --- Getters ---

    public boolean isSuccess() {
        return success;
    }

    public T getData() {
        return data;
    }

    public String getMessage() {
        return message;
    }

    public List<String> getErrors() {
        return errors;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
}
