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
)

include(":data:data-connections")
include(":business:business-connections")
include(":service:service-connections")

// Version catalog auto-discovered from gradle/libs.versions.toml
