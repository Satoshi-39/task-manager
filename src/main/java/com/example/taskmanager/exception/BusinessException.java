package com.example.taskmanager.exception;

/**
 * ビジネスロジック例外の基底クラス。
 * アプリケーション固有の例外はすべてこのクラスを継承する。
 *
 * Java Gold トピック:
 * - カスタム例外階層
 */
public class BusinessException extends RuntimeException {

    private final String errorCode;

    public BusinessException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BusinessException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
