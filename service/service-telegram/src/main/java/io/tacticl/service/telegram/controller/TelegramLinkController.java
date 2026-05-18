package io.tacticl.service.telegram.controller;

import io.cidadel.framework.authorization.annotation.AuthUser;
import io.cidadel.framework.authorization.annotation.RequireAuth;
import io.cidadel.framework.authorization.context.AuthenticatedUser;
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
    @RequireAuth
    public ResponseEntity<LinkTokenResponseDto> issueLink(
            @AuthUser AuthenticatedUser user) {
        var issued = linker.issueLinkToken(user.getUserId());
        return ResponseEntity.ok(new LinkTokenResponseDto(issued.token(), issued.botDeepLinkUrl()));
    }

    @GetMapping("/status")
    @RequireAuth
    public ResponseEntity<TelegramStatusDto> status(
            @AuthUser AuthenticatedUser user) {
        List<LinkedChatDto> linked = linker.linkedChats(user.getUserId()).stream()
                .map(l -> new LinkedChatDto(
                        l.getChatId(),
                        l.getUsername(),
                        l.getLinkedAt() != null ? l.getLinkedAt().toString() : null))
                .toList();
        return ResponseEntity.ok(new TelegramStatusDto(linked));
    }

    @DeleteMapping("/link/{chatId}")
    @RequireAuth
    public ResponseEntity<Void> unlink(
            @AuthUser AuthenticatedUser user,
            @PathVariable long chatId) {
        linker.unlink(user.getUserId(), chatId);
        return ResponseEntity.noContent().build();
    }
}
