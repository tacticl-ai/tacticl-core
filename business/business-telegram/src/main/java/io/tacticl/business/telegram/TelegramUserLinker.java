package io.tacticl.business.telegram;

import io.tacticl.client.telegram.config.TelegramConfig;
import io.tacticl.data.telegram.entity.TelegramLink;
import io.tacticl.data.telegram.entity.TelegramLinkToken;
import io.tacticl.data.telegram.repository.TelegramLinkRepository;
import io.tacticl.data.telegram.repository.TelegramLinkTokenRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
public class TelegramUserLinker {

    public record IssuedLink(String token, String botDeepLinkUrl) {}

    private final TelegramLinkRepository linkRepo;
    private final TelegramLinkTokenRepository tokenRepo;
    private final TelegramConfig config;
    private final SecureRandom random = new SecureRandom();

    public TelegramUserLinker(
            TelegramLinkRepository linkRepo,
            TelegramLinkTokenRepository tokenRepo,
            TelegramConfig config) {
        this.linkRepo = linkRepo;
        this.tokenRepo = tokenRepo;
        this.config = config;
    }

    public IssuedLink issueLinkToken(String userId) {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokenRepo.save(TelegramLinkToken.create(token, userId, config.getLinkTokenTtlMinutes()));
        String url = "https://t.me/" + config.getBotUsername() + "?start=" + token;
        return new IssuedLink(token, url);
    }

    public Optional<String> redeemToken(String token, long chatId, String username, String firstName) {
        Optional<TelegramLinkToken> stored = tokenRepo.findByToken(token);
        if (stored.isEmpty()) return Optional.empty();

        TelegramLinkToken t = stored.get();
        if (t.isConsumed() || t.isExpired()) return Optional.empty();

        Optional<TelegramLink> existing = linkRepo.findByChatId(chatId);
        if (existing.isPresent() && !existing.get().getUserId().equals(t.getUserId())) {
            return Optional.empty();
        }

        t.consume();
        tokenRepo.save(t);

        TelegramLink link = existing.orElseGet(() ->
                TelegramLink.create(t.getUserId(), chatId, username, firstName));
        link.setActive(true);
        linkRepo.save(link);

        return Optional.of(t.getUserId());
    }

    public void unlink(String userId, long chatId) {
        linkRepo.findByUserIdAndChatId(userId, chatId).ifPresent(link -> {
            link.delete();
            linkRepo.save(link);
        });
    }

    public List<TelegramLink> linkedChats(String userId) {
        return linkRepo.findByUserIdAndIsActiveTrue(userId);
    }
}
