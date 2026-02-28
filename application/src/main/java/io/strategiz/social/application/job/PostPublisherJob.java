package io.strategiz.social.application.job;

import io.strategiz.social.business.publish.PostContent;
import io.strategiz.social.business.publish.PublishResult;
import io.strategiz.social.business.publish.SocialMediaProvider;
import io.strategiz.social.business.publish.SocialMediaProviderFactory;
import io.strategiz.social.data.entity.PostState;
import io.strategiz.social.data.entity.SocialIntegration;
import io.strategiz.social.data.entity.SocialPost;
import io.strategiz.social.data.repository.SocialIntegrationRepository;
import io.strategiz.social.data.repository.SocialPostRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job that polls for QUEUED posts whose publishDate has arrived and publishes
 * them via the appropriate SocialMediaProvider.
 */
@Component
public class PostPublisherJob {

	private static final Logger log = LoggerFactory.getLogger(PostPublisherJob.class);

	private static final int MAX_RETRIES = 3;

	private final SocialPostRepository postRepository;

	private final SocialIntegrationRepository integrationRepository;

	private final SocialMediaProviderFactory providerFactory;

	public PostPublisherJob(SocialPostRepository postRepository,
			SocialIntegrationRepository integrationRepository, SocialMediaProviderFactory providerFactory) {
		this.postRepository = postRepository;
		this.integrationRepository = integrationRepository;
		this.providerFactory = providerFactory;
	}

	/** Run every 60 seconds to check for posts due for publishing. */
	@Scheduled(fixedDelay = 60000)
	public void publishDuePosts() {
		List<SocialPost> duePosts = postRepository.findDueForPublishing(Instant.now());
		if (duePosts.isEmpty()) {
			return;
		}

		log.info("Found {} posts due for publishing", duePosts.size());

		for (SocialPost post : duePosts) {
			try {
				publishPost(post);
			}
			catch (Exception e) {
				log.error("Failed to publish post {}: {}", post.getId(), e.getMessage(), e);
				handleFailure(post, e.getMessage());
			}
		}
	}

	private void publishPost(SocialPost post) {
		// Transition to PUBLISHING
		post.setState(PostState.PUBLISHING);
		post.setUpdatedAt(Instant.now());
		postRepository.save(post, post.getId());

		// Get the first target integration
		if (post.getTargetIntegrationIds().isEmpty()) {
			handleFailure(post, "No target integrations specified");
			return;
		}

		String integrationId = post.getTargetIntegrationIds().get(0);
		Optional<SocialIntegration> integration = integrationRepository.findById(post.getUserId(), integrationId);
		if (integration.isEmpty()) {
			handleFailure(post, "Integration not found: " + integrationId);
			return;
		}

		SocialIntegration integ = integration.get();
		if (integ.isDisabled() || integ.getAccessToken() == null) {
			handleFailure(post, "Integration disabled or missing token");
			return;
		}

		// Publish via provider
		SocialMediaProvider provider = providerFactory.getProvider(integ.getPlatform());
		PostContent content = new PostContent(post.getContent());
		content.setMediaUrls(post.getMediaUrls());

		PublishResult result = provider.publish(content, integ.getAccessToken());

		if (result.isSuccess()) {
			post.setState(PostState.PUBLISHED);
			post.setPublishedPostId(result.getPlatformPostId());
			post.setPublishedUrl(result.getPlatformPostUrl());
			post.setUpdatedAt(Instant.now());
			postRepository.save(post, post.getId());
			log.info("Published post {} to {}", post.getId(), integ.getPlatform());
		}
		else {
			handleFailure(post, result.getErrorMessage());
		}
	}

	private void handleFailure(SocialPost post, String errorMessage) {
		post.setRetryCount(post.getRetryCount() + 1);
		post.setLastError(errorMessage);
		post.setUpdatedAt(Instant.now());

		if (post.getRetryCount() >= MAX_RETRIES) {
			post.setState(PostState.FAILED);
			log.warn("Post {} failed after {} retries: {}", post.getId(), MAX_RETRIES, errorMessage);
		}
		else {
			post.setState(PostState.QUEUED);
			log.info("Post {} retry {}/{}: {}", post.getId(), post.getRetryCount(), MAX_RETRIES, errorMessage);
		}

		postRepository.save(post, post.getId());
	}

}
