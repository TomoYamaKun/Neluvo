//app/src/main/java/co/neluvo/papa/SimpleFragment.kt
//ver 1.00-05
package co.neluvo.papa

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

class SimpleFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val title = arguments?.getString(ARG_TITLE) ?: "画面"
        val textView = TextView(requireContext()).apply {
            text = title
            textSize = 20f
            gravity = Gravity.CENTER
        }
        return textView
    }

    companion object {
        private const val ARG_TITLE = "title"

        fun newInstance(title: String): SimpleFragment {
            val fragment = SimpleFragment()
            val args = Bundle().apply {
                putString(ARG_TITLE, title)
            }
            fragment.arguments = args
            return fragment
        }
    }
}
