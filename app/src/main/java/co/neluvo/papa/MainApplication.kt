//app/src/main/java/co/neluvo/papa/MainApplication.kt
//ver 1.00-12
package co.neluvo.papa

import android.app.Application

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // アプリ全域の未補獲クラッシュを自動検出＆ログファイル保存
        CrashHandler.init(this)
    }
}
