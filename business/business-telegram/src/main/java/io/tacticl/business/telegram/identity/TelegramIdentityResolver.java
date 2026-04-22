package io.tacticl.business.telegram.identity;

import io.tacticl.data.telegram.repository.TelegramLinkRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class TelegramIdentityResolver {

    private final TelegramLinkRepository linkRepo;

    public TelegramIdentityResolver(TelegramLinkRepository linkRepo) {
        this.linkRepo = linkRepo;
    }

    public Optional<String> resolveByChatId(long telegramUserId) {
        return linkRepo.findByChatId(telegramUserId).map(l -> l.getUserId());
    }
}
