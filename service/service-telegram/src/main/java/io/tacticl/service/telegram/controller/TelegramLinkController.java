package io.tacticl.service.telegram.controller;

import io.cidadel.service.base.controller.BaseController;
import io.tacticl.business.telegram.TelegramUserLinker;
import io.tacticl.service.telegram.dto.LinkTokenResponseDto;
import io.tacticl.service.telegram.dto.LinkedChatDto;
import io.tacticl.service.telegram.dto.TelegramStatusDto;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/telegram")
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class TelegramLinkController extends BaseController {

    private final TelegramUserLinker linker;

    public TelegramLinkController(TelegramUserLinker linker) {
        this.linker = linker;
    }

    @Override
    protected String getModuleName() {
        return "telegram-link";
    }

    @PostMapping("/link")
    public ResponseEntity<LinkTokenResponseDto> issueLink(
            @RequestHeader("X-User-Id") String userId) {
        var issued = linker.issueLinkToken(userId);
        return ResponseEntity.ok(new LinkTokenResponseDto(issued.token(), issued.botDeepLinkUrl()));
    }

    @GetMapping("/status")
    public ResponseEntity<TelegramStatusDto> status(
            @RequestHeader("X-User-Id") String userId) {
        List<LinkedChatDto> linked = linker.linkedChats(userId).stream()
                .map(l -> new LinkedChatDto(
                        l.getChatId(),
                        l.getUsername(),
                        l.getLinkedAt() != null ? l.getLinkedAt().toString() : null))
                .toList();
        return ResponseEntity.ok(new TelegramStatusDto(linked));
    }

    @DeleteMapping("/link/{chatId}")
    public ResponseEntity<Void> unlink(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable long chatId) {
        linker.unlink(userId, chatId);
        return ResponseEntity.noContent().build();
    }
}
