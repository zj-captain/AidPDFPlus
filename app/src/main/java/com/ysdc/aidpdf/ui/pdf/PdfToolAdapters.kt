package com.ysdc.aidpdf.ui.pdf

import android.graphics.Bitmap
import android.text.format.Formatter
import android.util.LruCache
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.RecyclerView
import com.ysdc.aidpdf.R
import com.ysdc.aidpdf.data.document.LocalDocument
import com.ysdc.aidpdf.databinding.ItemPageSelectBinding
import com.ysdc.aidpdf.databinding.ItemPdfSelectBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PdfSourceAdapter(
    private val multiSelect: Boolean,
    private val onChanged: () -> Unit
) : RecyclerView.Adapter<PdfSourceAdapter.Holder>() {

    private val documents = mutableListOf<LocalDocument>()
    private val selectedPaths = linkedSetOf<String>()

    fun submit(items: List<LocalDocument>, preselectedPath: String? = null) {
        documents.clear()
        documents += items
        selectedPaths.clear()
        if (preselectedPath != null && items.any { it.path == preselectedPath }) {
            selectedPaths += preselectedPath
        }
        notifyDataSetChanged()
        onChanged()
    }

    fun selected(): List<LocalDocument> {
        return selectedPaths.mapNotNull { selectedPath ->
            documents.firstOrNull { it.path == selectedPath }
        }
    }

    fun isEmpty(): Boolean = documents.isEmpty()

    fun remove(document: LocalDocument) {
        val index = documents.indexOfFirst { it.path == document.path }
        if (index < 0) return
        documents.removeAt(index)
        selectedPaths.remove(document.path)
        notifyItemRemoved(index)
        onChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(ItemPdfSelectBinding.inflate(LayoutInflater.from(parent.context), parent, false), multiSelect)
    }

    override fun getItemCount(): Int = documents.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val document = documents[position]
        holder.bind(document, selectedPaths.indexOf(document.path) + 1) {
            toggle(documents[position], holder.itemView.context)
        }
    }

    private fun toggle(document: LocalDocument, context: android.content.Context) {
        if (multiSelect.not()) selectedPaths.clear()
        if (document.path in selectedPaths) {
            selectedPaths.remove(document.path)
        } else {
            if (multiSelect && selectedPaths.size >= MAX_SELECTED_COUNT) {
                Toast.makeText(context, context.getString(R.string.merge_max_selected, MAX_SELECTED_COUNT), Toast.LENGTH_SHORT).show()
                return
            }
            selectedPaths += document.path
        }
        notifyDataSetChanged()
        onChanged()
    }

    class Holder(
        private val binding: ItemPdfSelectBinding,
        private val multiSelect: Boolean
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(document: LocalDocument, selectedOrder: Int, onClick: () -> Unit) {
            val selected = selectedOrder > 0
            binding.titleText.text = document.name
            binding.subtitleText.text = buildMeta(document)
            binding.root.isSelected = selected
            binding.selectBadge.setBackgroundResource(
                if (selected) R.drawable.bg_pdf_badge_active else R.drawable.bg_pdf_badge_normal
            )
            binding.selectOrder.isVisible = multiSelect
            binding.selectOrder.text = if (multiSelect && selected) selectedOrder.toString() else ""
            binding.selectCheck.isVisible = multiSelect.not() && selected
            binding.root.setOnClickListener { onClick() }
        }

        private fun buildMeta(document: LocalDocument): String {
            val context = binding.root.context
            val date = SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date(document.modifiedAt))
            return "$date        ${Formatter.formatFileSize(context, document.size)}"
        }
    }

    companion object {
        const val MAX_SELECTED_COUNT = 20
    }
}

class PdfPageAdapter(
    private val lifecycleScope: LifecycleCoroutineScope,
    private val onChanged: () -> Unit
) : RecyclerView.Adapter<PdfPageAdapter.Holder>() {

    private var pageCount = 0
    private var documentPath = ""
    private var documentPassword = ""
    private val selectedPages = mutableListOf<Int>()
    private val bitmapCache = object : LruCache<Int, Bitmap>(12 * 1024) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount / 1024
    }

    fun submit(count: Int, path: String, password: String) {
        pageCount = count
        documentPath = path
        documentPassword = password
        selectedPages.clear()
        bitmapCache.evictAll()
        notifyDataSetChanged()
        onChanged()
    }

    fun selected(): List<Int> = selectedPages.toList()

    fun totalCount(): Int = pageCount

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(ItemPageSelectBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun getItemCount(): Int = pageCount

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val order = selectedPages.indexOf(position) + 1
        holder.renderJob?.cancel()
        holder.bind(position, order, bitmapCache.get(position)) {
            val currentPosition = holder.bindingAdapterPosition
            if (currentPosition == RecyclerView.NO_POSITION) return@bind
            if (selectedPages.contains(currentPosition)) {
                selectedPages.remove(currentPosition)
            } else {
                selectedPages.add(currentPosition)
            }
            notifyDataSetChanged()
            onChanged()
        }
        if (bitmapCache.get(position) == null && documentPath.isNotBlank()) {
            holder.renderJob = lifecycleScope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    PdfFastAccess.renderPage(
                        context = holder.itemView.context,
                        path = documentPath,
                        password = documentPassword,
                        pageIndex = position,
                        width = THUMBNAIL_WIDTH,
                        height = THUMBNAIL_HEIGHT
                    )
                }
                if (bitmap != null) {
                    bitmapCache.put(position, bitmap)
                    if (holder.bindingAdapterPosition == position) {
                        holder.showPreview(bitmap)
                    }
                }
            }
        }
    }

    override fun onViewRecycled(holder: Holder) {
        holder.renderJob?.cancel()
        holder.renderJob = null
        super.onViewRecycled(holder)
    }

    class Holder(private val binding: ItemPageSelectBinding) : RecyclerView.ViewHolder(binding.root) {
        var renderJob: Job? = null

        fun bind(index: Int, selectedOrder: Int, preview: Bitmap?, onClick: () -> Unit) {
            binding.pageText.text = binding.root.context.getString(R.string.page_number, index + 1)
            binding.root.isSelected = selectedOrder > 0
            binding.selectedOrder.isVisible = selectedOrder > 0
            binding.selectedOrder.text = if (selectedOrder > 0) selectedOrder.toString() else ""
            if (preview == null) {
                binding.pagePreview.setImageResource(R.drawable.img_pdf)
                binding.pagePreview.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            } else {
                showPreview(preview)
            }
            binding.root.setOnClickListener { onClick() }
        }

        fun showPreview(bitmap: Bitmap) {
            binding.pagePreview.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            binding.pagePreview.setImageBitmap(bitmap)
        }
    }

    companion object {
        private const val THUMBNAIL_WIDTH = 160
        private const val THUMBNAIL_HEIGHT = 220
    }
}
