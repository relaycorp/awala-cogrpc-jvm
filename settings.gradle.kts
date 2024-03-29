rootProject.name = "cogrpc"

plugins {
    id("com.gradle.enterprise") version "3.12.6"
}

gradleEnterprise {
    buildScan {
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"
        if (!System.getenv("CI").isNullOrEmpty()) {
            publishOnFailure()
        }
    }
}
