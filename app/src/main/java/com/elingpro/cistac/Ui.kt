package com.elingpro.cistac

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton

/** Mali pomoćnici za pravljenje ekrana iz koda (bez XML layout-a). */
fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun Context.vbox(pad: Int = 16): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(dp(pad), dp(pad), dp(pad), dp(pad))
}

fun Context.hbox(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
}

fun Context.text(s: String, sizeSp: Float = 15f, bold: Boolean = false): TextView =
    TextView(this).apply {
        text = s
        textSize = sizeSp
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(4), 0, dp(4))
    }

fun Context.button(s: String, onClick: () -> Unit): MaterialButton =
    MaterialButton(this).apply {
        text = s
        isAllCaps = false
        setOnClickListener { onClick() }
    }

fun lpMatchWrap() = LinearLayout.LayoutParams(
    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
)

fun lpWeight(w: Float = 1f) = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w)

/** Jedan red u listi. */
data class Row(
    val key: String,
    var title: String,
    var subtitle: String,
    var icon: Drawable? = null,
    var checked: Boolean = false,
    var checkable: Boolean = true,
    var sortValue: Long = 0,
    var extra: Any? = null
)

/** Lista sa čekboksovima. Klik na red menja čekiranje, dugi klik poziva [onLong]. */
class CheckAdapter(
    private val ctx: Context,
    var rows: MutableList<Row> = mutableListOf(),
    private val onChange: () -> Unit = {},
    private val onLong: (Row) -> Unit = {}
) : BaseAdapter() {

    override fun getCount() = rows.size
    override fun getItem(position: Int) = rows[position]
    override fun getItemId(position: Int) = position.toLong()

    private class Holder(
        val root: LinearLayout,
        val cb: CheckBox,
        val icon: ImageView,
        val title: TextView,
        val sub: TextView
    )

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val h: Holder = if (convertView == null) {
            val root = ctx.hbox().apply { setPadding(ctx.dp(8), ctx.dp(8), ctx.dp(8), ctx.dp(8)) }
            val cb = CheckBox(ctx).apply { isClickable = false; isFocusable = false }
            val icon = ImageView(ctx)
            val texts = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            val title = ctx.text("", 15f, true).apply { setPadding(0, 0, 0, 0) }
            val sub = ctx.text("", 12f).apply { setPadding(0, 0, 0, 0); alpha = 0.75f }
            texts.addView(title); texts.addView(sub)
            root.addView(cb)
            root.addView(icon, LinearLayout.LayoutParams(ctx.dp(40), ctx.dp(40)).apply {
                marginEnd = ctx.dp(10)
            })
            root.addView(texts, lpWeight())
            Holder(root, cb, icon, title, sub).also { root.tag = it }
        } else convertView.tag as Holder

        val r = rows[position]
        h.title.text = r.title
        h.sub.text = r.subtitle
        h.cb.visibility = if (r.checkable) View.VISIBLE else View.INVISIBLE
        h.cb.isChecked = r.checked
        if (r.icon != null) {
            h.icon.visibility = View.VISIBLE
            h.icon.setImageDrawable(r.icon)
        } else h.icon.visibility = View.GONE

        h.root.setOnClickListener {
            if (r.checkable) {
                r.checked = !r.checked
                h.cb.isChecked = r.checked
                onChange()
            }
        }
        h.root.setOnLongClickListener { onLong(r); true }
        return h.root
    }

    fun checkedRows() = rows.filter { it.checked && it.checkable }
    fun setAll(v: Boolean) {
        rows.forEach { if (it.checkable) it.checked = v }
        notifyDataSetChanged(); onChange()
    }
}
