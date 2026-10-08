//==================================================
// FILE: /app/src/main/java/co/neluvo/papa/AppVersion.kt
// VER : 1.01-05
//==================================================
package co.neluvo.papa

object AppVersion {
    const val VERSION_NAME = "1.01-05"
    const val VERSION_CODE = 35

    fun getFullVersionInfo(): String {
        return "v$VERSION_NAME"
    }
}
