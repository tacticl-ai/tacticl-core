rootProject.name = "tacticl-core"

include(
    // Application (assembler)
    "application-api",

    // Service layer (REST controllers)
    "service:service-agent",
    "service:service-spark",
    "service:service-checkpoint",
    "service:service-repo",
    "service:service-token",

    // Business layer (domain logic)
    "business:business-agent",

    // Data layer (entities + repositories)

    // Client layer (external API clients)
    "client:client-brave-search",
    "client:client-github",
    "client:client-google",
    "client:client-jina",
    "client:client-whisper",
)

include(":data:data-connections")
include(":business:business-connections")
include(":service:service-connections")
include(":data:data-sparks")
include(":business:business-sparks")
include(":service:service-sparks")

include(":data:data-pipeline")
include(":client:client-ai-arbiter")
include(":business:business-pipeline")
include(":service:service-pipeline")

include(":data:data-conversation")
include(":business:business-conversation")
include(":service:service-conversation")

include(":data:data-profile")
include(":business:business-profile")
include(":service:service-profile")

include(":data:data-token")
include(":business:business-token")
// :service:service-token already declared in the top-level include(...) block above

include(":client:client-telegram")
include(":data:data-telegram")
include(":business:business-telegram")
include(":service:service-telegram")

include(":client:client-discord")
include(":data:data-discord")
include(":business:business-discord")
include(":service:service-discord")

include(":data:data-cloud-orchestrator")
include(":client:client-deepgram")
include(":client:client-elevenlabs")
include(":business:business-cloud-orchestrator")
include(":business:business-voice")
include(":service:service-voice")
include(":service:service-cloud-orchestrator")

// Version catalog auto-discovered from gradle/libs.versions.toml
