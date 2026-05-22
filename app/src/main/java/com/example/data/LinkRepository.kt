package com.example.data

import kotlinx.coroutines.flow.Flow

class LinkRepository(
    private val dao: SavedLinkDao,
    private val categoryDao: CategoryDao
) {
    val allLinks: Flow<List<SavedLink>> = dao.getAllLinksFlow()

    // Category operations
    val allCategories: Flow<List<Category>> = categoryDao.getAllCategoriesFlow()

    suspend fun insertCategory(category: Category): Long {
        return categoryDao.insertCategory(category)
    }

    suspend fun updateCategory(category: Category) {
        categoryDao.updateCategory(category)
    }

    suspend fun updateCategories(categories: List<Category>) {
        categoryDao.updateCategories(categories)
    }

    suspend fun deleteCategory(category: Category) {
        categoryDao.deleteCategory(category)
    }

    suspend fun getCategoryById(id: Int): Category? {
        return categoryDao.getCategoryById(id)
    }

    suspend fun deleteCategoryOnly(category: Category) {
        dao.clearCategoryForLinks(category.id)
        categoryDao.deleteCategory(category)
    }

    suspend fun deleteCategoryAndAllContent(category: Category) {
        dao.deleteLinksByCategoryId(category.id)
        categoryDao.deleteCategory(category)
    }

    // Link operations
    suspend fun insertLink(link: SavedLink): Long {
        return dao.insertLink(link)
    }

    suspend fun updateLink(link: SavedLink) {
        dao.updateLink(link)
    }

    suspend fun deleteLink(link: SavedLink) {
        dao.deleteLink(link)
    }

    suspend fun getLinkById(id: Int): SavedLink? {
        return dao.getLinkById(id)
    }

    fun getLinksByCategoryIdFlow(categoryId: Int): Flow<List<SavedLink>> {
        return dao.getLinksByCategoryIdFlow(categoryId)
    }
}
