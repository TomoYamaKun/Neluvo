//==================================================
// FILE: /app/src/main/java/co/neluvo/papa/WeeklyFragment.kt
// VER : 1.01-14
//==================================================
package co.neluvo.papa

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class WeeklyFragment : Fragment() {

    private lateinit var dbHelper: AmplitudeDbHelper
    private var trendGraphWeekly: TrendGraphView? = null
    private var tvWeeklyAverage: TextView? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return try {
            val pkg = requireContext().packageName
            val layoutId = resources.getIdentifier("fragment_weekly", "layout", pkg)
            if (layoutId != 0) {
                inflater.inflate(layoutId, container, false)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            dbHelper = AmplitudeDbHelper(requireContext())
            val pkg = requireContext().packageName

            val graphId = resources.getIdentifier("trendGraphWeekly", "id", pkg)
            if (graphId != 0) trendGraphWeekly = view.findViewById(graphId)

            val tvId = resources.getIdentifier("tvWeeklyAverage", "id", pkg)
            if (tvId != 0) tvWeeklyAverage = view.findViewById(tvId)

            loadWeeklyData()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadWeeklyData() {
        try {
            // 【変更】睡眠日基準（正午切り替え）のため、現在時刻から12時間を引いた時間をベースとする
            val shiftMs = 12 * 60 * 60 * 1000L
            val baseTime = System.currentTimeMillis() - shiftMs
            
            val cal = Calendar.getInstance()
            cal.timeInMillis = baseTime
            cal.add(Calendar.DAY_OF_YEAR, -6)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            
            val startTs = cal.timeInMillis + shiftMs // 検索用に実際の時間に直す
            val endTs = System.currentTimeMillis()

            val stats = dbHelper.getDailyStats(startTs, endTs)
            val statMap = stats.associateBy { it.dateStr }

            val trendDataList = mutableListOf<TrendGraphView.TrendData>()
            val dbDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val displayFormat = SimpleDateFormat("M/d", Locale.getDefault())

            var executedDays = 0
            var sumQuiet = 0f
            var sumNormal = 0f
            var sumSnore = 0f
            var sumHeavy = 0f

            for (i in 0..6) {
                val targetCal = Calendar.getInstance()
                targetCal.timeInMillis = baseTime
                targetCal.add(Calendar.DAY_OF_YEAR, -6 + i)
                val dateStr = dbDateFormat.format(targetCal.time)
                val dispStr = displayFormat.format(targetCal.time)

                val stat = statMap[dateStr]
                if (stat != null && stat.totalCount > 0) {
                    val total = stat.totalCount.toFloat()
                    val qPct = stat.quietCount / total * 100f
                    val nPct = stat.normalCount / total * 100f
                    val sPct = stat.snoreCount / total * 100f
                    val hPct = stat.heavyCount / total * 100f

                    trendDataList.add(TrendGraphView.TrendData(dispStr, true, qPct, nPct, sPct, hPct))
                    
                    executedDays++
                    sumQuiet += qPct
                    sumNormal += nPct
                    sumSnore += sPct
                    sumHeavy += hPct
                } else {
                    trendDataList.add(TrendGraphView.TrendData(dispStr, false, 0f, 0f, 0f, 0f))
                }
            }

            trendGraphWeekly?.setData(trendDataList)

            if (executedDays > 0) {
                val avgQ = (sumQuiet / executedDays).toInt()
                val avgN = (sumNormal / executedDays).toInt()
                val avgS = (sumSnore / executedDays).toInt()
                val avgH = (sumHeavy / executedDays).toInt()
                
                tvWeeklyAverage?.text = "【$executedDays 日間の平均データ】\n" +
                        "静音: $avgQ%   安眠: $avgN%\n" +
                        "いびき: $avgS%   大いびき: $avgH%"
            } else {
                tvWeeklyAverage?.text = "過去7日間に実行データがありません"
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
