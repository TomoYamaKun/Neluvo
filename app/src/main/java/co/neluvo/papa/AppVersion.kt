//app/src/main/java/co/neluvo/papa/AppVersion.kt
//ver 1.00-19
package co.neluvo.papa

object AppVersion {
    const val VERSION_NAME = "1.00-19"
    const val VERSION_CODE = 19
    const val APP_NAME = "Neluvo Papa"

    fun getFullVersionInfo(): String {
        return "$APP_NAME v$VERSION_NAME (Build $VERSION_CODE)"
    }
}
