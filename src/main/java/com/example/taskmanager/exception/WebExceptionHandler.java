package com.example.taskmanager.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;

/**
 * Web画面用例外ハンドラ。
 * @Controller アノテーションが付与されたコントローラでのみ有効。
 * エラーページ表示やフラッシュメッセージでリダイレクトする。
 */
@ControllerAdvice(annotations = Controller.class)
public class WebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(WebExceptionHandler.class);

    private final MessageSource messageSource;

    public WebExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(TaskNotFoundException.class)
    public String handleTaskNotFound(TaskNotFoundException ex, Model model) {
        log.warn("Task not found (web): {}", ex.getMessage());
        Locale locale = LocaleContextHolder.getLocale();
        String message = messageSource.getMessage("web.error.notfound", null, locale);
        model.addAttribute("message", message);
        return "error/404";
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public String handleInvalidTransition(InvalidStatusTransitionException ex,
                                          HttpServletRequest request,
                                          RedirectAttributes redirectAttributes) {
        log.warn("Invalid status transition (web): {}", ex.getMessage());
        Locale locale = LocaleContextHolder.getLocale();
        String message = messageSource.getMessage("web.error.invalid.transition", null, locale);
        redirectAttributes.addFlashAttribute("errorMessage", message);
        String referer = request.getHeader("Referer");
        return "redirect:" + (referer != null ? referer : "/tasks");
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public String handleOptimisticLock(ObjectOptimisticLockingFailureException ex,
                                       HttpServletRequest request,
                                       RedirectAttributes redirectAttributes) {
        log.warn("Optimistic lock conflict (web): {}", ex.getMessage());
        Locale locale = LocaleContextHolder.getLocale();
        String message = messageSource.getMessage("web.error.optimistic.lock", null, locale);
        redirectAttributes.addFlashAttribute("errorMessage", message);
        String referer = request.getHeader("Referer");
        return "redirect:" + (referer != null ? referer : "/tasks");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public String handleAccessDenied(AccessDeniedException ex,
                                      RedirectAttributes redirectAttributes) {
        log.warn("Access denied (web): {}", ex.getMessage());
        redirectAttributes.addFlashAttribute("errorMessage", "このリソースへのアクセス権がありません。");
        return "redirect:/tasks";
    }

    @ExceptionHandler(Exception.class)
    public String handleUnexpected(Exception ex, Model model) {
        log.error("Unexpected error (web)", ex);
        return "error/500";
    }
}
