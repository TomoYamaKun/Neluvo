//==================================================
// FILE: /app/src/main/java/co/neluvo/papa/LogFragment.kt
// VER : 1.01-31
//==================================================
package co.neluvo.papa

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogFragment : Fragment() {

    private lateinit var dbHelper: AmplitudeDbHelper
    private var recyclerViewLogs: RecyclerView? = null
    private var tvEmpty: TextView? = null
    private var fileList = mutableListOf<File>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return try {
            val pkg = requireContext().packageName
            val layoutId = resources.getIdentifier("fragment_log", "layout", pkg)
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

            val rvId = resources.getIdentifier("recyclerViewLogs", "id", pkg)
            if (rvId != 0) recyclerViewLogs = view.findViewById(rvId)

            val emptyId = resources.getIdentifier("tvEmpty", "id", pkg)
            if (emptyId != 0) tvEmpty = view.findViewById(emptyId)

            recyclerViewLogs?.layoutManager = LinearLayoutManager(requireContext())

            loadFileList()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        loadFileList()
    }

    private fun loadFileList() {
        try {
            val dir = requireContext().getExternalFilesDir(null)
            val files = dir?.listFiles { _, name -> name.endsWith(".m4a") }
            
            if (files != null && files.isNotEmpty()) {
                fileList = files.sortedByDescending { it.lastModified() }.toMutableList()
                tvEmpty?.visibility = View.GONE
                recyclerViewLogs?.visibility = View.VISIBLE

                recyclerViewLogs?.adapter = LogAdapter(fileList) { file ->
                    showFileOptionsDialog(file)
                }
            } else {
                recyclerViewLogs?.visibility = View.GONE
                tvEmpty?.visibility = View.VISIBLE
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showFileOptionsDialog(file: File) {
        val options = arrayOf("日次画面で表示・再生する", "このファイルを削除する")
        AlertDialog.Builder(requireContext())
            .setTitle(file.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        (activity as? MainActivity)?.jumpToDailyTab(file.name)
                    }
                    1 -> {
                        confirmAndDeleteFile(file)
                    }
                }
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun confirmAndDeleteFile(file: File) {
        AlertDialog.Builder(requireContext())
            .setTitle("削除の確認")
            .setMessage("${file.name} を削除しますか？\n（関連するDBの分析データも削除されます）")
            .setPositiveButton("削除する") { _, _ ->
                try {
                    dbHelper.deleteLogsForFile(file.name)
                    if (file.exists()) file.delete()
                    
                    Toast.makeText(requireContext(), "ファイルを削除しました", Toast.LENGTH_SHORT).show()
                    loadFileList()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(requireContext(), "削除に失敗しました", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    // RecyclerView用の内部アダプター
    private class LogAdapter(
        private val files: List<File>,
        private val onItemClick: (File) -> Unit
    ) : RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

        class LogViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvItem: TextView = view.findViewById(android.R.id.text1) ?: TextView(view.context)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_1, parent, false)
            return LogViewHolder(view)
        }

        override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
            val file = files[position]
            val sizeMb = String.format(Locale.US, "%.2f MB", file.length() / (1024.0 * 1024.0))
            val dateStr = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()).format(Date(file.lastModified()))
            holder.tvItem.text = "${file.name}\n($sizeMb / $dateStr)"
            holder.tvItem.setPadding(32, 24, 32, 24)
            holder.itemView.setOnClickListener { onItemClick(file) }
        }

        override fun getItemCount(): Int = files.size
    }
}
