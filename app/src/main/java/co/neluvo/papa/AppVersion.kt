//==================================================
// FILE: /app/src/main/java/co/neluvo/papa/AppVersion.kt
// VER : 1.01-16
//==================================================
package co.neluvo.papa

object AppVersion {
    const val VERSION_NAME = "1.01-16"
    const val VERSION_CODE = 46

    fun getFullVersionInfo(): String {
        return "v$VERSION_NAME"
    }
}
