package dev.alerix.step

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform