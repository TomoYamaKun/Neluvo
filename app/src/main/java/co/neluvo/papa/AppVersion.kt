//==================================================
// FILE: /app/src/main/java/co/neluvo/papa/AppVersion.kt
// VER : 1.01-31
//==================================================
package co.neluvo.papa

object AppVersion {
    const val VERSION_NAME = "1.01-31"
    const val VERSION_CODE = 60

    fun getFullVersionInfo(): String {
        return "v$VERSION_NAME"
    }
}
