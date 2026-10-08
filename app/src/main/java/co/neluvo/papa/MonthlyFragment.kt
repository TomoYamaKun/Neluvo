//==================================================
// FILE: /app/src/main/java/co/neluvo/papa/MonthlyFragment.kt
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

class MonthlyFragment : Fragment() {

    private lateinit var dbHelper: AmplitudeDbHelper
    private var trendGraphMonthly: TrendGraphView? = null
    private var tvMonthlyAverage: TextView? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return try {
            val pkg = requireContext().packageName
            val layoutId = resources.getIdentifier("fragment_monthly", "layout", pkg)
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

            val graphId = resources.getIdentifier("trendGraphMonthly", "id", pkg)
            if (graphId != 0) trendGraphMonthly = view.findViewById(graphId)

            val tvId = resources.getIdentifier("tvMonthlyAverage", "id", pkg)
            if (tvId != 0) tvMonthlyAverage = view.findViewById(tvId)

            loadMonthlyData()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadMonthlyData() {
        try {
            val daysCount = 30
            // 【変更】睡眠日基準（正午切り替え）
            val shiftMs = 12 * 60 * 60 * 1000L
            val baseTime = System.currentTimeMillis() - shiftMs
            
            val cal = Calendar.getInstance()
            cal.timeInMillis = baseTime
            cal.add(Calendar.DAY_OF_YEAR, -(daysCount - 1))
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            
            val startTs = cal.timeInMillis + shiftMs
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

            for (i in 0 until daysCount) {
                val targetCal = Calendar.getInstance()
                targetCal.timeInMillis = baseTime
                targetCal.add(Calendar.DAY_OF_YEAR, -(daysCount - 1) + i)
                val dateStr = dbDateFormat.format(targetCal.time)
                
                val dispStr = if (i % 5 == 0 || i == daysCount - 1) displayFormat.format(targetCal.time) else ""

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

            trendGraphMonthly?.setData(trendDataList)

            if (executedDays > 0) {
                val avgQ = (sumQuiet / executedDays).toInt()
                val avgN = (sumNormal / executedDays).toInt()
                val avgS = (sumSnore / executedDays).toInt()
                val avgH = (sumHeavy / executedDays).toInt()
                
                tvMonthlyAverage?.text = "【過去30日間 ($executedDays 日実行) の平均データ】\n" +
                        "静音: $avgQ%   安眠: $avgN%\n" +
                        "いびき: $avgS%   大いびき: $avgH%"
            } else {
                tvMonthlyAverage?.text = "過去30日間に実行データがありません"
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
