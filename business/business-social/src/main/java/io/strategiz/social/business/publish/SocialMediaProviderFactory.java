package io.strategiz.social.business.publish;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import io.strategiz.social.data.entity.PlatformType;

/**
 * Factory for resolving SocialMediaProvider implementations by platform type.
 */
@Component
public class SocialMediaProviderFactory {

	private final Map<PlatformType, SocialMediaProvider> providers;

	public SocialMediaProviderFactory(List<SocialMediaProvider> providerList) {
		this.providers = providerList.stream()
			.collect(Collectors.toMap(SocialMediaProvider::getPlatformType, Function.identity()));
	}

	public SocialMediaProvider getProvider(PlatformType platformType) {
		return Optional.ofNullable(providers.get(platformType))
			.orElseThrow(() -> new IllegalArgumentException("Unsupported platform: " + platformType));
	}

	public boolean isSupported(PlatformType platformType) {
		return providers.containsKey(platformType);
	}

}
