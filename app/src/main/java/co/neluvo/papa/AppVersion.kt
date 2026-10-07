//app/src/main/java/co/neluvo/papa/AppVersion.kt
//ver 1.00-21
package co.neluvo.papa

object AppVersion {
    const val VERSION_NAME = "1.00-21"
    const val VERSION_CODE = 21
    const val APP_NAME = "Neluvo Papa"

    fun getFullVersionInfo(): String {
        return "$APP_NAME v$VERSION_NAME (Build $VERSION_CODE)"
    }
}
