package io.tacticl.service.discord.controller;

import io.cidadel.framework.authorization.annotation.AuthUser;
import io.cidadel.framework.authorization.annotation.RequireAuth;
import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.cidadel.service.base.controller.BaseController;
import io.tacticl.business.discord.DiscordUserLinker;
import io.tacticl.data.discord.entity.DiscordAccountLink;
import io.tacticl.service.discord.dto.DiscordLinkStatusDto;
import io.tacticl.service.discord.dto.DiscordRedeemRequestDto;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Web-side of the Discord account link. The user obtains a one-time code by running {@code /link}
 * in Discord (handled by {@code DiscordInteractionDispatcher}); here they redeem it against their
 * authenticated Tacticl account, which binds their Discord snowflake so it can trigger pipelines.
 *
 * <p>Mirrors {@code TelegramLinkController}. Dormant unless {@code tacticl.discord.enabled=true}.
 */
@RestController
@RequestMapping("/v1/discord")
@ConditionalOnProperty(name = "tacticl.discord.enabled", havingValue = "true")
public class DiscordLinkController extends BaseController {

    private final DiscordUserLinker linker;

    public DiscordLinkController(DiscordUserLinker linker) {
        this.linker = linker;
    }

    @Override
    protected String getModuleName() {
        return "discord-link";
    }

    @PostMapping("/link/redeem")
    @RequireAuth
    public ResponseEntity<DiscordLinkStatusDto> redeem(
            @AuthUser AuthenticatedUser user,
            @RequestBody DiscordRedeemRequestDto request) {
        String token = request == null ? null : request.token();
        return linker.redeemToken(token, user.getUserId())
            .map(link -> ResponseEntity.ok(toDto(link)))
            .orElseGet(() -> ResponseEntity.status(HttpStatus.BAD_REQUEST).build());
    }

    @GetMapping("/status")
    @RequireAuth
    public ResponseEntity<DiscordLinkStatusDto> status(@AuthUser AuthenticatedUser user) {
        return ResponseEntity.ok(linker.linkedAccount(user.getUserId())
            .map(this::toDto)
            .orElse(new DiscordLinkStatusDto(false, null, null, null)));
    }

    @DeleteMapping("/link")
    @RequireAuth
    public ResponseEntity<Void> unlink(@AuthUser AuthenticatedUser user) {
        linker.unlink(user.getUserId());
        return ResponseEntity.noContent().build();
    }

    private DiscordLinkStatusDto toDto(DiscordAccountLink link) {
        return new DiscordLinkStatusDto(
            link.isLinked(),
            link.getDiscordUserId(),
            link.getDiscordUsername(),
            link.getLinkedAt() != null ? link.getLinkedAt().toString() : null);
    }
}
