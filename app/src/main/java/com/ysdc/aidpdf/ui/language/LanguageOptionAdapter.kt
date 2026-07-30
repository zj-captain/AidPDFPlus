package com.ysdc.aidpdf.ui.language

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ysdc.aidpdf.R
import com.ysdc.aidpdf.databinding.ItemLanguageOptionBinding

class LanguageOptionAdapter(
    private val languages: List<AppLanguage>,
    initialTag: String
) : RecyclerView.Adapter<LanguageOptionAdapter.LanguageViewHolder>() {

    var selectedTag: String = languages.firstOrNull { it.tag == initialTag }?.tag
        ?: languages.first().tag
        private set

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LanguageViewHolder {
        val binding = ItemLanguageOptionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return LanguageViewHolder(binding, ::select)
    }

    override fun onBindViewHolder(holder: LanguageViewHolder, position: Int) {
        val language = languages[position]
        holder.bind(language, language.tag == selectedTag)
    }

    override fun getItemCount(): Int = languages.size

    private fun select(position: Int) {
        val language = languages.getOrNull(position) ?: return
        if (language.tag == selectedTag) return
        val previous = languages.indexOfFirst { it.tag == selectedTag }
        selectedTag = language.tag
        if (previous >= 0) notifyItemChanged(previous)
        notifyItemChanged(position)
    }

    class LanguageViewHolder(
        private val binding: ItemLanguageOptionBinding,
        private val onSelect: (Int) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(language: AppLanguage, selected: Boolean) {
            binding.languageName.text = language.label
            binding.checkIcon.setImageResource(
                if (selected) R.drawable.ic_language_checked else R.drawable.ic_language_unchecked
            )
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onSelect(position)
                }
            }
        }
    }
}
