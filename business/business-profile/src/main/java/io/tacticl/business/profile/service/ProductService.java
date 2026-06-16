package io.tacticl.business.profile.service;

import io.strategiz.social.client.github.GitHubClient;
import io.strategiz.social.client.github.config.GitHubConfig;
import io.strategiz.social.client.github.model.GitHubRepository;
import io.tacticl.data.pipeline.entity.EntryPoint;
import io.tacticl.data.pipeline.repository.EntryPointRepository;
import io.tacticl.data.profile.entity.Product;
import io.tacticl.data.profile.entity.RepoRef;
import io.tacticl.data.profile.entity.RepoSource;
import io.tacticl.data.profile.repository.ProductRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Registers and serves {@link Product}s — user-side groupings of repos + channel bindings.
 *
 * <p>On registration a product (a) resolves/provisions its repos (creating via GitHub when asked,
 * else validating an attached URL), recording each into the per-user repo memory, and (b) upserts
 * an {@link EntryPoint} per channel binding so inbound Discord/Telegram/WEB/VOICE traffic routes to
 * the (literal) {@code "tacticl"} arbiter product against this product's primary repo.
 */
@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    /** The arbiter wire product id — stays the literal "tacticl"; products do not introduce new ones. */
    private static final String ARBITER_PRODUCT_ID = "tacticl";
    private static final String DEFAULT_PLAYBOOK = "BUG_FIX";
    private static final String KNOWLEDGE_NAMESPACE_TEMPLATE = "tacticl-{userId}";
    private static final double DEFAULT_COST_CEILING_USD = 10.0;

    private final ProductRepository productRepository;
    private final UserRepoService userRepoService;
    private final ObjectProvider<EntryPointRepository> entryPointRepository;
    private final ObjectProvider<GitHubClient> gitHubClient;
    private final ObjectProvider<GitHubConfig> gitHubConfig;

    public ProductService(ProductRepository productRepository,
                          UserRepoService userRepoService,
                          ObjectProvider<EntryPointRepository> entryPointRepository,
                          ObjectProvider<GitHubClient> gitHubClient,
                          ObjectProvider<GitHubConfig> gitHubConfig) {
        this.productRepository = productRepository;
        this.userRepoService = userRepoService;
        this.entryPointRepository = entryPointRepository;
        this.gitHubClient = gitHubClient;
        this.gitHubConfig = gitHubConfig;
    }

    /** A repo to attach to a product, or to provision fresh when {@code create} is set. */
    public record RepoSpec(String url, boolean create, String owner, String repoName, boolean isPrivate) {}

    /** A channel binding to register for a product. */
    public record ChannelSpec(String channelType, String externalKey, String label) {}

    /**
     * Register a new product: resolve its repos (create or attach), persist the product, and upsert
     * an {@link EntryPoint} per channel binding.
     */
    public Product registerProduct(String userId, String name, List<RepoSpec> repos, List<ChannelSpec> channels) {
        List<String> canonicalUrls = new ArrayList<>();

        if (repos != null) {
            for (RepoSpec spec : repos) {
                String url = resolveRepo(userId, spec);
                if (url != null && !url.isBlank() && !canonicalUrls.contains(url)) {
                    canonicalUrls.add(url);
                }
            }
        }

        Product product = Product.create(userId, name);
        product.setRepos(canonicalUrls);
        if (channels != null) {
            List<Product.ChannelBinding> bindings = new ArrayList<>();
            for (ChannelSpec spec : channels) {
                bindings.add(new Product.ChannelBinding(spec.channelType(), spec.externalKey(), spec.label()));
            }
            product.setChannels(bindings);
        }
        Product saved = productRepository.save(product);
        log.info("Registered product id={} user={} repos={} channels={}",
                 saved.getId(), userId, canonicalUrls.size(),
                 saved.getChannels() == null ? 0 : saved.getChannels().size());

        upsertEntryPoints(userId, channels, canonicalUrls);
        return saved;
    }

    /**
     * Resolve a single repo spec to its canonical URL, registering it into the per-user repo memory.
     * When {@code create} is set the repo is provisioned via GitHub (best-effort, requires the
     * github client/config beans); otherwise an attached URL is validated/canonicalised.
     */
    private String resolveRepo(String userId, RepoSpec spec) {
        if (spec == null) {
            return null;
        }
        if (spec.create()) {
            GitHubClient client = gitHubClient.getIfAvailable();
            GitHubConfig config = gitHubConfig.getIfAvailable();
            if (client == null || config == null) {
                log.warn("Cannot create repo owner={} name={} — GitHub client/config not configured",
                         spec.owner(), spec.repoName());
                return null;
            }
            GitHubRepository repo = client.createRepo(
                    spec.repoName(), spec.owner(), spec.isPrivate(),
                    "Created via Tacticl onboarding", config.getAppToken());
            String url = repo.htmlUrl() != null ? repo.htmlUrl()
                    : "https://github.com/" + repo.fullName();
            userRepoService.registerRepoUse(userId, url, RepoSource.CREATED);
            log.info("Provisioned repo {} for user {}", url, userId);
            return url;
        }
        Optional<RepoRef> parsed = RepoRef.parse(spec.url());
        if (parsed.isEmpty()) {
            log.warn("Skipping repo — not a parseable GitHub URL: {}", spec.url());
            return null;
        }
        String canonicalUrl = parsed.get().canonicalUrl();
        userRepoService.registerRepoUse(userId, canonicalUrl, RepoSource.ATTACHED);
        return canonicalUrl;
    }

    /** Upsert one {@link EntryPoint} per channel binding (no-op if the registry bean is absent). */
    private void upsertEntryPoints(String userId, List<ChannelSpec> channels, List<String> canonicalUrls) {
        if (channels == null || channels.isEmpty()) {
            return;
        }
        EntryPointRepository repo = entryPointRepository.getIfAvailable();
        if (repo == null) {
            log.debug("EntryPointRepository unavailable — skipping {} channel binding(s)", channels.size());
            return;
        }
        String primaryRepoUrl = canonicalUrls.isEmpty() ? "" : canonicalUrls.get(0);
        for (ChannelSpec spec : channels) {
            Optional<EntryPoint> existing =
                    repo.findByChannelAndExternalKeyAndIsActiveTrue(spec.channelType(), spec.externalKey());
            if (existing.isPresent()) {
                log.debug("EntryPoint already exists channel={} externalKey={}",
                          spec.channelType(), spec.externalKey());
                continue;
            }
            EntryPoint ep = EntryPoint.create(
                    spec.channelType(),
                    spec.externalKey(),
                    ARBITER_PRODUCT_ID,
                    primaryRepoUrl,
                    DEFAULT_PLAYBOOK,
                    KNOWLEDGE_NAMESPACE_TEMPLATE.replace("{userId}", userId),
                    Set.of(userId),
                    DEFAULT_COST_CEILING_USD,
                    "",
                    false);
            repo.save(ep);
            log.info("Created EntryPoint channel={} externalKey={} repo={}",
                     spec.channelType(), spec.externalKey(), primaryRepoUrl);
        }
    }

    /** A user's active products, newest first. */
    public List<Product> listProducts(String userId) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        return productRepository.findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(userId);
    }

    /** A single product scoped to its owning user. */
    public Optional<Product> getProduct(String userId, String id) {
        if (userId == null || id == null) {
            return Optional.empty();
        }
        return productRepository.findByIdAndUserId(id, userId)
                .filter(Product::isActive);
    }

    /** Soft-delete a product (isActive=false). Returns true if a product was found and deleted. */
    public boolean deleteProduct(String userId, String id) {
        Optional<Product> found = productRepository.findByIdAndUserId(id, userId);
        if (found.isEmpty()) {
            return false;
        }
        Product product = found.get();
        product.delete();
        productRepository.save(product); // updatedAt auto-set by @LastModifiedDate auditing
        log.info("Soft-deleted product id={} user={}", id, userId);
        return true;
    }
}
