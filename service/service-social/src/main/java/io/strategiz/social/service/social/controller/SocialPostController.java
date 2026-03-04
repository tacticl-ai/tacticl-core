package io.strategiz.social.service.social.controller;

import io.cidadel.framework.authorization.annotation.AuthUser;
import io.cidadel.framework.authorization.annotation.RequireAuth;
import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.strategiz.social.data.entity.PostState;
import io.strategiz.social.data.entity.SocialPost;
import io.strategiz.social.data.repository.SocialPostRepository;
import io.strategiz.social.service.social.dto.CreatePostRequest;
import io.strategiz.social.service.social.dto.PostResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import com.google.cloud.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for social media posts CRUD. */
@RestController
@RequestMapping("/api/social/posts")
@Tag(name = "Social Posts", description = "Create, schedule, and manage social media posts")
public class SocialPostController {

	private static final Logger log = LoggerFactory.getLogger(SocialPostController.class);

	private final SocialPostRepository postRepository;

	public SocialPostController(SocialPostRepository postRepository) {
		this.postRepository = postRepository;
	}

	@PostMapping
	@RequireAuth
	@Operation(summary = "Create a new post", description = "Create a draft or scheduled post")
	public ResponseEntity<PostResponse> createPost(@Valid @RequestBody CreatePostRequest request,
			@AuthUser AuthenticatedUser user) {
		SocialPost post = new SocialPost();
		post.setId(UUID.randomUUID().toString());
		post.setUserId(user.getUserId());
		post.setContent(request.getContent());
		post.setMediaUrls(request.getMediaUrls());
		post.setTargetIntegrationIds(request.getTargetIntegrationIds());
		post.setCreatedDate(Timestamp.now());
		post.setModifiedDate(Timestamp.now());

		if (request.getPublishDate() != null) {
			post.setPublishDate(request.getPublishDate());
			post.setState(PostState.QUEUED);
		}
		else {
			post.setState(PostState.DRAFT);
		}

		postRepository.save(post, user.getUserId());
		log.info("Created post {} for user {}", post.getId(), user.getUserId());

		return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(post));
	}

	@GetMapping
	@RequireAuth
	@Operation(summary = "List posts", description = "List posts for the authenticated user")
	public ResponseEntity<List<PostResponse>> listPosts(@AuthUser AuthenticatedUser user,
			@RequestParam(required = false) String state) {
		List<SocialPost> posts;
		if (state != null) {
			PostState postState = PostState.valueOf(state.toUpperCase());
			posts = postRepository.findByUserIdAndState(user.getUserId(), postState);
		}
		else {
			posts = postRepository.findAll().stream()
				.filter(p -> user.getUserId().equals(p.getUserId()))
				.toList();
		}

		return ResponseEntity.ok(posts.stream().map(this::toResponse).toList());
	}

	@GetMapping("/{postId}")
	@RequireAuth
	@Operation(summary = "Get a post", description = "Get a single post by ID")
	public ResponseEntity<PostResponse> getPost(@PathVariable String postId, @AuthUser AuthenticatedUser user) {
		Optional<SocialPost> post = postRepository.findById(postId);
		if (post.isEmpty() || !post.get().getUserId().equals(user.getUserId())) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(toResponse(post.get()));
	}

	@DeleteMapping("/{postId}")
	@RequireAuth
	@Operation(summary = "Cancel a post", description = "Cancel a scheduled or draft post")
	public ResponseEntity<Void> cancelPost(@PathVariable String postId, @AuthUser AuthenticatedUser user) {
		Optional<SocialPost> post = postRepository.findById(postId);
		if (post.isEmpty() || !post.get().getUserId().equals(user.getUserId())) {
			return ResponseEntity.notFound().build();
		}

		SocialPost p = post.get();
		if (p.getState() == PostState.PUBLISHED || p.getState() == PostState.PUBLISHING) {
			return ResponseEntity.badRequest().build();
		}

		p.setState(PostState.CANCELLED);
		p.setModifiedDate(Timestamp.now());
		postRepository.save(p, user.getUserId());
		log.info("Cancelled post {} for user {}", postId, user.getUserId());

		return ResponseEntity.noContent().build();
	}

	private PostResponse toResponse(SocialPost post) {
		PostResponse response = new PostResponse();
		response.setId(post.getId());
		response.setContent(post.getContent());
		response.setMediaUrls(post.getMediaUrls());
		response.setTargetIntegrationIds(post.getTargetIntegrationIds());
		response.setState(post.getState().name());
		response.setPublishDate(post.getPublishDate());
		response.setPublishedPostId(post.getPublishedPostId());
		response.setPublishedUrl(post.getPublishedUrl());
		response.setCreatedAt(post.getCreatedDate() != null ? post.getCreatedDate().toDate().toInstant() : null);
		return response;
	}

}
