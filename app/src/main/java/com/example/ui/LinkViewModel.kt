package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.SavedLink
import com.example.data.LinkRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class SortOrder {
    RECENT,
    TITLE,
    DOMAIN
}

class LinkViewModel(private val repository: LinkRepository) : ViewModel() {

    // All saved links from the Database
    private val _allLinks = repository.allLinks

    // Search query filter State
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    // Selected tag filter State ("All" represents no tag filter)
    private val _selectedTag = MutableStateFlow<String?>("All")
    val selectedTag = _selectedTag.asStateFlow()

    // Sort order State
    private val _sortOrder = MutableStateFlow(SortOrder.RECENT)
    val sortOrder = _sortOrder.asStateFlow()

    // UI State combining searches, sorts, and tag selections
    val uiState: StateFlow<List<SavedLink>> = combine(
        _allLinks,
        _searchQuery,
        _selectedTag,
        _sortOrder
    ) { links, query, tag, sort ->
        links.filter { link ->
            // Filter by search query (checks URL, note/caption, title, tags)
            val matchesQuery = query.isBlank() || 
                link.title.contains(query, ignoreCase = true) ||
                link.url.contains(query, ignoreCase = true) ||
                link.note.contains(query, ignoreCase = true) ||
                link.tags.contains(query, ignoreCase = true)

            // Filter by tag selection
            val matchesTag = tag == "All" || tag == null || 
                link.tags.split(",")
                    .map { it.trim().lowercase() }
                    .contains(tag.lowercase())

            matchesQuery && matchesTag
        }.sortedWith { a, b ->
            when (sort) {
                SortOrder.RECENT -> b.timestamp.compareTo(a.timestamp)
                SortOrder.TITLE -> a.title.lowercase().compareTo(b.title.lowercase())
                SortOrder.DOMAIN -> {
                    val domA = getDomainName(a.url).lowercase()
                    val domB = getDomainName(b.url).lowercase()
                    domA.compareTo(domB)
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Reactive database configuration of dynamically extracted tags
    val availableTags: StateFlow<List<String>> = _allLinks.map { links ->
        val defaultTags = listOf("Reading", "Work", "Tech", "Travel")
        val savedTags = links.flatMap { link ->
            link.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
        (defaultTags + savedTags).distinctBy { it.lowercase() }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = listOf("Reading", "Work", "Tech", "Travel")
    )

    // Handle incoming shared links to pop up UI in the workflow
    private val _sharedTextToProcess = MutableStateFlow<String?>(null)
    val sharedTextToProcess = _sharedTextToProcess.asStateFlow()

    fun setSharedText(text: String?) {
        _sharedTextToProcess.value = text
    }

    fun clearSharedText() {
        _sharedTextToProcess.value = null
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectTag(tag: String?) {
        _selectedTag.value = tag
    }

    fun updateSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    // Categories reactive exposed flow
    val allCategories: StateFlow<List<com.example.data.Category>> = repository.allCategories
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun saveCategory(name: String, logo: String) {
        viewModelScope.launch {
            repository.insertCategory(com.example.data.Category(name = name, logo = logo))
        }
    }

    fun updateCategory(category: com.example.data.Category) {
        viewModelScope.launch {
            repository.updateCategory(category)
        }
    }

    fun deleteCategoryOnly(category: com.example.data.Category) {
        viewModelScope.launch {
            repository.deleteCategoryOnly(category)
        }
    }

    fun deleteCategoryAndAllContent(category: com.example.data.Category) {
        viewModelScope.launch {
            repository.deleteCategoryAndAllContent(category)
        }
    }

    fun getLinksByCategoryIdFlow(categoryId: Int): Flow<List<SavedLink>> {
        return repository.getLinksByCategoryIdFlow(categoryId)
    }

    fun saveLink(title: String, url: String, note: String, tags: List<String>, categoryId: Int = 0) {
        viewModelScope.launch {
            val linkTitle = title.trim().ifBlank { getDisplayTitle(url) }
            val tagsString = tags.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(",")
            val cleanUrl = formatUrl(url)
            repository.insertLink(
                SavedLink(
                    url = cleanUrl,
                    title = linkTitle,
                    note = note.trim(),
                    tags = tagsString,
                    categoryId = categoryId
                )
            )
        }
    }

    fun updateLink(link: SavedLink) {
        viewModelScope.launch {
            repository.updateLink(link)
        }
    }

    fun deleteLink(link: SavedLink) {
        viewModelScope.launch {
            repository.deleteLink(link)
        }
    }

    // Helper to beautifully parse domains (e.g. facebook.com, medium.com)
    fun getDomainName(url: String): String {
        return try {
            val clean = url.trim()
            val uri = java.net.URI(if (clean.contains("://")) clean else "https://$clean")
            val domain = uri.host ?: ""
            val raw = if (domain.startsWith("www.")) domain.substring(4) else domain
            if (raw.isBlank()) "web" else raw
        } catch (e: Exception) {
            "web"
        }
    }

    private fun getDisplayTitle(url: String): String {
        val domain = getDomainName(url)
        return "Saved Link from $domain"
    }

    private fun formatUrl(url: String): String {
        val trimmed = url.trim()
        return if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            "https://$trimmed"
        } else {
            trimmed
        }
    }
}

class LinkViewModelFactory(private val repository: LinkRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LinkViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LinkViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
