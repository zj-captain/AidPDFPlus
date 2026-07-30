package com.ysdc.aidpdf.ui.document

import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ysdc.aidpdf.R
import com.ysdc.aidpdf.data.document.HomeSection
import com.ysdc.aidpdf.data.document.LocalDocument
import com.ysdc.aidpdf.databinding.ItemDocumentBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DocumentListAdapter(
    private val onOpen: (LocalDocument) -> Unit,
    private val onFavorite: (LocalDocument) -> Unit,
    private val onMore: (LocalDocument) -> Unit
) : ListAdapter<LocalDocument, DocumentListAdapter.DocumentHolder>(DocumentDiff) {

    private var section = HomeSection.HOME

    init {
        setHasStableIds(true)
    }

    fun submitDocuments(documents: List<LocalDocument>, section: HomeSection) {
        val sectionChanged = this.section != section
        this.section = section
        if (currentList == documents) {
            if (sectionChanged && itemCount > 0) {
                notifyItemRangeChanged(0, itemCount)
            }
            return
        }
        submitList(documents.toList())
    }

    override fun getItemId(position: Int): Long = getItem(position).path.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DocumentHolder {
        val inflater = LayoutInflater.from(parent.context)
        return DocumentHolder(ItemDocumentBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: DocumentHolder, position: Int) {
        holder.bind(getItem(position), section, onOpen, onFavorite, onMore)
    }

    class DocumentHolder(
        private val binding: ItemDocumentBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            document: LocalDocument,
            section: HomeSection,
            onOpen: (LocalDocument) -> Unit,
            onFavorite: (LocalDocument) -> Unit,
            onMore: (LocalDocument) -> Unit
        ) {
            val showMore = section != HomeSection.FAVORITES
            binding.fileIcon.setImageResource(document.kind.iconRes)
            binding.fileName.text = document.name
            binding.fileMeta.text = documentMeta(document)
            binding.favoriteButton.setImageResource(
                if (document.favorite) R.drawable.img_star_yellow else R.drawable.img_star_gray
            )
            binding.moreButton.isVisible = showMore
            binding.root.setOnClickListener { onOpen(document) }
            binding.favoriteButton.setOnClickListener { onFavorite(document) }
            binding.moreButton.setOnClickListener {
                if (showMore) onMore(document)
            }
        }

        private fun documentMeta(document: LocalDocument): String {
            val context = binding.root.context
            val date = SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date(document.modifiedAt))
            val size = Formatter.formatFileSize(context, document.size)
            return "$date        $size"
        }
    }

    private object DocumentDiff : DiffUtil.ItemCallback<LocalDocument>() {
        override fun areItemsTheSame(oldItem: LocalDocument, newItem: LocalDocument): Boolean {
            return oldItem.path == newItem.path
        }

        override fun areContentsTheSame(oldItem: LocalDocument, newItem: LocalDocument): Boolean {
            return oldItem == newItem
        }
    }
}
