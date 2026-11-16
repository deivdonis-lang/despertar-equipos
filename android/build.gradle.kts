plugins {
    id("com.android.application") version "8.6.1" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
// Carpeta build fuera de OneDrive
allprojects {
    layout.buildDirectory.set(file("C:/Users/Deivid/android-tools/build/despertar-equipos/${project.name}"))
}
