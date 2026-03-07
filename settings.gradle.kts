rootProject.name = "tacticl-core"

include(
    // Application (assembler)
    "application-api",

    // Service layer (REST controllers)
    "service:service-agent",
    "service:service-spark",
    "service:service-checkpoint",
    "service:service-social",
    "service:service-repo",
    "service:service-token",

    // Business layer (domain logic)
    "business:business-agent",
    "business:business-browser",
    "business:business-social",

    // Data layer (entities + repositories)
    "data:data-browser",
    "data:data-social",

    // Client layer (external API clients)
    "client:client-brave-search",
    "client:client-gcs",
    "client:client-github",
    "client:client-google",
    "client:client-instagram",
    "client:client-jina",
    "client:client-linkedin",
    "client:client-siliconflow",
    "client:client-twitter",
)

// Version catalog auto-discovered from gradle/libs.versions.toml
