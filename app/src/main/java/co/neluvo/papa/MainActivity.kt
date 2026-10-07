//app/src/main/java/co/neluvo/papa/MainActivity.kt
//ver 1.00-16
package co.neluvo.papa

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import java.io.PrintWriter
import java.io.StringWriter

class MainActivity : AppCompatActivity() {

    private val tabTitles = arrayOf(
        "スタート",
        "ログ",
        "日次",
        "週間",
        "月間",
        "バックアップ"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. 直前のクラッシュログが存在する場合は、コードのみで全画面表示
        val crashLog = CrashHandler.getSavedCrashLog(this)
        if (!crashLog.isNullOrEmpty()) {
            showRawErrorScreen("⚠️ 前回の未捕捉クラッシュログ", crashLog)
            return
        }

        // 2. メイン画面の生成
        try {
            setContentView(R.layout.activity_main)

            val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
            val viewPager = findViewById<ViewPager2>(R.id.viewPager)

            viewPager.adapter = object : FragmentStateAdapter(this) {
                override fun getItemCount(): Int = tabTitles.size

                override fun createFragment(position: Int): Fragment {
                    return try {
                        when (position) {
                            0 -> HomeFragment()
                            2 -> DailyFragment()
                            else -> SimpleFragment.newInstance("${tabTitles[position]}\n(${AppVersion.getFullVersionInfo()})")
                        }
                    } catch (e: Throwable) {
                        SimpleFragment.newInstance("エラー (${tabTitles[position]}): ${e.localizedMessage}")
                    }
                }
            }

            TabLayoutMediator(tabLayout, viewPager) { tab, position ->
                tab.text = tabTitles[position]
            }.attach()

        } catch (e: Throwable) {
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            showRawErrorScreen("⚠️ 起動時レイアウト/テーマエラー", sw.toString())
        }
    }

    /**
     * XMLレイアウトやテーマに一切依存せず、画面に全画面エラーを表示する緊急UI
     */
    private fun showRawErrorScreen(title: String, detailMessage: String) {
        val scrollView = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val tvTitle = TextView(this).apply {
            text = title
            textSize = 20f
            setTextColor(Color.RED)
            setPadding(0, 0, 0, 16)
        }

        val btnClear = Button(this).apply {
            text = "ログを消去して通常起動を試す"
            setOnClickListener {
                CrashHandler.clearCrashLog(this@MainActivity)
                recreate()
            }
        }

        val tvMsg = TextView(this).apply {
            text = "${AppVersion.getFullVersionInfo()}\n\n$detailMessage"
            textSize = 12f
            setTextColor(Color.DKGRAY)
            setPadding(0, 24, 0, 0)
        }

        layout.addView(tvTitle)
        layout.addView(btnClear)
        layout.addView(tvMsg)
        scrollView.addView(layout)
        setContentView(scrollView)
    }
}
