package com.ysdc.aidpdf.ui.document

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.ysdc.aidpdf.R
import com.ysdc.aidpdf.data.document.DocumentKind
import com.ysdc.aidpdf.data.document.HomeSection
import com.ysdc.aidpdf.data.document.LocalDocument
import com.ysdc.aidpdf.databinding.PageDocumentListBinding

class DocumentPagerAdapter(
    private val onOpen: (LocalDocument) -> Unit,
    private val onFavorite: (LocalDocument) -> Unit,
    private val onMore: (LocalDocument) -> Unit,
    private val onGrantPermission: () -> Unit
) : RecyclerView.Adapter<DocumentPagerAdapter.PageHolder>() {

    private val kinds = DocumentKind.filters
    private var section = HomeSection.HOME
    private var hasPermission = true
    private var documentsByKind: Map<DocumentKind, List<LocalDocument>> = emptyMap()
    private val boundHolders = mutableMapOf<DocumentKind, PageHolder>()

    init {
        setHasStableIds(true)
    }

    fun submitSection(
        section: HomeSection,
        documents: List<LocalDocument>,
        hasPermission: Boolean
    ) {
        val groupedDocuments = documents.groupBy { it.kind }
        val nextDocumentsByKind = kinds.associateWith { kind ->
            groupedDocuments[kind].orEmpty()
        }
        if (
            this.section == section &&
            this.hasPermission == hasPermission &&
            documentsByKind == nextDocumentsByKind
        ) {
            return
        }
        val previousSection = this.section
        val previousPermission = this.hasPermission
        val previousDocuments = documentsByKind
        this.section = section
        this.hasPermission = hasPermission
        documentsByKind = nextDocumentsByKind

        kinds.forEach { kind ->
            if (
                previousSection != section ||
                previousPermission != hasPermission ||
                previousDocuments[kind].orEmpty() != nextDocumentsByKind[kind].orEmpty()
            ) {
                boundHolders[kind]?.bind(
                    documents = nextDocumentsByKind[kind].orEmpty(),
                    hasPermission = hasPermission,
                    emptyCopy = emptyCopyFor(section)
                )
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
        val inflater = LayoutInflater.from(parent.context)
        return PageHolder(
            binding = PageDocumentListBinding.inflate(inflater, parent, false),
            onOpen = onOpen,
            onFavorite = onFavorite,
            onMore = onMore,
            onGrantPermission = onGrantPermission
        )
    }

    override fun getItemCount(): Int = kinds.size

    override fun getItemId(position: Int): Long = kinds[position].ordinal.toLong()

    override fun onBindViewHolder(holder: PageHolder, position: Int) {
        val kind = kinds[position]
        boundHolders[kind] = holder
        holder.bind(
            documents = documentsByKind[kind].orEmpty(),
            hasPermission = hasPermission,
            emptyCopy = emptyCopyFor(section)
        )
    }

    override fun onViewRecycled(holder: PageHolder) {
        boundHolders.entries.removeAll { it.value == holder }
        super.onViewRecycled(holder)
    }

    private fun emptyCopyFor(section: HomeSection): EmptyCopy {
        return when (section) {
            HomeSection.HOME -> EmptyCopy(section, R.string.empty_title, R.string.empty_message)
            HomeSection.RECENT -> EmptyCopy(section, R.string.empty_recent_title, R.string.empty_recent_message)
            HomeSection.FAVORITES -> EmptyCopy(section, R.string.empty_favorites_title, R.string.empty_favorites_message)
            HomeSection.SETTING -> EmptyCopy(section, R.string.empty_title, R.string.empty_message)
        }
    }

    class PageHolder(
        private val binding: PageDocumentListBinding,
        onOpen: (LocalDocument) -> Unit,
        onFavorite: (LocalDocument) -> Unit,
        onMore: (LocalDocument) -> Unit,
        onGrantPermission: () -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private val listAdapter = DocumentListAdapter(
            onOpen = onOpen,
            onFavorite = onFavorite,
            onMore = onMore
        )

        init {
            binding.documentPageList.itemAnimator = null
            binding.documentPageList.adapter = listAdapter
            binding.grantButton.setOnClickListener { onGrantPermission() }
        }

        fun bind(
            documents: List<LocalDocument>,
            hasPermission: Boolean,
            emptyCopy: EmptyCopy
        ) {
            val showList = hasPermission && documents.isNotEmpty()
            binding.documentPageList.isVisible = showList
            binding.emptyGroup.isVisible = hasPermission && documents.isEmpty()
            binding.permissionGroup.isVisible = hasPermission.not()
            binding.emptyTitle.setText(emptyCopy.titleRes)
            binding.emptyMessage.setText(emptyCopy.messageRes)
            listAdapter.submitDocuments(documents, emptyCopy.section)
        }
    }

    data class EmptyCopy(
        val section: HomeSection,
        @StringRes val titleRes: Int,
        @StringRes val messageRes: Int
    )
}
