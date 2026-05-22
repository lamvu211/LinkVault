package com.example.data

import android.content.Context
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

// Retrofit interfaces for Google Sheets API v4 and Google Drive API v3
interface GoogleSheetsService {
    @POST("v4/spreadsheets")
    suspend fun createSpreadsheet(
        @Header("Authorization") authorizationHeader: String,
        @Body request: CreateSpreadsheetRequest
    ): Response<CreateSpreadsheetResponse>

    @PUT("v4/spreadsheets/{spreadsheetId}/values/{range}")
    suspend fun updateValues(
        @Header("Authorization") authorizationHeader: String,
        @Path("spreadsheetId") spreadsheetId: String,
        @Path("range") range: String,
        @Query("valueInputOption") valueInputOption: String = "USER_ENTERED",
        @Body request: UpdateValuesRequest
    ): Response<UpdateValuesResponse>
}

interface GoogleDriveService {
    @POST("v3/files/{fileId}/permissions")
    suspend fun createPermission(
        @Header("Authorization") authorizationHeader: String,
        @Path("fileId") fileId: String,
        @Body request: CreatePermissionRequest
    ): Response<Unit>
}

// Request & Response DTOs
data class CreateSpreadsheetRequest(
    val properties: SpreadsheetProperties
)

data class SpreadsheetProperties(
    val title: String
)

data class CreateSpreadsheetResponse(
    val spreadsheetId: String,
    val spreadsheetUrl: String
)

data class UpdateValuesRequest(
    val range: String,
    val majorDimension: String = "ROWS",
    val values: List<List<String>>
)

data class UpdateValuesResponse(
    val spreadsheetId: String,
    val updatedRange: String,
    val updatedRows: Int
)

data class CreatePermissionRequest(
    val role: String,
    val type: String
)

object GoogleWorkspaceSyncManager {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .build()

    private val sheetsRetrofit = Retrofit.Builder()
        .baseUrl("https://sheets.googleapis.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val driveRetrofit = Retrofit.Builder()
        .baseUrl("https://www.googleapis.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val sheetsService: GoogleSheetsService = sheetsRetrofit.create(GoogleSheetsService::class.java)
    val driveService: GoogleDriveService = driveRetrofit.create(GoogleDriveService::class.java)

    // Helper to format Row Data for exports
    fun prepareRows(links: List<SavedLink>, categories: List<Category>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        // Header
        rows.add(listOf("Link", "Note", "Category"))
        
        // Items
        links.forEach { link ->
            val categoryName = if (link.categoryId == 0) {
                "General"
            } else {
                categories.find { it.id == link.categoryId }?.name ?: "General"
            }
            rows.add(listOf(link.url, link.note, categoryName))
        }
        return rows
    }

    // High level method to perform real sync
    suspend fun syncToDriveReal(
        token: String,
        links: List<SavedLink>,
        categories: List<Category>
    ): SyncResult {
        try {
            val authHeader = "Bearer $token"
            
            // 1. Create spreadsheet
            Log.d("LinkVaultSync", "Creating LinkVault spreadsheet...")
            val createResponse = sheetsService.createSpreadsheet(
                authorizationHeader = authHeader,
                request = CreateSpreadsheetRequest(SpreadsheetProperties("LinkVault"))
            )
            
            if (!createResponse.isSuccessful) {
                val errorMsg = createResponse.errorBody()?.string() ?: "Unknown error"
                return SyncResult.Failure("Failed to create spreadsheet: Code ${createResponse.code()}. Details: $errorMsg")
            }
            
            val spreadsheet = createResponse.body()
                ?: return SyncResult.Failure("Spreadsheet creation returned empty body")
                
            val spreadsheetId = spreadsheet.spreadsheetId
            val spreadsheetUrl = spreadsheet.spreadsheetUrl
            Log.d("LinkVaultSync", "Spreadsheet created! ID: $spreadsheetId, URL: $spreadsheetUrl")
            
            // 2. Add Row Values
            val rows = prepareRows(links, categories)
            Log.d("LinkVaultSync", "Adding values to spreadsheet...")
            val updateResponse = sheetsService.updateValues(
                authorizationHeader = authHeader,
                spreadsheetId = spreadsheetId,
                range = "Sheet1!A1",
                request = UpdateValuesRequest(
                    range = "Sheet1!A1",
                    values = rows
                )
            )
            
            if (!updateResponse.isSuccessful) {
                val errorMsg = updateResponse.errorBody()?.string() ?: "Unknown error"
                return SyncResult.Failure("Failed to write sheet row data: Code ${updateResponse.code()}. Details: $errorMsg")
            }
            
            Log.d("LinkVaultSync", "Values updated successfully!")
            return SyncResult.Success(spreadsheetId, spreadsheetUrl)
            
        } catch (e: Exception) {
            e.printStackTrace()
            return SyncResult.Failure("Network or parsing error: ${e.message}")
        }
    }

    // High level method to make sheets public ("share to anyone" via Drive API v3)
    suspend fun shareFileToAnyoneReal(
        token: String,
        fileId: String
    ): ShareResult {
        try {
            val authHeader = "Bearer $token"
            Log.d("LinkVaultSync", "Updating file permissions to public...")
            val response = driveService.createPermission(
                authorizationHeader = authHeader,
                fileId = fileId,
                request = CreatePermissionRequest(
                    role = "reader",
                    type = "anyone"
                )
            )
            
            if (!response.isSuccessful) {
                val errorMsg = response.errorBody()?.string() ?: "Unknown error"
                return ShareResult.Failure("Failed to share file: Code ${response.code()}. Details: $errorMsg")
            }
            
            Log.d("LinkVaultSync", "Permissions updated successfully!")
            return ShareResult.Success
        } catch (e: Exception) {
            e.printStackTrace()
            return ShareResult.Failure("Network error updating file sharing: ${e.message}")
        }
    }
}

sealed class SyncResult {
    data class Success(val fileId: String, val sheetUrl: String) : SyncResult()
    data class Failure(val message: String) : SyncResult()
}

sealed class ShareResult {
    object Success : ShareResult()
    data class Failure(val message: String) : ShareResult()
}
