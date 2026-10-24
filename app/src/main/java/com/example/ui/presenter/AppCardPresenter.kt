package com.example.ui.presenter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.leanback.widget.Presenter
import com.example.R
import com.example.model.AppInfo

/**
 * Modern AuraTV Glass Presenter für App-Kacheln auf dem Android TV.
 * Rendert einzelne abgerundete Kacheln (Tiles) mit Fokusrahmen.
 */
class AppCardPresenter(
    private val onLongClickAction: ((AppInfo, View) -> Unit)? = null
) : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.view_app_card, parent, false)
        view.isFocusable = true
        view.isFocusableInTouchMode = true

        view.setOnFocusChangeListener { v, hasFocus ->
            animateFocus(v, hasFocus)
        }

        return CardViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val holder = viewHolder as CardViewHolder
        val appInfo = item as? AppInfo ?: return
        val context = holder.view.context

        // Kachel-Hintergrund & Fokus-Selektor
        if (appInfo.isFavorite) {
            holder.view.background = ContextCompat.getDrawable(context, R.drawable.bg_favorite_card_selector)
        } else {
            holder.view.background = ContextCompat.getDrawable(context, R.drawable.bg_card_selector)
        }

        val displayDrawable = appInfo.getDisplayDrawable()

        if (appInfo.bannerDrawable != null) {
            // TV-Banner / Marken-Logo füllt die Kachel aus (z. B. Netflix, YouTube, DAZN)
            holder.ivBanner.visibility = View.VISIBLE
            holder.layoutContent.visibility = View.GONE
            holder.ivBanner.setImageDrawable(appInfo.bannerDrawable)
        } else {
            // Standard App-Icon mit Textbeschriftung innerhalb der Kachel
            holder.ivBanner.visibility = View.GONE
            holder.layoutContent.visibility = View.VISIBLE

            if (displayDrawable != null) {
                holder.ivIcon.setImageDrawable(displayDrawable)
            } else {
                holder.ivIcon.setImageResource(R.drawable.ic_app_default)
            }
            holder.tvTitle.text = appInfo.label
        }

        // Lang-Klick für das App-Kontextmenü registrieren
        holder.view.setOnLongClickListener { v ->
            onLongClickAction?.invoke(appInfo, v)
            true
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val holder = viewHolder as CardViewHolder
        holder.ivBanner.setImageDrawable(null)
        holder.ivIcon.setImageDrawable(null)
        holder.view.setOnLongClickListener(null)
    }

    /**
     * Fokussteuerung ohne Skalierung/Zoom, sodass nur der Rahmen über den Selector erscheint.
     */
    private fun animateFocus(view: View, hasFocus: Boolean) {
        view.scaleX = 1.0f
        view.scaleY = 1.0f
        view.translationZ = if (hasFocus) 4f else 0f
    }

    private class CardViewHolder(rootView: View) : ViewHolder(rootView) {
        val ivBanner: ImageView = rootView.findViewById(R.id.ivCardBanner)
        val layoutContent: LinearLayout = rootView.findViewById(R.id.layoutCardContent)
        val ivIcon: ImageView = rootView.findViewById(R.id.ivCardIcon)
        val tvTitle: TextView = rootView.findViewById(R.id.tvCardTitle)
    }
}
