//==================================================
// FILE: /app/src/main/java/co/neluvo/papa/MainActivity.kt
// VER : 1.01-15
//==================================================
package co.neluvo.papa

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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

    // ジャンプ先ファイル名を保持する変数
    var targetDailyFilename: String? = null
    private var viewPager: ViewPager2? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val crashLog = CrashHandler.getSavedCrashLog(this)
        if (!crashLog.isNullOrEmpty()) {
            showRawErrorScreen("⚠️ 前回の未捕捉クラッシュログ", crashLog)
            return
        }

        try {
            setContentView(R.layout.activity_main)

            val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
            viewPager = findViewById(R.id.viewPager)

            viewPager?.adapter = object : FragmentStateAdapter(this) {
                override fun getItemCount(): Int = tabTitles.size

                override fun createFragment(position: Int): Fragment {
                    return try {
                        when (position) {
                            0 -> HomeFragment()
                            1 -> LogFragment()
                            2 -> DailyFragment()
                            3 -> WeeklyFragment()
                            4 -> MonthlyFragment()
                            else -> SimpleFragment.newInstance("${tabTitles[position]}\n(${AppVersion.getFullVersionInfo()})")
                        }
                    } catch (e: Throwable) {
                        SimpleFragment.newInstance("エラー (${tabTitles[position]}): ${e.localizedMessage}")
                    }
                }
            }

            TabLayoutMediator(tabLayout, viewPager!!) { tab, position ->
                tab.text = tabTitles[position]
            }.attach()

        } catch (e: Throwable) {
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            showRawErrorScreen("⚠️ 起動時レイアウト/テーマエラー", sw.toString())
        }
    }

    // グラフタップ時に呼ばれ、日次タブ(index=2)へ強制移動するメソッド
    fun jumpToDailyTab(filename: String) {
        targetDailyFilename = filename
        viewPager?.currentItem = 2
    }

    private fun showRawErrorScreen(title: String, detailMessage: String) {
        val fullLogText = "${AppVersion.getFullVersionInfo()}\n\n$detailMessage"
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
        val btnLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 16)
        }
        val btnCopy = Button(this).apply {
            text = "📋 ログをコピー"
            setOnClickListener { copyToClipboard(fullLogText) }
        }
        val btnClear = Button(this).apply {
            text = "ログ消去して再起動"
            setOnClickListener {
                CrashHandler.clearCrashLog(this@MainActivity)
                recreate()
            }
        }
        btnLayout.addView(btnCopy)
        btnLayout.addView(btnClear)
        val tvMsg = TextView(this).apply {
            text = "$fullLogText\n\n(※ここをタップしてコピー)"
            textSize = 12f
            setTextColor(Color.DKGRAY)
            setPadding(0, 16, 0, 0)
            setOnClickListener { copyToClipboard(fullLogText) }
        }
        layout.addView(tvTitle)
        layout.addView(btnLayout)
        layout.addView(tvMsg)
        scrollView.addView(layout)
        setContentView(scrollView)
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Crash Log", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "ログをコピーしました", Toast.LENGTH_SHORT).show()
    }
}
