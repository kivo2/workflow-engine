rootProject.name = "workflow-engine"

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

include(
    "common",
    "orchestrator",
    "inventory-worker",
    "payment-worker",
    "order-worker",
)
