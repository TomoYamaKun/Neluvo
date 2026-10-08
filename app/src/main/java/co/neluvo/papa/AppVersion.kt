//==================================================
// FILE: /app/src/main/java/co/neluvo/papa/AppVersion.kt
// VER : 1.01-10
//==================================================
package co.neluvo.papa

object AppVersion {
    const val VERSION_NAME = "1.01-10"
    const val VERSION_CODE = 40

    fun getFullVersionInfo(): String {
        return "v$VERSION_NAME"
    }
}
