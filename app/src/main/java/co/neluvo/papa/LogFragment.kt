//app/src/main/java/co/neluvo/papa/LogFragment.kt
//ver 1.00-27
package co.neluvo.papa

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView
    private val fileList = mutableListOf<File>()
    private lateinit var adapter: LogAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_log, container, false)
        
        recyclerView = view.findViewById(R.id.recyclerViewLogs)
        tvEmpty = view.findViewById(R.id.tvEmpty)
        
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = LogAdapter()
        recyclerView.adapter = adapter
        
        return view
    }

    override fun onResume() {
        super.onResume()
        loadFiles()
    }

    private fun loadFiles() {
        val dir = requireContext().getExternalFilesDir(null)
        val files = dir?.listFiles { _, name ->
            name.startsWith("neluvo_") && name.endsWith(".m4a")
        }?.sortedByDescending { it.lastModified() } ?: emptyList()

        fileList.clear()
        fileList.addAll(files)

        if (fileList.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            tvEmpty.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
        
        adapter.notifyDataSetChanged()
    }

    private fun deleteFile(file: File, position: Int) {
        val fileName = file.name
        if (file.delete()) {
            // DB上の波形データ履歴も完全に削除
            try {
                val dbHelper = AmplitudeDbHelper(requireContext())
                dbHelper.writableDatabase.delete("amplitudes", "file_name = ?", arrayOf(fileName))
            } catch (e: Exception) {
                e.printStackTrace()
            }

            fileList.removeAt(position)
            adapter.notifyItemRemoved(position)
            Toast.makeText(requireContext(), "削除しました", Toast.LENGTH_SHORT).show()
            
            if (fileList.isEmpty()) {
                tvEmpty.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            }
        } else {
            Toast.makeText(requireContext(), "削除に失敗しました", Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatSize(sizeBytes: Long): String {
        if (sizeBytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(sizeBytes.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(Locale.getDefault(), "%.1f %s", sizeBytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    inner class LogAdapter : RecyclerView.Adapter<LogAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvFileName: TextView = view.findViewById(R.id.tvFileName)
            val tvFileInfo: TextView = view.findViewById(R.id.tvFileInfo)
            val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_log_file, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val file = fileList[position]
            holder.tvFileName.text = file.name

            val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
            val dateStr = sdf.format(Date(file.lastModified()))
            val sizeStr = formatSize(file.length())
            holder.tvFileInfo.text = "$dateStr | $sizeStr"

            holder.btnDelete.setOnClickListener {
                AlertDialog.Builder(requireContext())
                    .setTitle("ファイルの削除")
                    .setMessage("${file.name}\n\nこの録音データと波形履歴を完全に削除しますか？")
                    .setPositiveButton("削除") { _, _ ->
                        deleteFile(file, holder.adapterPosition)
                    }
                    .setNegativeButton("キャンセル", null)
                    .show()
            }
        }

        override fun getItemCount(): Int = fileList.size
    }
}
