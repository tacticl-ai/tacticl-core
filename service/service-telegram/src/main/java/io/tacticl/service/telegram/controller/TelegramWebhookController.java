package io.tacticl.service.telegram.controller;

import io.cidadel.service.base.controller.BaseController;
import io.tacticl.business.telegram.TelegramDispatchService;
import io.tacticl.business.telegram.TelegramWebhookSecurity;
import io.tacticl.client.telegram.dto.Update;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/telegram/webhook")
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class TelegramWebhookController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(TelegramWebhookController.class);

    private final TelegramWebhookSecurity security;
    private final TelegramDispatchService dispatch;

    public TelegramWebhookController(
            TelegramWebhookSecurity security,
            TelegramDispatchService dispatch) {
        this.security = security;
        this.dispatch = dispatch;
    }

    @Override
    protected String getModuleName() {
        return "telegram-webhook";
    }

    @PostMapping
    public ResponseEntity<Void> webhook(
            @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String secret,
            @RequestBody Update update) {
        if (!security.isValidSignature(secret)) {
            logger.warn("Rejected Telegram webhook with invalid signature");
            return ResponseEntity.status(401).build();
        }
        try {
            dispatch.handle(update);
        } catch (Exception e) {
            logger.error("Telegram dispatch failed for update_id={}", update.update_id(), e);
        }
        return ResponseEntity.ok().build();
    }
}
