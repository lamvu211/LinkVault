package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.GoogleAuthUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.SavedLink
import com.example.data.LinkDatabase
import com.example.data.LinkRepository
import com.example.ui.LinkViewModel
import com.example.ui.ImportedLinkDraft
import com.example.ui.LinkViewModelFactory
import com.example.ui.LoginScreen
import com.example.ui.SortOrder
import com.example.ui.theme.*
import kotlin.math.roundToInt
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawWithContent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.provider.MediaStore
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import coil.compose.rememberAsyncImagePainter
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import androidx.compose.ui.text.font.FontStyle
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope

private fun appText(language: String, vi: String, en: String): String = if (language == "en") en else vi

private fun searchPlaceholder(language: String): String = appText(language, "Bạn muốn tìm gì?", "What are you looking for?")

data class GithubReleaseInfo(
    val tagName: String,
    val htmlUrl: String,
    val apkUrl: String?
)

suspend fun fetchLatestGithubRelease(): GithubReleaseInfo = withContext(Dispatchers.IO) {
    val request = okhttp3.Request.Builder()
        .url("https://api.github.com/repos/lamvu211/LinkVault/releases/latest")
        .header("Accept", "application/vnd.github+json")
        .build()

    okhttp3.OkHttpClient().newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            throw IllegalStateException("GitHub returned ${response.code}")
        }

        val body = response.body?.string() ?: throw IllegalStateException("GitHub returned an empty response")
        val json = org.json.JSONObject(body)
        val assets = json.optJSONArray("assets")
        var apkUrl: String? = null
        if (assets != null) {
            for (index in 0 until assets.length()) {
                val asset = assets.getJSONObject(index)
                if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url")
                    break
                }
            }
        }

        GithubReleaseInfo(
            tagName = json.optString("tag_name"),
            htmlUrl = json.optString("html_url"),
            apkUrl = apkUrl
        )
    }
}

fun isRemoteVersionNewer(currentVersion: String, remoteTag: String): Boolean {
    val currentParts = currentVersion.trim().removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
    val remoteParts = remoteTag.trim().removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
    val maxSize = maxOf(currentParts.size, remoteParts.size)
    for (index in 0 until maxSize) {
        val current = currentParts.getOrElse(index) { 0 }
        val remote = remoteParts.getOrElse(index) { 0 }
        if (remote != current) return remote > current
    }
    return false
}

fun formatRelativeTime(timestamp: Long, language: String): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        diff < 0 -> appText(language, "Vừa xong", "Just now")
        seconds < 60 -> appText(language, "Vừa xong", "Just now")
        minutes < 60 -> appText(language, "${minutes} phút trước", "${minutes}m ago")
        hours < 24 -> appText(language, "${hours} giờ trước", "${hours}h ago")
        days == 1L -> appText(language, "Hôm qua", "Yesterday")
        days < 7 -> appText(language, "${days} ngày trước", "${days}d ago")
        else -> {
            val locale = if (language == "en") java.util.Locale.ENGLISH else java.util.Locale("vi", "VN")
            val sdf = java.text.SimpleDateFormat("MMM dd", locale)
            sdf.format(java.util.Date(timestamp))
        }
    }
}

class MainActivity : ComponentActivity() {

    private var activeViewModel: LinkViewModel? = null
    private var isFromShareIntent = false
    private var pendingSharedText: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Read intent data if app was started via Share Sheet
        handleSharedIntent(intent)

        setContent {
            val sharedPrefs = LocalContext.current.getSharedPreferences("linkvault_prefs", Context.MODE_PRIVATE)

            // Setup state synced with SharedPreferences
            var isLoggedIn by remember { mutableStateOf(sharedPrefs.getBoolean("is_logged_in", false)) }
            var userEmail by remember { mutableStateOf(sharedPrefs.getString("user_email", "") ?: "") }
            var themeMode by remember { mutableStateOf(sharedPrefs.getString("theme_mode", "light") ?: "light") }
            var themeSelection by remember { mutableStateOf(sharedPrefs.getString("theme_name", "denim") ?: "denim") }
            var language by remember { mutableStateOf(sharedPrefs.getString("language", "vi") ?: "vi") }

            val isSystemDark = isSystemInDarkTheme()
            val isDark = when(themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemDark
            }

            MyApplicationTheme(
                darkTheme = isDark,
                themeName = themeSelection
            ) {
                if (!isLoggedIn) {
                    LoginScreen(
                        language = language,
                        onLoginSuccess = { email ->
                            sharedPrefs.edit()
                                .putBoolean("is_logged_in", true)
                                .putString("user_email", email)
                                .apply()
                            userEmail = email
                            isLoggedIn = true
                        }
                    )
                } else {
                    val currentContext = LocalContext.current
                    val currentViewModel = remember(userEmail) {
                        val database = LinkDatabase.getDatabase(currentContext, userEmail)
                        val repository = LinkRepository(database.savedLinkDao, database.categoryDao)
                        val factory = LinkViewModelFactory(repository)
                        ViewModelProvider(this@MainActivity, factory)[userEmail, LinkViewModel::class.java].also { vm ->
                            activeViewModel = vm
                            pendingSharedText?.let {
                                vm.setSharedText(it)
                                pendingSharedText = null
                            }
                        }
                    }

                    MainAppScreen(
                        viewModel = currentViewModel,
                        userEmail = userEmail,
                        themeMode = themeMode,
                        themeSelection = themeSelection,
                        onThemeModeChange = { mode ->
                            sharedPrefs.edit().putString("theme_mode", mode).apply()
                            themeMode = mode
                        },
                        onThemeSelectionChange = { name ->
                            sharedPrefs.edit().putString("theme_name", name).apply()
                            themeSelection = name
                        },
                        language = language,
                        onLanguageChange = { selectedLanguage ->
                            sharedPrefs.edit().putString("language", selectedLanguage).apply()
                            language = selectedLanguage
                        },
                        onLogout = {
                            sharedPrefs.edit().remove("is_logged_in").remove("user_email").apply()
                            userEmail = ""
                            isLoggedIn = false
                        },
                        isFromShareIntent = isFromShareIntent,
                        onFinishActivity = {
                            finish()
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSharedIntent(intent)
    }

    private fun handleSharedIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && "text/plain" == intent.type) {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                isFromShareIntent = true
                if (activeViewModel != null) {
                    activeViewModel?.setSharedText(sharedText)
                } else {
                    pendingSharedText = sharedText
                }
            }
        }
    }
}

@Composable
fun MainAppScreen(
    viewModel: LinkViewModel,
    userEmail: String,
    themeMode: String,
    themeSelection: String,
    onThemeModeChange: (String) -> Unit,
    onThemeSelectionChange: (String) -> Unit,
    language: String,
    onLanguageChange: (String) -> Unit,
    onLogout: () -> Unit,
    isFromShareIntent: Boolean = false,
    onFinishActivity: () -> Unit = {}
) {
    val context = LocalContext.current
    val t = { vi: String, en: String -> appText(language, vi, en) }

    // Resolve local overriding color parameters seamlessly
    val colors = MaterialTheme.colorScheme
    val NaturalBg = colors.background
    val NaturalText = colors.onBackground
    val NaturalSurface = colors.surface
    val NaturalSurfaceVariant = colors.surfaceVariant
    val NaturalPrimary = colors.primary
    val NaturalSecondary = colors.secondary
    val NaturalTertiary = colors.tertiary
    val NaturalBorder = colors.outline
    val NaturalAccentChip = colors.primaryContainer

    var customPickedUri by remember { mutableStateOf<Uri?>(null) }
    var onCropFinishedCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            customPickedUri = uri
        } else {
            onCropFinishedCallback = null
        }
    }

    val triggerGalleryPicker = { onFinished: (String) -> Unit ->
        onCropFinishedCallback = onFinished
        galleryLauncher.launch("image/*")
    }

    var currentTab by remember { mutableStateOf("links") } // "links", "categories", "settings"
    var showAddManualForm by remember { mutableStateOf(false) }

    // Observe State flows from ViewModel
    val links by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val sharedTextToProcess by viewModel.sharedTextToProcess.collectAsStateWithLifecycle()
    
    // Category states
    val categories by viewModel.allCategories.collectAsStateWithLifecycle()
    var selectedCategoryId by remember { mutableStateOf(0) }
    var activeCategoryDetail by remember { mutableStateOf<com.example.data.Category?>(null) }
    var showAddCategoryForm by remember { mutableStateOf(false) }
    var categoryDraftName by remember { mutableStateOf("") }
    var categoryDraftLogo by remember { mutableStateOf("logo_01_work") }
    var showLogoLibraryDialog by remember { mutableStateOf(false) }
    var returnToNoteFormAfterCategoryCreate by remember { mutableStateOf(false) }
    var pendingCreatedCategoryName by remember { mutableStateOf<String?>(null) }

    // Keyboard Focus Requester
    val noteFocusRequester = remember { FocusRequester() }

    // Form inputs state
    var inputUrl by remember { mutableStateOf("") }
    var inputNote by remember { mutableStateOf("") }
    
    // Track editing link state
    var editingLink by remember { mutableStateOf<SavedLink?>(null) }
    var showDiscardConfirmation by remember { mutableStateOf(false) }

    // Intercept back button to support custom back trajectories
    BackHandler(enabled = currentTab != "links" && !showAddManualForm) {
        if (currentTab == "categories" && activeCategoryDetail != null) {
            activeCategoryDetail = null
        } else {
            currentTab = "links"
        }
    }

    // Intercept back button if add/edit form is showing to close/discard it gracefully
    BackHandler(enabled = showAddManualForm) {
        if (inputUrl.isNotBlank() || inputNote.isNotBlank()) {
            showDiscardConfirmation = true
        } else {
            inputUrl = ""
            inputNote = ""
            selectedCategoryId = 0
            editingLink = null
            showAddManualForm = false
            showDiscardConfirmation = false
            viewModel.clearSharedText()
            if (isFromShareIntent) {
                onFinishActivity()
            }
        }
    }

    // Sync input fields if a shared link was captured
    LaunchedEffect(sharedTextToProcess) {
        sharedTextToProcess?.let { rawText ->
            val parsedUrl = extractUrl(rawText)
            val extractedNote = if (rawText != parsedUrl) {
                val rawNote = rawText.replace(parsedUrl, "").trim().trim { it == '\n' || it == '\r' || it == ' ' }
                if (rawNote.length > 60) rawNote.take(60) else rawNote
            } else ""

            inputUrl = parsedUrl
            inputNote = extractedNote
            selectedCategoryId = 0
            editingLink = null
            showAddManualForm = true
            
            // Wait slightly for target view layout to render, then open virtual keyboard automatically
            kotlinx.coroutines.delay(400)
            noteFocusRequester.requestFocus()
        }
    }

    // Clear form utility
    val resetForm = {
        inputUrl = ""
        inputNote = ""
        selectedCategoryId = 0
        editingLink = null
        showAddManualForm = false
        showDiscardConfirmation = false
        viewModel.clearSharedText()
    }

    val handleCloseAction = {
        if (inputUrl.isNotBlank() || inputNote.isNotBlank()) {
            showDiscardConfirmation = true
        } else {
            resetForm()
            if (isFromShareIntent) {
                onFinishActivity()
            }
        }
    }

    LaunchedEffect(categories, pendingCreatedCategoryName) {
        val createdName = pendingCreatedCategoryName ?: return@LaunchedEffect
        val createdCategory = categories.firstOrNull { it.name.equals(createdName, ignoreCase = true) }
        if (createdCategory != null) {
            selectedCategoryId = createdCategory.id
            pendingCreatedCategoryName = null
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = NaturalBg,
        bottomBar = {
            // Elegant footer with tabs matching dynamic tones
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .border(1.dp, NaturalBorder, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Links tab
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clickable(
                                onClick = { currentTab = "links" },
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            )
                            .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (currentTab == "links") NaturalAccentChip else Color.Transparent)
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = "Links tab",
                                tint = if (currentTab == "links") NaturalText else NaturalSecondary.copy(alpha = 0.6f)
                            )
                        }
                        Text(
                            text = t("Liên kết", "Links"),
                            fontSize = 11.sp,
                            fontWeight = if (currentTab == "links") FontWeight.Bold else FontWeight.Medium,
                            color = if (currentTab == "links") NaturalText else NaturalSecondary.copy(alpha = 0.6f),
                            modifier = Modifier.testTag("links_tab_label")
                        )
                    }

                    // Categories tab (Grid 3x4 Layout Entry)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clickable(
                                onClick = { currentTab = "categories" },
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            )
                            .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (currentTab == "categories") NaturalAccentChip else Color.Transparent)
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.List,
                                contentDescription = "Categories tab",
                                tint = if (currentTab == "categories") NaturalText else NaturalSecondary.copy(alpha = 0.6f)
                            )
                        }
                        Text(
                            text = t("Danh mục", "Categories"),
                            fontSize = 11.sp,
                            fontWeight = if (currentTab == "categories") FontWeight.Bold else FontWeight.Medium,
                            color = if (currentTab == "categories") NaturalText else NaturalSecondary.copy(alpha = 0.6f),
                            modifier = Modifier.testTag("categories_tab_label")
                        )
                    }

                    // Settings tab
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clickable(
                                onClick = { currentTab = "settings" },
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            )
                            .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (currentTab == "settings") NaturalAccentChip else Color.Transparent)
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings tab",
                                tint = if (currentTab == "settings") NaturalText else NaturalSecondary.copy(alpha = 0.6f)
                            )
                        }
                        Text(
                            text = t("Cài đặt", "Settings"),
                            fontSize = 11.sp,
                            fontWeight = if (currentTab == "settings") FontWeight.Bold else FontWeight.Medium,
                            color = if (currentTab == "settings") NaturalText else NaturalSecondary.copy(alpha = 0.6f),
                            modifier = Modifier.testTag("settings_tab_label")
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (currentTab == "links" && !showAddManualForm) {
                FloatingActionButton(
                    onClick = { showAddManualForm = true },
                    containerColor = NaturalAccentChip,
                    contentColor = NaturalText,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .padding(bottom = 12.dp)
                        .border(1.dp, NaturalBorder, RoundedCornerShape(16.dp))
                        .testTag("add_link_fab"),
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Link Manually",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            when (currentTab) {
                "links" -> {
                    LinksDirectoryView(
                        links = links,
                        searchQuery = searchQuery,
                        sortOrder = sortOrder,
                        onUpdateSearch = { viewModel.updateSearchQuery(it) },
                        onUpdateSort = { viewModel.updateSortOrder(it) },
                        onDeleteLink = { 
                            viewModel.deleteLink(it)
                            Toast.makeText(context, t("Đã xóa liên kết.", "Deleted link successfully."), Toast.LENGTH_SHORT).show()
                        },
                        onOpenUrl = { url ->
                            val cleanUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                                "https://$url"
                            } else url
                            try {
                                val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl)).apply {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER
                                    } else {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                }
                                context.startActivity(appIntent)
                            } catch (e: Exception) {
                                try {
                                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl)).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(browserIntent)
                                } catch (ex: Exception) {
                                    Toast.makeText(context, t("Không tìm thấy ứng dụng trình duyệt.", "No browser app detected."), Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        getDomain = { viewModel.getDomainName(it) },
                        onEditLink = { link ->
                            inputUrl = link.url
                            inputNote = link.note
                            selectedCategoryId = link.categoryId
                            editingLink = link
                            showAddManualForm = true
                        },
                        onShareLink = { link -> shareLinkItem(context, link, language) },
                        language = language
                    )
                }
                "categories" -> {
                    if (activeCategoryDetail != null) {
                        CategoryDetailsView(
                            category = activeCategoryDetail!!,
                            viewModel = viewModel,
                            onPickGalleryLogo = triggerGalleryPicker,
                            onBack = { activeCategoryDetail = null },
                            onEditLink = { link ->
                                inputUrl = link.url
                                inputNote = link.note
                                selectedCategoryId = link.categoryId
                                editingLink = link
                                showAddManualForm = true
                            },
                            onDeleteLink = {
                                viewModel.deleteLink(it)
                                Toast.makeText(context, t("Đã xóa liên kết.", "Deleted link successfully."), Toast.LENGTH_SHORT).show()
                            },
                            onOpenUrl = { url ->
                                val cleanUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                                    "https://$url"
                                } else url
                                try {
                                    val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl)).apply {
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER
                                        } else {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                    }
                                    context.startActivity(appIntent)
                                } catch (e: Exception) {
                                    try {
                                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl)).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(browserIntent)
                                    } catch (ex: Exception) {
                                        Toast.makeText(context, t("Không tìm thấy ứng dụng trình duyệt.", "No browser app detected."), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onShareLink = { link -> shareLinkItem(context, link, language) },
                            language = language
                        )
                    } else {
                        CategoriesDirectoryView(
                            categories = categories,
                            language = language,
                            onCategoryClick = { activeCategoryDetail = it },
                            onAddCategory = {
                                categoryDraftName = ""
                                categoryDraftLogo = "logo_01_work"
                                showAddCategoryForm = true
                            },
                            onReorder = { from, to -> viewModel.reorderCategories(from, to) }
                        )
                    }
                }
                "settings" -> {
                    SettingsView(
                        userEmail = userEmail,
                        themeMode = themeMode,
                        themeSelection = themeSelection,
                        onThemeModeChange = onThemeModeChange,
                        onThemeSelectionChange = onThemeSelectionChange,
                        onLogout = onLogout,
                        links = links,
                        categories = categories,
                        language = language,
                        onLanguageChange = onLanguageChange,
                        onImportLinks = { rows -> viewModel.importLinks(rows) }
                    )
                }
            }

            // Overlay Detail/Edit Form Dialog (Manual FAB / Share Sheet / Swiped Edit)
            if (showAddManualForm) {
                Dialog(
                    onDismissRequest = { handleCloseAction() },
                    properties = DialogProperties(
                        usePlatformDefaultWidth = false,
                        decorFitsSystemWindows = true
                    )
                ) {
                    // Dim screen overlay background
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable(enabled = true, onClick = { handleCloseAction() }),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .imePadding()
                                .clickable(enabled = false, onClick = {}) // stop click propagation
                                .testTag("add_link_form_container"),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.surface),
                        border = BorderStroke(1.dp, NaturalBorder),
                        elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Drag bar handle at topmost
                            Box(
                                modifier = Modifier
                                    .size(36.dp, 4.dp)
                                    .clip(CircleShape)
                                    .background(NaturalSecondary.copy(alpha = 0.2f))
                                    .align(Alignment.CenterHorizontally)
                            )

                            // URL input field & Action SAVE in SAME Row
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = inputUrl,
                                        onValueChange = { inputUrl = it },
                                        placeholder = { Text("https://example.com/site", color = NaturalTertiary) },
                                        modifier = Modifier
                                            .weight(1.0f)
                                            .height(50.dp)
                                            .testTag("input_url_field"),
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = NaturalPrimary,
                                            unfocusedBorderColor = NaturalBorder,
                                            focusedContainerColor = colors.surface,
                                            unfocusedContainerColor = colors.surface,
                                            focusedTextColor = NaturalText,
                                            unfocusedTextColor = NaturalText
                                        ),
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Uri,
                                            imeAction = ImeAction.Done
                                        )
                                    )

                                    Button(
                                        onClick = {
                                            if (inputUrl.isBlank()) {
                                                Toast.makeText(context, t("Vui lòng nhập đường dẫn hợp lệ.", "Please enter a valid link."), Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            if (editingLink != null) {
                                                viewModel.updateLink(editingLink!!.copy(
                                                    url = inputUrl,
                                                    note = inputNote,
                                                    categoryId = selectedCategoryId
                                                ))
                                                Toast.makeText(context, t("Đã cập nhật ghi chú.", "Note updated."), Toast.LENGTH_SHORT).show()
                                            } else {
                                                viewModel.saveLink(
                                                    title = "",
                                                    url = inputUrl,
                                                    note = inputNote,
                                                    tags = emptyList(),
                                                    categoryId = selectedCategoryId
                                                )
                                                Toast.makeText(context, t("Đã lưu ghi chú.", "Note saved."), Toast.LENGTH_SHORT).show()
                                            }
                                            resetForm()
                                            if (isFromShareIntent) {
                                                onFinishActivity()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = NaturalSecondary),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .height(50.dp)
                                            .testTag("submit_link_button")
                                    ) {
                                        Text(
                                            text = if (editingLink != null) t("Cập nhật", "Update") else t("Lưu", "Save"),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }

                            // Category selection Dropdown
                            var dropdownExpanded by remember { mutableStateOf(false) }
                            val activeCategory = categories.find { it.id == selectedCategoryId }

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                BoxWithConstraints(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val dropdownWidth = maxWidth
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .border(1.dp, NaturalBorder, RoundedCornerShape(12.dp))
                                            .background(colors.surface)
                                            .clickable { dropdownExpanded = true }
                                            .padding(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (activeCategory != null) {
                                                CategoryLogoDisplay(
                                                    logoKey = activeCategory.logo,
                                                    size = 20.dp,
                                                    tint = NaturalPrimary
                                                )
                                                Text(
                                                    text = activeCategory.name,
                                                    fontSize = 14.sp,
                                                    color = NaturalText,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.List,
                                                    contentDescription = null,
                                                    tint = NaturalTertiary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Text(
                                                    text = t("Không có danh mục", "No category"),
                                                    fontSize = 14.sp,
                                                    color = NaturalTertiary
                                                )
                                            }
                                        }
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = t("Mở danh sách danh mục", "Expand categories"),
                                            tint = NaturalSecondary
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = dropdownExpanded,
                                        onDismissRequest = { dropdownExpanded = false },
                                        modifier = Modifier
                                            .width(dropdownWidth)
                                            .background(colors.surface)
                                            .border(1.dp, NaturalBorder, RoundedCornerShape(12.dp))
                                    ) {
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(Modifier.width(20.dp), contentAlignment = Alignment.Center) {
                                                        Icon(Icons.Default.Add, null, tint = NaturalPrimary, modifier = Modifier.size(20.dp))
                                                    }
                                                    Text(t("Tạo danh mục mới", "Create new category"), color = NaturalPrimary, fontWeight = FontWeight.SemiBold)
                                                }
                                            },
                                            onClick = {
                                                dropdownExpanded = false
                                                categoryDraftName = ""
                                                categoryDraftLogo = "logo_01_work"
                                                returnToNoteFormAfterCategoryCreate = true
                                                showAddManualForm = false
                                                showAddCategoryForm = true
                                            }
                                        )
                                        HorizontalDivider(color = NaturalBorder.copy(alpha = 0.6f))
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(Modifier.width(20.dp), contentAlignment = Alignment.Center) {
                                                        Icon(Icons.Default.List, null, tint = NaturalTertiary, modifier = Modifier.size(20.dp))
                                                    }
                                                    Text(t("Không có danh mục", "No category"), color = NaturalText)
                                                }
                                            },
                                            onClick = {
                                                selectedCategoryId = 0
                                                dropdownExpanded = false
                                            }
                                        )
                                        categories.forEach { category ->
                                            DropdownMenuItem(
                                                text = {
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Box(Modifier.width(20.dp), contentAlignment = Alignment.Center) {
                                                            CategoryLogoDisplay(logoKey = category.logo, size = 20.dp, tint = NaturalPrimary)
                                                        }
                                                        Text(category.name, color = NaturalText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    }
                                                },
                                                onClick = {
                                                    selectedCategoryId = category.id
                                                    dropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // Supplemental notes field
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                OutlinedTextField(
                                    value = inputNote,
                                    onValueChange = { if (it.length <= 60) inputNote = it },
                                    placeholder = { Text(t("Nhập ghi chú tại đây...", "Write a note here..."), color = NaturalTertiary) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(84.dp)
                                        .focusRequester(noteFocusRequester)
                                        .testTag("input_note_field"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = NaturalPrimary,
                                        unfocusedBorderColor = NaturalBorder,
                                        focusedContainerColor = colors.surface,
                                        unfocusedContainerColor = colors.surface,
                                        focusedTextColor = NaturalText,
                                        unfocusedTextColor = NaturalText
                                    ),
                                    minLines = 2,
                                    maxLines = 2,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Text,
                                        imeAction = ImeAction.Default
                                    )
                                )
                            }
                        }
                    }
                }

                // Popup Save / Discard alert if changes are pending
                if (showDiscardConfirmation) {
                    AlertDialog(
                        onDismissRequest = { showDiscardConfirmation = false },
                        title = { Text(t("Lưu thay đổi?", "Save changes?"), color = NaturalText, fontWeight = FontWeight.Bold) },
                        text = { Text(t("Bạn có thay đổi chưa lưu. Bạn muốn lưu hay bỏ bản nháp này?", "You have unsaved changes. Do you want to save or discard this draft?"), color = NaturalTertiary) },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showDiscardConfirmation = false
                                    if (inputUrl.isBlank()) {
                                        Toast.makeText(context, "Vui lòng nhập đường dẫn hợp lệ.", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    if (editingLink != null) {
                                        viewModel.updateLink(editingLink!!.copy(
                                            url = inputUrl,
                                            note = inputNote,
                                            categoryId = selectedCategoryId
                                        ))
                                        Toast.makeText(context, "Đã cập nhật ghi chú.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        viewModel.saveLink(
                                            title = "",
                                            url = inputUrl,
                                            note = inputNote,
                                            tags = emptyList(),
                                            categoryId = selectedCategoryId
                                        )
                                        Toast.makeText(context, "Đã lưu ghi chú.", Toast.LENGTH_SHORT).show()
                                    }
                                    resetForm()
                                    if (isFromShareIntent) {
                                        onFinishActivity()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NaturalPrimary)
                            ) {
                                Text(t("Lưu", "Save"), color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    showDiscardConfirmation = false
                                    resetForm()
                                    if (isFromShareIntent) {
                                        onFinishActivity()
                                    }
                                }
                            ) {
                                Text(t("Bỏ qua", "Discard"), color = Color.Red, fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                }
            }
        }

            // Category Creation overlays (Moved outside showAddManualForm scope)
            if (showAddCategoryForm) {
                var createNameError by remember { mutableStateOf(false) }
                AlertDialog(
                    onDismissRequest = {
                        showAddCategoryForm = false
                        if (returnToNoteFormAfterCategoryCreate) {
                            returnToNoteFormAfterCategoryCreate = false
                            showAddManualForm = true
                        }
                    },
                    title = { Text(t("Tạo danh mục mới", "Create category"), fontWeight = FontWeight.Bold, color = NaturalText) },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = categoryDraftName,
                                onValueChange = {
                                    categoryDraftName = it
                                    createNameError = false
                                },
                                label = { Text(t("Tên danh mục", "Category name")) },
                                isError = createNameError,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("create_category_name_field"),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NaturalPrimary,
                                    unfocusedBorderColor = NaturalBorder,
                                    focusedTextColor = NaturalText,
                                    unfocusedTextColor = NaturalText
                                )
                            )

                            Text(
                                text = t("Chọn biểu tượng", "Icon selection"),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = NaturalSecondary
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, NaturalBorder, RoundedCornerShape(12.dp))
                                    .background(colors.surface)
                                    .clickable { showLogoLibraryDialog = true }
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val logoIcon = CATEGORY_LOGOS[categoryDraftLogo] ?: Icons.Default.List
                                        Icon(
                                            imageVector = logoIcon,
                                            contentDescription = null,
                                            tint = NaturalPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = LOGO_DISPLAY_NAMES[categoryDraftLogo] ?: "Default Icon",
                                            fontSize = 14.sp,
                                            color = NaturalText,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Text(
                                        text = t("Thay đổi", "Change"),
                                        fontSize = 12.sp,
                                        color = NaturalPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (categoryDraftName.isBlank()) {
                                    createNameError = true
                                    Toast.makeText(context, t("Vui lòng nhập tên danh mục.", "Please enter a category name."), Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val createdCategoryName = categoryDraftName.trim()
                                viewModel.saveCategory(createdCategoryName, categoryDraftLogo)
                                Toast.makeText(context, t("Đã tạo danh mục.", "Category created."), Toast.LENGTH_SHORT).show()
                                pendingCreatedCategoryName = createdCategoryName
                                showAddCategoryForm = false
                                if (returnToNoteFormAfterCategoryCreate) {
                                    returnToNoteFormAfterCategoryCreate = false
                                    showAddManualForm = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NaturalPrimary)
                        ) {
                            Text(t("Tạo", "Create"), color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showAddCategoryForm = false
                                if (returnToNoteFormAfterCategoryCreate) {
                                    returnToNoteFormAfterCategoryCreate = false
                                    showAddManualForm = true
                                }
                            }
                        ) {
                            Text(t("Hủy", "Cancel"), color = NaturalTertiary)
                        }
                    }
                )
            }

            if (showLogoLibraryDialog) {
                AlertDialog(
                    onDismissRequest = { showLogoLibraryDialog = false },
                    title = { Text(t("Chọn biểu tượng", "Choose icon"), fontWeight = FontWeight.Bold, color = NaturalText) },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    triggerGalleryPicker { savedPath ->
                                        categoryDraftLogo = savedPath
                                        showLogoLibraryDialog = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, NaturalPrimary)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.PhotoLibrary, null, tint = NaturalPrimary)
                                    Text(t("Chọn từ thư viện", "Choose from Gallery"), color = NaturalPrimary, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            Text(
                                t("Hoặc chọn biểu tượng có sẵn:", "Or select a standard icon:"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = NaturalSecondary
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(240.dp)
                            ) {
                                val logosList = CATEGORY_LOGOS.keys.toList()
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                val rowCount = (logosList.size + 3) / 4
                                items(rowCount) { rowIndex ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        for (colIndex in 0 until 4) {
                                            val logIndex = rowIndex * 4 + colIndex
                                            val logoKey = logosList.getOrNull(logIndex)
                                            if (logoKey != null) {
                                                val optIcon = CATEGORY_LOGOS[logoKey] ?: Icons.Default.List
                                                val isChosen = categoryDraftLogo == logoKey
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .aspectRatio(1f)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(if (isChosen) NaturalAccentChip else colors.surfaceVariant)
                                                        .border(1.dp, if (isChosen) NaturalPrimary else Color.Transparent, RoundedCornerShape(8.dp))
                                                        .clickable {
                                                            categoryDraftLogo = logoKey
                                                            showLogoLibraryDialog = false
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                        Icon(optIcon, null, tint = if (isChosen) NaturalPrimary else NaturalSecondary, modifier = Modifier.size(24.dp))
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Text(LOGO_DISPLAY_NAMES[logoKey]?.take(6) ?: "Logo", fontSize = 8.sp, color = NaturalTertiary, maxLines = 1)
                                                    }
                                                }
                                            } else {
                                                Box(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { showLogoLibraryDialog = false }) {
                            Text(t("Đóng", "Close"), color = NaturalPrimary)
                        }
                    }
                )
            }
        }
    }

    if (customPickedUri != null) {
        CropImageDialog(
            imageUri = customPickedUri!!,
            onDismiss = {
                customPickedUri = null
                onCropFinishedCallback = null
            },
            onCropped = { bitmap ->
                try {
                    val filename = "cat_${System.currentTimeMillis()}.png"
                    val file = File(context.filesDir, filename)
                    val out = FileOutputStream(file)
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    out.flush()
                    out.close()
                    onCropFinishedCallback?.invoke(file.absolutePath)
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(context, "Error saving custom icon: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    customPickedUri = null
                    onCropFinishedCallback = null
                }
            }
        )
    }
}

@Composable
fun LinksDirectoryView(
    links: List<SavedLink>,
    searchQuery: String,
    sortOrder: SortOrder,
    onUpdateSearch: (String) -> Unit,
    onUpdateSort: (SortOrder) -> Unit,
    onDeleteLink: (SavedLink) -> Unit,
    onOpenUrl: (String) -> Unit,
    getDomain: (String) -> String,
    onEditLink: (SavedLink) -> Unit,
    onShareLink: (SavedLink) -> Unit,
    language: String
) {
    val context = LocalContext.current
    val t = { vi: String, en: String -> appText(language, vi, en) }
    var showSortDropdown by remember { mutableStateOf(false) }

    // Resolve local overriding colors
    val colors = MaterialTheme.colorScheme
    val NaturalBg = colors.background
    val NaturalText = colors.onBackground
    val NaturalPrimary = colors.primary
    val NaturalSecondary = colors.secondary
    val NaturalTertiary = colors.tertiary
    val NaturalBorder = colors.outline
    val NaturalAccentChip = colors.primaryContainer

    // Bulk selection modes
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedLinks = remember { mutableStateListOf<SavedLink>() }

    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedLinks.clear()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NaturalBg)
    ) {
        // App header & Search bar
        if (isSelectionMode) {
            // Bulk selection actions header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .background(NaturalAccentChip.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .border(1.dp, NaturalBorder, RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = {
                        isSelectionMode = false
                        selectedLinks.clear()
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Exit selection mode", tint = NaturalText)
                    }
                    Text(
                        text = t("Đã chọn ${selectedLinks.size}", "${selectedLinks.size} selected"),
                        fontWeight = FontWeight.Bold,
                        color = NaturalText,
                        fontSize = 16.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(onClick = {
                        selectedLinks.clear()
                        selectedLinks.addAll(links)
                    }) {
                        Text(t("Chọn tất cả", "Select all"), color = NaturalPrimary, fontWeight = FontWeight.Bold)
                    }

                    IconButton(
                        onClick = {
                            if (selectedLinks.isEmpty()) {
                                Toast.makeText(context, t("Chưa chọn liên kết nào.", "No links selected."), Toast.LENGTH_SHORT).show()
                                return@IconButton
                            }
                            selectedLinks.forEach { linkItem ->
                                onDeleteLink(linkItem)
                            }
                            Toast.makeText(context, t("Đã xóa ${selectedLinks.size} liên kết.", "Deleted ${selectedLinks.size} links."), Toast.LENGTH_SHORT).show()
                            isSelectionMode = false
                            selectedLinks.clear()
                        },
                        enabled = selectedLinks.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete selected items",
                            tint = if (selectedLinks.isNotEmpty()) Color.Red else NaturalTertiary.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        } else {
            // Normal Header & Search Panel
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "LinkVault",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = NaturalText,
                            modifier = Modifier.testTag("app_header_title")
                        )
                        Text(
                            text = t("Lưu trữ an toàn các liên kết quan trọng", "Save important links in one safe place"),
                            fontSize = 12.sp,
                            color = NaturalTertiary
                        )
                    }
                }

                // Search Panel
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(colors.surfaceVariant)
                        .border(1.dp, NaturalBorder, RoundedCornerShape(28.dp))
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search icon",
                        tint = NaturalSecondary,
                        modifier = Modifier.size(24.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Box(modifier = Modifier.weight(1.0f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = searchPlaceholder(language),
                                color = NaturalTertiary,
                                fontSize = 14.sp
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onUpdateSearch,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = NaturalText,
                                fontSize = 14.sp
                            ),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("search_links_input")
                        )
                    }

                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onUpdateSearch("") },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = NaturalSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    // Sort Order dropdown
                    Box {
                        IconButton(
                            onClick = { showSortDropdown = true },
                            modifier = Modifier
                                .size(28.dp)
                                .testTag("sort_trigger_button")
                        ) {
                            Icon(
                                imageVector = when (sortOrder) {
                                    SortOrder.RECENT -> Icons.Default.KeyboardArrowDown
                                    else -> Icons.Default.KeyboardArrowUp
                                },
                                contentDescription = "Sort links",
                                tint = NaturalText
                            )
                        }

                        DropdownMenu(
                            expanded = showSortDropdown,
                            onDismissRequest = { showSortDropdown = false },
                            modifier = Modifier.background(colors.surface)
                        ) {
                            DropdownMenuItem(
                                text = { Text(t("Sắp xếp theo ngày mới nhất", "Sort by newest"), color = NaturalText) },
                                onClick = {
                                    onUpdateSort(SortOrder.RECENT)
                                    showSortDropdown = false
                                },
                                leadingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = NaturalSecondary) }
                            )
                            DropdownMenuItem(
                                text = { Text(t("Sắp xếp theo tên A-Z", "Sort by title A-Z"), color = NaturalText) },
                                onClick = {
                                    onUpdateSort(SortOrder.TITLE)
                                    showSortDropdown = false
                                },
                                leadingIcon = { Icon(Icons.Default.Home, contentDescription = null, tint = NaturalSecondary) }
                            )
                            DropdownMenuItem(
                                text = { Text(t("Sắp xếp theo tên miền", "Sort by domain"), color = NaturalText) },
                                onClick = {
                                    onUpdateSort(SortOrder.DOMAIN)
                                    showSortDropdown = false
                                },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = NaturalSecondary) }
                            )
                        }
                    }
                }
            }
        }

        // Subtitle header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (searchQuery.isNotEmpty()) t("KẾT QUẢ TÌM KIẾM", "SEARCH RESULTS") else t("ĐÃ LƯU GẦN ĐÂY", "RECENTLY SAVED"),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = NaturalTertiary,
                letterSpacing = 1.sp
            )

            Text(
                text = t("${links.size} liên kết", "${links.size} links"),
                fontSize = 11.sp,
                color = NaturalTertiary
            )
        }

        // List Content
        if (links.isEmpty()) {
            EmptyListPlaceholder(language)
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("saved_links_list"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(links, key = { it.id }) { linkItem ->
                    val isSelected = selectedLinks.contains(linkItem)
                    SwipeableLinkItem(
                        link = linkItem,
                        isSelected = isSelected,
                        isSelectionMode = isSelectionMode,
                        onSelectToggle = {
                            if (isSelected) {
                                selectedLinks.remove(linkItem)
                                if (selectedLinks.isEmpty()) {
                                    isSelectionMode = false
                                }
                            } else {
                                selectedLinks.add(linkItem)
                            }
                        },
                        onEnterSelectionMode = {
                            isSelectionMode = true
                            selectedLinks.clear()
                            selectedLinks.add(linkItem)
                        },
                        onEdit = {
                            onEditLink(linkItem)
                        },
                        onDelete = {
                            onDeleteLink(linkItem)
                        },
                        onTap = {
                            onOpenUrl(linkItem.url)
                        },
                        onShare = { onShareLink(linkItem) },
                        getDomain = getDomain,
                        language = language
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwipeableLinkItem(
    link: SavedLink,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onSelectToggle: () -> Unit,
    onEnterSelectionMode: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTap: () -> Unit,
    onShare: () -> Unit,
    getDomain: (String) -> String,
    language: String
) {
    val density = LocalDensity.current
    val t = { vi: String, en: String -> appText(language, vi, en) }
    val maxSwipeLeft = with(density) { -80.dp.toPx() }
    val maxSwipeRight = with(density) { 80.dp.toPx() }

    var offsetX by remember { mutableStateOf(0f) }
    val animatedOffsetX by animateFloatAsState(targetValue = offsetX)

    // Resolve Dynamic override colors
    val colors = MaterialTheme.colorScheme
    val NaturalBg = colors.background
    val NaturalText = colors.onBackground
    val NaturalPrimary = colors.primary
    val NaturalSecondary = colors.secondary
    val NaturalTertiary = colors.tertiary
    val NaturalBorder = colors.outline
    val NaturalAccentChip = colors.primaryContainer

    val isVideo = getDomain(link.url).contains("youtube.com") || getDomain(link.url).contains("youtu.be") || getDomain(link.url).contains("vimeo")
    
    val bubbleColor = when {
        isVideo -> colors.primaryContainer
        else -> colors.surfaceVariant
    }

    val bubbleIcon = when {
        isVideo -> Icons.Default.PlayArrow
        else -> Icons.Default.Share
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        // Underlay Actions
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Swipe Right Details (Edit action)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(80.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                    .background(Color(0xFFE8F0E3))
                    .clickable {
                        offsetX = 0f
                        onEdit()
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit link", tint = Color(0xFF2E7D32))
                    Text(t("Sửa", "Edit"), fontSize = 11.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                }
            }

            // Swipe Left Details (Delete action)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(80.dp)
                    .clip(RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))
                    .background(Color(0xFFFDE8E8))
                    .clickable {
                        offsetX = 0f
                        onDelete()
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete link", tint = Color(0xFFC62828))
                    Text(t("Xóa", "Delete"), fontSize = 11.sp, color = Color(0xFFC62828), fontWeight = FontWeight.Bold)
                }
            }
        }

        // Card Overlay
        Card(
            modifier = Modifier
                .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetX < maxSwipeLeft / 2) {
                                offsetX = maxSwipeLeft
                            } else if (offsetX > maxSwipeRight / 2) {
                                offsetX = maxSwipeRight
                            } else {
                                offsetX = 0f
                            }
                        },
                        onDragCancel = {
                            offsetX = 0f
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            val newOffset = offsetX + dragAmount
                            offsetX = newOffset.coerceIn(maxSwipeLeft, maxSwipeRight)
                        }
                    )
                }
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        if (isSelectionMode) {
                            onSelectToggle()
                        } else {
                            onTap()
                        }
                    },
                    onLongClick = {
                        if (!isSelectionMode) {
                            onEnterSelectionMode()
                        }
                    }
                )
                .testTag("link_card_item_${link.id}"),
            colors = CardDefaults.cardColors(
                containerColor = if (isSelected) NaturalAccentChip else colors.surface
            ),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) NaturalPrimary else NaturalBorder
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onSelectToggle() },
                        colors = CheckboxDefaults.colors(checkedColor = NaturalPrimary)
                    )
                }

                // Multi information layout: NOTE on TOP, DOMAIN on BOTTOM
                Column(modifier = Modifier.weight(1.0f)) {
                    // Note at the very top
                    Text(
                        text = if (link.note.isNotEmpty()) link.note else t("Chưa có ghi chú", "No note yet"),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = NaturalText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Domain and timestamp below
                    Text(
                        text = "${getDomain(link.url)} • ${formatRelativeTime(link.timestamp, language)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = NaturalTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onShare,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Chia sẻ ghi chú",
                        tint = NaturalSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyListPlaceholder(language: String) {
    val colors = MaterialTheme.colorScheme
    val t = { vi: String, en: String -> appText(language, vi, en) }
    val NaturalBg = colors.background
    val NaturalText = colors.onBackground
    val NaturalPrimary = colors.primary
    val NaturalSecondary = colors.secondary
    val NaturalTertiary = colors.tertiary
    val NaturalBorder = colors.outline
    val NaturalAccentChip = colors.primaryContainer

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(CircleShape)
                .background(colors.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = null,
                tint = NaturalPrimary,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = t("Chưa có liên kết", "No links saved"),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = NaturalText
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = t("Chia sẻ URL, bài viết mạng xã hội hoặc trang web từ ứng dụng khác vào LinkVault. Bạn cũng có thể nhấn + để lưu thủ công và phân loại bằng ghi chú riêng.", "Share any browser URL, social post, or web page from other apps to LinkVault. Tap + to save one manually and organize it with notes."),
            fontSize = 13.sp,
            color = NaturalTertiary,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    }
}

@Composable
fun SettingsView(
    userEmail: String,
    themeMode: String,
    themeSelection: String,
    onThemeModeChange: (String) -> Unit,
    onThemeSelectionChange: (String) -> Unit,
    onLogout: () -> Unit,
    links: List<SavedLink>,
    categories: List<com.example.data.Category>,
    language: String,
    onLanguageChange: (String) -> Unit,
    onImportLinks: (List<ImportedLinkDraft>) -> Unit
) {
    // Dynamic override
    val colors = MaterialTheme.colorScheme
    val t = { vi: String, en: String -> appText(language, vi, en) }
    val NaturalBg = colors.background
    val NaturalText = colors.onBackground
    val NaturalPrimary = colors.primary
    val NaturalSecondary = colors.secondary
    val NaturalTertiary = colors.tertiary
    val NaturalBorder = colors.outline
    val NaturalAccentChip = colors.primaryContainer

    var showUserGuideDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NaturalBg)
            .statusBarsPadding()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = t("Cài đặt", "Settings"),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = NaturalText,
            modifier = Modifier.testTag("settings_header_title")
        )

        // 1. Google account email card info
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, NaturalBorder)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(NaturalAccentChip),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (userEmail.isNotEmpty()) userEmail.take(2).uppercase() else "G",
                        color = NaturalPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                Column(modifier = Modifier.weight(1.0f)) {
                    Text(
                        text = t("TÀI KHOẢN GOOGLE", "GOOGLE ACCOUNT"),
                        fontSize = 11.sp,
                        color = NaturalTertiary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = userEmail,
                        fontSize = 15.sp,
                        color = NaturalText,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = onLogout,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFDE8E8)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(t("Đăng xuất", "Logout"), color = Color(0xFFC62828), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, NaturalBorder)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isVietnameseActive = language != "en"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isVietnameseActive) NaturalAccentChip else colors.surfaceVariant)
                            .border(1.dp, if (isVietnameseActive) NaturalPrimary else NaturalBorder, RoundedCornerShape(10.dp))
                            .clickable { onLanguageChange("vi") },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Tiếng Việt",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isVietnameseActive) NaturalPrimary else NaturalTertiary
                        )
                    }

                    val isEnglishActive = language == "en"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isEnglishActive) NaturalAccentChip else colors.surfaceVariant)
                            .border(1.dp, if (isEnglishActive) NaturalPrimary else NaturalBorder, RoundedCornerShape(10.dp))
                            .clickable { onLanguageChange("en") },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "English",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isEnglishActive) NaturalPrimary else NaturalTertiary
                        )
                    }
                }
            }
        }

        // 2. Choose Mode (Dark / Light)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, NaturalBorder)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Light Mode Button
                    val isLightActive = themeMode == "light"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isLightActive) NaturalAccentChip else colors.surfaceVariant)
                            .border(1.dp, if (isLightActive) NaturalPrimary else NaturalBorder, RoundedCornerShape(10.dp))
                            .clickable { onThemeModeChange("light") },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            t("Sáng", "Light"),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isLightActive) NaturalPrimary else NaturalTertiary
                        )
                    }

                    // Dark Mode Button
                    val isDarkActive = themeMode == "dark"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isDarkActive) NaturalAccentChip else colors.surfaceVariant)
                            .border(1.dp, if (isDarkActive) NaturalPrimary else NaturalBorder, RoundedCornerShape(10.dp))
                            .clickable { onThemeModeChange("dark") },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            t("Tối", "Dark"),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkActive) NaturalPrimary else NaturalTertiary
                        )
                    }

                    // System Mode Button
                    val isSystemActive = themeMode == "system"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSystemActive) NaturalAccentChip else colors.surfaceVariant)
                            .border(1.dp, if (isSystemActive) NaturalPrimary else NaturalBorder, RoundedCornerShape(10.dp))
                            .clickable { onThemeModeChange("system") },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            t("Theo máy", "System"),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSystemActive) NaturalPrimary else NaturalTertiary
                        )
                    }
                }
            }
        }

        // 3. Choose Custom Theme (5 default themes)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, NaturalBorder)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeColorItem(
                            title = "Denim Cool",
                            dotColor = Color(0xFF1E88E5),
                            isActive = themeSelection == "denim",
                            onClick = { onThemeSelectionChange("denim") }
                        )
                        ThemeColorItem(
                            title = "Forest Jade",
                            dotColor = Color(0xFF2E7D32),
                            isActive = themeSelection == "forest",
                            onClick = { onThemeSelectionChange("forest") }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeColorItem(
                            title = "Blossom Rose",
                            dotColor = Color(0xFFD81B60),
                            isActive = themeSelection == "blossom",
                            onClick = { onThemeSelectionChange("blossom") }
                        )
                        ThemeColorItem(
                            title = "Peach Amber",
                            dotColor = Color(0xFFE65100),
                            isActive = themeSelection == "peach",
                            onClick = { onThemeSelectionChange("peach") }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeColorItem(
                            title = "Lavender Mist",
                            dotColor = Color(0xFF8E24AA),
                            isActive = themeSelection == "lavender",
                            onClick = { onThemeSelectionChange("lavender") }
                        )
                        Box(modifier = Modifier.weight(1f)) // spacer block
                    }
                }
            }
        }

        // SharedPreferences & Context for CSV Export
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()

        var showExportChoiceDialog by remember { mutableStateOf(false) }
        var showSavedSuccessDialog by remember { mutableStateOf(false) }
        var importErrorMessage by remember { mutableStateOf<String?>(null) }
        var updateMessage by remember { mutableStateOf<String?>(null) }
        var updateDownloadUrl by remember { mutableStateOf<String?>(null) }
        var isCheckingUpdate by remember { mutableStateOf(false) }
        var savedUri by remember { mutableStateOf<Uri?>(null) }

        val csvImportLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val csvText = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                            ?: throw IllegalArgumentException(t("Không thể đọc file CSV đã chọn.", "Could not read the selected CSV file."))
                        when (val result = parseImportedCsv(csvText, categories)) {
                            is CsvImportResult.Success -> withContext(Dispatchers.Main) {
                                onImportLinks(result.rows)
                                Toast.makeText(context, t("Đã nhập ${result.rows.size} ghi chú từ file CSV.", "Imported ${result.rows.size} notes from CSV."), Toast.LENGTH_LONG).show()
                            }
                            is CsvImportResult.Error -> withContext(Dispatchers.Main) {
                                importErrorMessage = result.message
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            importErrorMessage = e.message ?: t("Không thể nhập file CSV.", "Could not import the CSV file.")
                        }
                    }
                }
            }
        }

        val csvExportLauncher = rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv")
        ) { uri ->
            if (uri != null) {
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                            val csvString = generateCsvContent(links, categories)
                            outputStream.write(csvString.toByteArray(Charsets.UTF_8))
                        }
                        withContext(Dispatchers.Main) {
                            savedUri = uri
                            showSavedSuccessDialog = true
                            Toast.makeText(context, t("Đã xuất dữ liệu thành công ra file CSV!", "Data exported to CSV successfully."), Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, t("Xuất file thất bại: ${e.message}", "Export failed: ${e.message}"), Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }

        fun shareCsvDirectly() {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val csvString = generateCsvContent(links, categories)
                    val exportDir = File(context.cacheDir, "exports")
                    if (!exportDir.exists()) {
                        exportDir.mkdirs()
                    }
                    val file = File(exportDir, "LinkVault_Export.csv")
                    file.writeBytes(csvString.toByteArray(Charsets.UTF_8))
                    
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/csv"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    
                    withContext(Dispatchers.Main) {
                        context.startActivity(Intent.createChooser(shareIntent, t("Chia sẻ file CSV qua:", "Share CSV with:")))
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, t("Chia sẻ thất bại: ${e.message}", "Sharing failed: ${e.message}"), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { showExportChoiceDialog = true },
                modifier = Modifier.weight(1f).height(48.dp).testTag("export_csv_button"),
                colors = ButtonDefaults.buttonColors(containerColor = NaturalPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = t("Xuất dữ liệu", "Export data"),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Button(
                onClick = { csvImportLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/*", "*/*")) },
                modifier = Modifier.weight(1f).height(48.dp).testTag("import_csv_button"),
                colors = ButtonDefaults.buttonColors(containerColor = NaturalSecondary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.UploadFile,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = t("Nhập dữ liệu", "Import data"),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        if (importErrorMessage != null) {
            AlertDialog(
                onDismissRequest = { importErrorMessage = null },
                title = { Text(t("Không thể nhập CSV", "Could not import CSV"), fontWeight = FontWeight.Bold, color = NaturalText) },
                text = { Text(importErrorMessage.orEmpty(), color = NaturalSecondary) },
                confirmButton = {
                    Button(
                        onClick = { importErrorMessage = null },
                        colors = ButtonDefaults.buttonColors(containerColor = NaturalPrimary)
                    ) {
                        Text(t("Đã hiểu", "Got it"), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        // Export Choice Dialog (Save vs Share)
        if (showExportChoiceDialog) {
            AlertDialog(
                onDismissRequest = { showExportChoiceDialog = false },
                title = { Text(t("Xuất dữ liệu", "Export data"), fontWeight = FontWeight.Bold, color = NaturalText) },
                text = { Text(t("Bạn muốn lưu tệp CSV về máy hay chia sẻ trực tiếp qua ứng dụng khác?", "Do you want to save the CSV file to this device or share it directly?"), color = NaturalSecondary) },
                confirmButton = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Option Save
                        Button(
                            onClick = {
                                showExportChoiceDialog = false
                                csvExportLauncher.launch("LinkVault_Export.csv")
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = NaturalPrimary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Default.Save, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(t("Lưu về máy", "Save"), color = Color.White, fontSize = 12.sp)
                        }

                        // Option Share
                        Button(
                            onClick = {
                                showExportChoiceDialog = false
                                shareCsvDirectly()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = NaturalSecondary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Default.Share, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(t("Chia sẻ", "Share"), color = Color.White, fontSize = 12.sp)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExportChoiceDialog = false }) {
                        Text(t("Hủy", "Cancel"), color = NaturalTertiary)
                    }
                }
            )
        }

        // Saved Success Open-Immediate Dialog
        if (showSavedSuccessDialog && savedUri != null) {
            AlertDialog(
                onDismissRequest = { showSavedSuccessDialog = false },
                title = { Text(t("Xuất tệp thành công!", "Export complete"), fontWeight = FontWeight.Bold, color = NaturalText) },
                text = { Text(t("Dữ liệu của bạn đã được xuất ra định dạng CSV và lưu vào thiết bị dưới tên tệp bạn đã chọn. Bạn có muốn mở xem ngay lập tức?", "Your data was exported as a CSV file and saved to this device. Do you want to open it now?"), color = NaturalSecondary) },
                confirmButton = {
                    Button(
                        onClick = {
                            showSavedSuccessDialog = false
                            try {
                                val openIntent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(savedUri, "text/csv")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(Intent.createChooser(openIntent, t("Mở file CSV bằng:", "Open CSV with:")))
                            } catch (e: Exception) {
                                Toast.makeText(context, t("Không thể mở file tự động. Bạn có thể tự tìm kiếm file trong thư mục Downloads của máy.", "Could not open the file automatically. You can find it in your Downloads folder."), Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NaturalPrimary)
                    ) {
                        Text(t("Mở tệp ngay", "Open now"), color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSavedSuccessDialog = false }) {
                        Text(t("Để sau", "Later"), color = NaturalTertiary)
                    }
                }
            )
        }

        // 4. User Guide Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showUserGuideDialog = true },
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, NaturalBorder)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(NaturalAccentChip),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "User Guide",
                        tint = NaturalPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = t("HƯỚNG DẪN SỬ DỤNG", "USER GUIDE"),
                    fontSize = 11.sp,
                    color = NaturalPrimary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.weight(1f)
                )

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Open guide",
                    tint = NaturalTertiary
                )
            }
        }

        Button(
            onClick = {
                isCheckingUpdate = true
                coroutineScope.launch {
                    try {
                        val release = fetchLatestGithubRelease()
                        val isNewer = isRemoteVersionNewer(BuildConfig.VERSION_NAME, release.tagName)
                        updateDownloadUrl = if (isNewer) release.apkUrl ?: release.htmlUrl else null
                        updateMessage = if (isNewer) {
                            t(
                                "Đã có phiên bản ${release.tagName}. Bạn có muốn mở trang tải APK không?",
                                "Version ${release.tagName} is available. Open the APK download page?"
                            )
                        } else {
                            t(
                                "Bạn đang dùng phiên bản mới nhất (${BuildConfig.VERSION_NAME}).",
                                "You are using the latest version (${BuildConfig.VERSION_NAME})."
                            )
                        }
                    } catch (e: Exception) {
                        updateDownloadUrl = null
                        updateMessage = t(
                            "Không thể kiểm tra cập nhật. Vui lòng thử lại sau.",
                            "Could not check for updates. Please try again later."
                        )
                    } finally {
                        isCheckingUpdate = false
                    }
                }
            },
            enabled = !isCheckingUpdate,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("check_update_button"),
            colors = ButtonDefaults.buttonColors(containerColor = NaturalPrimary),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SystemUpdate,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = Color.White
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isCheckingUpdate) t("Đang kiểm tra...", "Checking...") else t("Kiểm tra cập nhật", "Check update"),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        if (updateMessage != null) {
        AlertDialog(
            onDismissRequest = { updateMessage = null },
            title = { Text(t("Cập nhật", "Update"), fontWeight = FontWeight.Bold, color = NaturalText) },
            text = { Text(updateMessage.orEmpty(), color = NaturalSecondary) },
            confirmButton = {
                if (updateDownloadUrl != null) {
                    Button(
                        onClick = {
                            val openIntent = Intent(Intent.ACTION_VIEW, Uri.parse(updateDownloadUrl))
                            context.startActivity(openIntent)
                            updateMessage = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NaturalPrimary)
                    ) {
                        Text(t("Mở trang tải", "Open download"), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = { updateMessage = null },
                        colors = ButtonDefaults.buttonColors(containerColor = NaturalPrimary)
                    ) {
                        Text(t("Đã hiểu", "Got it"), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                if (updateDownloadUrl != null) {
                    TextButton(onClick = { updateMessage = null }) {
                        Text(t("Để sau", "Later"), color = NaturalTertiary)
                    }
                }
            }
        )
        }
    }

    if (showUserGuideDialog) {
        AlertDialog(
            onDismissRequest = { showUserGuideDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.MenuBook, null, tint = NaturalPrimary)
                    Text(t("Hướng dẫn sử dụng", "User guide"), fontWeight = FontWeight.Bold, color = NaturalText)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        t("LinkVault giúp bạn lưu, tìm kiếm và chia sẻ các liên kết quan trọng bằng những thao tác đơn giản:", "LinkVault helps you save, find, and share important links with simple gestures:"),
                        fontSize = 14.sp,
                        color = NaturalText
                    )

                    // Swipe Right Section
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(colors.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Edit, null, tint = NaturalPrimary, modifier = Modifier.size(18.dp))
                        }
                        Column {
                            Text(t("Vuốt sang phải", "Swipe right"), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = NaturalText)
                            Text(t("Sửa và cập nhật ghi chú", "Edit and update a note"), fontSize = 12.sp, color = NaturalTertiary)
                        }
                    }

                    // Swipe Left Section
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFFFDE8E8), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Delete, null, tint = Color(0xFFC62828), modifier = Modifier.size(18.dp))
                        }
                        Column {
                            Text(t("Vuốt sang trái", "Swipe left"), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = NaturalText)
                            Text(t("Xóa nhanh ghi chú không còn cần thiết", "Quickly delete notes you no longer need"), fontSize = 12.sp, color = NaturalTertiary)
                        }
                    }

                    // Long Press Section
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(colors.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CheckCircle, null, tint = NaturalPrimary, modifier = Modifier.size(18.dp))
                        }
                        Column {
                            Text(t("Nhấn giữ", "Long press"), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = NaturalText)
                            Text(t("Chọn nhiều ghi chú để xóa hàng loạt", "Select multiple notes for bulk delete"), fontSize = 12.sp, color = NaturalTertiary)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(colors.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Share, null, tint = NaturalPrimary, modifier = Modifier.size(18.dp))
                        }
                        Column {
                            Text(t("Chia sẻ", "Share"), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = NaturalText)
                            Text(t("Nhấn biểu tượng chia sẻ ở từng ghi chú để gửi sang ứng dụng khác", "Tap the share icon on a note to send it to another app"), fontSize = 12.sp, color = NaturalTertiary)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showUserGuideDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = NaturalPrimary)
                ) {
                    Text(t("Đã hiểu", "Got it"), color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
fun RowScope.ThemeColorItem(
    title: String,
    dotColor: Color,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isActive) colors.primaryContainer else colors.surfaceVariant)
            .border(1.dp, if (isActive) colors.primary else colors.outline, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )

            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isActive) colors.primary else colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// Utility: parse URL links from raw block strings securely using broad match
fun shareLinkItem(context: Context, link: SavedLink, language: String) {
    val shareText = if (link.note.isNotBlank()) {
        "${link.note}\n\n${link.url}"
    } else {
        link.url
    }
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, shareText)
    }
    context.startActivity(Intent.createChooser(shareIntent, appText(language, "Chia sẻ ghi chú qua:", "Share note with:")))
}

fun extractUrl(text: String): String {
    val regex = "https?://[^\\s]+".toRegex()
    val match = regex.find(text)
    if (match != null) {
        var url = match.value
        // Clean trailing punctuation sentence-level
        while (url.isNotEmpty() && (url.endsWith(".") || url.endsWith(",") || url.endsWith(")") || url.endsWith("]"))) {
            url = url.substring(0, url.length - 1)
        }
        return url
    }
    return text.trim()
}

// ==========================================
// CATEGORY COMPONENT GRAPHICS & DASHBOARDS
// ==========================================

val CATEGORY_LOGOS = mapOf(
    "logo_01_work" to Icons.Default.BusinessCenter,
    "logo_02_business" to Icons.Default.Laptop,
    "logo_03_goals" to Icons.Default.Adjust,
    "logo_04_growth" to Icons.Default.TrendingUp,
    "logo_05_ideas" to Icons.Default.Lightbulb,
    "logo_06_study" to Icons.Default.MenuBook,
    "logo_07_education" to Icons.Default.School,
    "logo_08_writing" to Icons.Default.Create,
    "logo_09_reading" to Icons.Default.Article,
    "logo_10_knowledge" to Icons.Default.Psychology,
    "logo_11_fitness" to Icons.Default.FitnessCenter,
    "logo_12_photography" to Icons.Default.PhotoCamera,
    "logo_13_music" to Icons.Default.MusicNote,
    "logo_14_art" to Icons.Default.Palette,
    "logo_15_gaming" to Icons.Default.SportsEsports,
    "logo_16_travel" to Icons.Default.Flight,
    "logo_17_coffee" to Icons.Default.LocalCafe,
    "logo_18_movies" to Icons.Default.Movie,
    "logo_19_entertainment" to Icons.Default.Headphones,
    "logo_20_social" to Icons.Default.People
)

val LOGO_DISPLAY_NAMES = mapOf(
    "logo_01_work" to "Work",
    "logo_02_business" to "Business",
    "logo_03_goals" to "Goals",
    "logo_04_growth" to "Growth",
    "logo_05_ideas" to "Ideas",
    "logo_06_study" to "Study",
    "logo_07_education" to "Education",
    "logo_08_writing" to "Writing",
    "logo_09_reading" to "Reading",
    "logo_10_knowledge" to "Knowledge",
    "logo_11_fitness" to "Fitness",
    "logo_12_photography" to "Photography",
    "logo_13_music" to "Music",
    "logo_14_art" to "Art",
    "logo_15_gaming" to "Gaming",
    "logo_16_travel" to "Travel",
    "logo_17_coffee" to "Coffee",
    "logo_18_movies" to "Movies",
    "logo_19_entertainment" to "Entertainment",
    "logo_20_social" to "Social"
)

@Composable
fun CategoryLogoDisplay(
    logoKey: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    size: androidx.compose.ui.unit.Dp = 32.dp,
    shape: androidx.compose.ui.graphics.Shape = CircleShape,
    fillMax: Boolean = false
) {
    if (logoKey.startsWith("/") || logoKey.startsWith("file://") || logoKey.startsWith("content://")) {
        Image(
            painter = rememberAsyncImagePainter(logoKey),
            contentDescription = "Custom Category Logo",
            modifier = modifier
                .then(if (fillMax) Modifier.fillMaxSize() else Modifier.size(size))
                .clip(shape),
            contentScale = ContentScale.Crop
        )
    } else {
        val icon = CATEGORY_LOGOS[logoKey] ?: Icons.Default.List
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = modifier.size(size)
        )
    }
}

@Composable
fun CropImageDialog(
    imageUri: Uri,
    onDismiss: () -> Unit,
    onCropped: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    var scale by remember { mutableStateOf(1.0f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    
    val density = LocalDensity.current
    val viewportSizeDp = 200.dp
    val viewportSizePx = with(density) { viewportSizeDp.toPx() }

    LaunchedEffect(imageUri) {
        try {
            val contentResolver = context.contentResolver
            val bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, imageUri)) { decoder, _, _ ->
                    decoder.isMutableRequired = true
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
            }
            originalBitmap = bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error reading image: ${e.message}", Toast.LENGTH_SHORT).show()
            onDismiss()
        }
    }

    if (originalBitmap != null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Crop 1:1 Icon", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "Kéo để di chuyển, nhúm để phóng to/thu nhỏ ảnh để chọn đúng vùng cần cắt.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    
                    val bitmap = originalBitmap!!
                    val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()

                    Box(
                        modifier = Modifier
                            .size(viewportSizeDp)
                            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            .clip(CircleShape)
                            .background(Color.LightGray)
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(1.0f, 6.0f)
                                    
                                    val wDrawn = if (aspectRatio >= 1f) viewportSizePx * aspectRatio * scale else viewportSizePx * scale
                                    val hDrawn = if (aspectRatio >= 1f) viewportSizePx * scale else (viewportSizePx / aspectRatio) * scale
                                    
                                    val maxOffsetX = (wDrawn - viewportSizePx) / 2f
                                    val maxOffsetY = (hDrawn - viewportSizePx) / 2f
                                    
                                    offsetX = (offsetX + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                                    offsetY = (offsetY + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val imageWidthDp = if (aspectRatio >= 1f) viewportSizeDp * aspectRatio else viewportSizeDp
                        val imageHeightDp = if (aspectRatio >= 1f) viewportSizeDp else viewportSizeDp / aspectRatio

                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Pinch and drag preview",
                            modifier = Modifier
                                .requiredSize(imageWidthDp, imageHeightDp)
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offsetX,
                                    translationY = offsetY
                                ),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val bitmap = originalBitmap!!
                        val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
                        
                        val wDrawn = if (aspectRatio >= 1f) viewportSizePx * aspectRatio * scale else viewportSizePx * scale
                        val hDrawn = if (aspectRatio >= 1f) viewportSizePx * scale else (viewportSizePx / aspectRatio) * scale
                        
                        val viewLeftRelImg = (wDrawn - viewportSizePx) / 2f - offsetX
                        val viewTopRelImg = (hDrawn - viewportSizePx) / 2f - offsetY
                        
                        val factor = bitmap.width.toFloat() / wDrawn
                        
                        val cropX = (viewLeftRelImg * factor).toInt().coerceIn(0, bitmap.width - 1)
                        val cropY = (viewTopRelImg * factor).toInt().coerceIn(0, bitmap.height - 1)
                        val cropSize = (viewportSizePx * factor).toInt().coerceIn(1, minOf(bitmap.width - cropX, bitmap.height - cropY))
                        
                        try {
                            val cropped = Bitmap.createBitmap(bitmap, cropX, cropY, cropSize, cropSize)
                            onCropped(cropped)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Toast.makeText(context, "Crop failed: ${e.message}", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Confirm Crop", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

@Composable
fun DashedBox(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .background(colors.surface)
            .drawWithContent {
                drawContent()
                val pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
                drawRoundRect(
                    color = colors.outline.copy(alpha = 0.5f),
                    style = Stroke(width = 2.dp.toPx(), pathEffect = pathEffect),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx(), 12.dp.toPx())
                )
            },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
fun CategoriesDirectoryView(
    categories: List<com.example.data.Category>,
    language: String,
    onCategoryClick: (com.example.data.Category) -> Unit,
    onAddCategory: () -> Unit,
    onReorder: (from: Int, to: Int) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val t = { vi: String, en: String -> appText(language, vi, en) }
    val NaturalBg = colors.background
    val NaturalText = colors.onBackground
    val NaturalPrimary = colors.primary
    val NaturalBorder = colors.outline

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NaturalBg)
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        Text(
            text = t("Danh mục", "Categories"),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = NaturalText,
            modifier = Modifier
                .padding(bottom = 16.dp)
                .testTag("categories_header_title")
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopStart
        ) {
            CategoriesGrid(
                categories = categories,
                language = language,
                onCategoryClick = onCategoryClick,
                onAddClick = onAddCategory,
                onReorder = onReorder
            )
        }
    }
}

@Composable
fun CategoriesGrid(
    categories: List<com.example.data.Category>,
    language: String,
    onCategoryClick: (com.example.data.Category) -> Unit,
    onAddClick: () -> Unit,
    onReorder: (from: Int, to: Int) -> Unit
) {
    val t = { vi: String, en: String -> appText(language, vi, en) }
    val totalSlots = 12
    val cols = 3
    val totalNeeded = if (categories.size + 1 > totalSlots) categories.size + 1 else totalSlots
    val rows = (totalNeeded + cols - 1) / cols

    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var targetIndex by remember { mutableStateOf<Int?>(null) }
    val itemBounds = remember { mutableMapOf<Int, androidx.compose.ui.geometry.Rect>() }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        for (row in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                for (col in 0 until cols) {
                    val index = row * cols + col
                    Box(modifier = Modifier.weight(1f)) {
                        if (index < categories.size) {
                            val category = categories[index]
                            val logoKey = category.logo
                            val isCustom = logoKey.startsWith("/") || logoKey.startsWith("file://") || logoKey.startsWith("content://") || logoKey.contains("/")
                            
                            val isDragged = draggedIndex == index
                            val isTargeted = targetIndex == index && draggedIndex != index

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .onGloballyPositioned { coords ->
                                        itemBounds[index] = coords.boundsInWindow()
                                    }
                                    .zIndex(if (isDragged) 1f else 0f)
                                    .graphicsLayer {
                                        if (isDragged) {
                                            translationX = dragOffset.x
                                            translationY = dragOffset.y
                                            scaleX = 1.05f
                                            scaleY = 1.05f
                                            alpha = 0.9f
                                            shadowElevation = 8f
                                        } else if (isTargeted) {
                                            scaleX = 0.95f
                                            scaleY = 0.95f
                                        }
                                    }
                                    .pointerInput(categories) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { _ ->
                                                draggedIndex = index
                                                targetIndex = index
                                                dragOffset = androidx.compose.ui.geometry.Offset.Zero
                                            },
                                            onDragEnd = {
                                                if (draggedIndex != null && targetIndex != null && draggedIndex != targetIndex) {
                                                    onReorder(draggedIndex!!, targetIndex!!)
                                                }
                                                draggedIndex = null
                                                targetIndex = null
                                                dragOffset = androidx.compose.ui.geometry.Offset.Zero
                                            },
                                            onDragCancel = {
                                                draggedIndex = null
                                                targetIndex = null
                                                dragOffset = androidx.compose.ui.geometry.Offset.Zero
                                            },
                                            onDrag = { change: PointerInputChange, dragAmount: androidx.compose.ui.geometry.Offset ->
                                                change.consume()
                                                dragOffset += dragAmount
                                                
                                                val draggedBounds = itemBounds[draggedIndex]
                                                if (draggedBounds != null) {
                                                    val currentCenter = androidx.compose.ui.geometry.Offset(
                                                        x = draggedBounds.center.x + dragOffset.x,
                                                        y = draggedBounds.center.y + dragOffset.y
                                                    )
                                                    
                                                    // Find which item we are overlapping
                                                    var newTarget: Int? = null
                                                    for ((i, bounds) in itemBounds) {
                                                        if (i < categories.size && bounds.contains(currentCenter)) {
                                                            newTarget = i
                                                            break
                                                        }
                                                    }
                                                    targetIndex = newTarget ?: targetIndex
                                                }
                                            }
                                        )
                                    }
                            ) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable { if (draggedIndex == null) onCategoryClick(category) }
                                        .testTag("category_grid_item_${category.id}"),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isTargeted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                    ),
                                    border = BorderStroke(
                                        if (isTargeted) 2.dp else 1.dp, 
                                        if (isTargeted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    if (isCustom) {
                                        CategoryLogoDisplay(
                                            logoKey = logoKey,
                                            shape = RoundedCornerShape(12.dp),
                                            fillMax = true,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            CategoryLogoDisplay(
                                                logoKey = category.logo,
                                                size = 36.dp,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        } else if (index == categories.size) {
                            DashedBox(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("category_grid_add_item"),
                                onClick = onAddClick
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Create Category",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = t("Mới", "New"),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        } else {
                            DashedBox(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = onAddClick
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryDetailsView(
    category: com.example.data.Category,
    viewModel: LinkViewModel,
    onBack: () -> Unit,
    onEditLink: (SavedLink) -> Unit,
    onDeleteLink: (SavedLink) -> Unit,
    onOpenUrl: (String) -> Unit,
    onShareLink: (SavedLink) -> Unit,
    language: String,
    onPickGalleryLogo: (((String) -> Unit) -> Unit)? = null
) {
    val context = LocalContext.current
    val t = { vi: String, en: String -> appText(language, vi, en) }
    val colors = MaterialTheme.colorScheme
    val NaturalBg = colors.background
    val NaturalText = colors.onBackground
    val NaturalPrimary = colors.primary
    val NaturalSecondary = colors.secondary
    val NaturalTertiary = colors.tertiary
    val NaturalBorder = colors.outline
    val NaturalAccentChip = colors.primaryContainer

    var detailTab by remember { mutableStateOf("notes") } // "notes" or "settings"

    // Core Settings fields
    var editName by remember(category) { mutableStateOf(category.name) }
    var editLogo by remember(category) { mutableStateOf(category.logo) }
    var showLogoPicker by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Notes tab content states
    val allLinks by viewModel.uiState.collectAsStateWithLifecycle()
    var notesSearchText by remember { mutableStateOf("") }

    val categoryLinks = allLinks.filter { it.categoryId == category.id && (notesSearchText.isBlank() || it.note.contains(notesSearchText, ignoreCase = true) || it.url.contains(notesSearchText, ignoreCase = true)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NaturalBg)
            .statusBarsPadding()
    ) {
        // App top header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(Icons.Default.ArrowBack, t("Quay lại", "Go back"), tint = NaturalText)
            }

            CategoryLogoDisplay(
                logoKey = category.logo,
                size = 24.dp,
                tint = NaturalPrimary
            )

            Text(
                text = category.name,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = NaturalText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        // Two tabs selection
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surfaceVariant),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (detailTab == "notes") NaturalAccentChip else Color.Transparent)
                    .clickable { detailTab = "notes" },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = t("Ghi chú", "Notes"),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (detailTab == "notes") NaturalText else NaturalTertiary
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (detailTab == "settings") NaturalAccentChip else Color.Transparent)
                    .clickable { detailTab = "settings" },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = t("Cài đặt", "Settings"),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (detailTab == "settings") NaturalText else NaturalTertiary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Tab detail dispatching
        if (detailTab == "notes") {
            // Notes filtering view
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                // Category search bar
                OutlinedTextField(
                    value = notesSearchText,
                    onValueChange = { notesSearchText = it },
                    placeholder = { Text(searchPlaceholder(language), color = NaturalTertiary) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .testTag("category_notes_search_bar"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NaturalPrimary,
                        unfocusedBorderColor = NaturalBorder,
                        focusedContainerColor = colors.surface,
                        unfocusedContainerColor = colors.surface,
                        focusedTextColor = NaturalText,
                        unfocusedTextColor = NaturalText
                    ),
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = NaturalSecondary)
                    }
                )

                if (categoryLinks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = t("Chưa có ghi chú nào trong danh mục này.", "No notes in this category yet."),
                            fontSize = 13.sp,
                            color = NaturalTertiary,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(categoryLinks, key = { it.id }) { link ->
                            SwipeableLinkItem(
                                link = link,
                                isSelected = false,
                                isSelectionMode = false,
                                onSelectToggle = {},
                                onEnterSelectionMode = {},
                                onEdit = { onEditLink(link) },
                                onDelete = { onDeleteLink(link) },
                                onTap = { onOpenUrl(link.url) },
                                onShare = { onShareLink(link) },
                                getDomain = { viewModel.getDomainName(it) },
                                language = language
                            )
                        }
                    }
                }
            }
        } else {
            // Settings tab (Update name / logo / delete / save category metadata)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = t("Tên danh mục", "Category name"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = NaturalSecondary
                    )
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_category_name_field"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NaturalPrimary,
                            unfocusedBorderColor = NaturalBorder,
                            focusedTextColor = NaturalText,
                            unfocusedTextColor = NaturalText
                        )
                    )
                }

                // Logo field with change trigger
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = t("Biểu tượng danh mục", "Category icon"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = NaturalSecondary
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, NaturalBorder, RoundedCornerShape(12.dp))
                            .background(colors.surface)
                            .clickable { showLogoPicker = true }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val editLogoIcon = CATEGORY_LOGOS[editLogo] ?: Icons.Default.List
                                Icon(
                                    imageVector = editLogoIcon,
                                    contentDescription = null,
                                    tint = NaturalPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = LOGO_DISPLAY_NAMES[editLogo] ?: "Default Icon",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = NaturalText
                                )
                            }
                            Text(
                                text = t("Thay đổi", "Change"),
                                fontSize = 12.sp,
                                color = NaturalPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Actions Save / Delete row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = { showDeleteConfirmDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFDE8E8)),
                        border = BorderStroke(1.dp, Color(0xFFE57373)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("delete_category_button")
                    ) {
                        Icon(Icons.Default.Delete, "Delete", tint = Color(0xFFC62828))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Xóa", color = Color(0xFFC62828), fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            if (editName.isBlank()) {
                                Toast.makeText(context, t("Tên danh mục không được để trống.", "Category name cannot be empty."), Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            viewModel.updateCategory(category.copy(name = editName, logo = editLogo))
                            Toast.makeText(context, t("Đã cập nhật danh mục.", "Category updated."), Toast.LENGTH_SHORT).show()
                            onBack()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NaturalPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("save_category_button")
                    ) {
                        Text(t("Lưu thay đổi", "Save changes"), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Logo library popover Dialog
    if (showLogoPicker) {
        AlertDialog(
            onDismissRequest = { showLogoPicker = false },
            title = { Text(t("Chọn biểu tượng", "Select icon"), fontWeight = FontWeight.Bold, color = NaturalText) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onPickGalleryLogo?.invoke { savedPath ->
                                editLogo = savedPath
                                showLogoPicker = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, NaturalPrimary)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PhotoLibrary, null, tint = NaturalPrimary)
                            Text(t("Chọn từ thư viện", "Choose from Gallery"), color = NaturalPrimary, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Text(
                        "Or select standard minimalism icon:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = NaturalSecondary
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                    ) {
                        val logosList = CATEGORY_LOGOS.keys.toList()
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                        val rowCount = (logosList.size + 3) / 4
                        items(rowCount) { rowIndex ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (colIndex in 0 until 4) {
                                    val logIndex = rowIndex * 4 + colIndex
                                    val logoKey = logosList.getOrNull(logIndex)
                                    if (logoKey != null) {
                                        val optIcon = CATEGORY_LOGOS[logoKey] ?: Icons.Default.List
                                        val isChosen = editLogo == logoKey
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isChosen) NaturalAccentChip else colors.surfaceVariant)
                                                .border(1.dp, if (isChosen) NaturalPrimary else Color.Transparent, RoundedCornerShape(8.dp))
                                                .clickable {
                                                    editLogo = logoKey
                                                    showLogoPicker = false
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(optIcon, null, tint = if (isChosen) NaturalPrimary else NaturalSecondary, modifier = Modifier.size(24.dp))
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(LOGO_DISPLAY_NAMES[logoKey]?.take(6) ?: "Logo", fontSize = 8.sp, color = NaturalTertiary, maxLines = 1)
                                            }
                                        }
                                    } else {
                                        Box(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLogoPicker = false }) {
                    Text("Close", color = NaturalPrimary)
                }
            }
        )
    }

    // Double option deletion dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(t("Xóa danh mục?", "Delete category?"), color = NaturalText, fontWeight = FontWeight.Bold) },
            text = { Text(t("Chọn cách xử lý các ghi chú đang thuộc danh mục này:", "Choose what to do with notes in this category:"), color = NaturalTertiary) },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Button(
                        onClick = {
                            showDeleteConfirmDialog = false
                            viewModel.deleteCategoryOnly(category)
                            Toast.makeText(context, t("Đã xóa danh mục và giữ lại ghi chú.", "Deleted category and kept notes."), Toast.LENGTH_SHORT).show()
                            onBack()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NaturalSecondary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(t("Chỉ xóa danh mục", "Delete category only"), color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            showDeleteConfirmDialog = false
                            viewModel.deleteCategoryAndAllContent(category)
                            Toast.makeText(context, t("Đã xóa danh mục và toàn bộ ghi chú liên quan.", "Deleted category and all related notes."), Toast.LENGTH_SHORT).show()
                            onBack()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(t("Xóa danh mục và toàn bộ ghi chú", "Delete category and all notes"), color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    TextButton(
                        onClick = { showDeleteConfirmDialog = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(t("Hủy", "Cancel"), color = NaturalPrimary, textAlign = TextAlign.Center)
                    }
                }
            },
            dismissButton = {}
        )
    }
}

// Global helper functions for CSV Export
fun generateCsvContent(
    links: List<com.example.data.SavedLink>,
    categories: List<com.example.data.Category>
): String {
    val sb = StringBuilder()
    // Add UTF-8 BOM (Byte Order Mark) to force Microsoft Excel / Google Sheets to recognize Vietnamese Unicode
    sb.append('\uFEFF')
    
    // Header
    sb.append("Link,Note,Category\r\n")
    
    // Row content
    links.forEach { link ->
        val catName = if (link.categoryId == 0) {
            "General"
        } else {
            categories.find { it.id == link.categoryId }?.name ?: "General"
        }
        
        sb.append(escapeCsvField(link.url)).append(",")
          .append(escapeCsvField(link.note)).append(",")
          .append(escapeCsvField(catName)).append("\r\n")
    }
    
    return sb.toString()
}

fun escapeCsvField(field: String): String {
    if (field.isEmpty()) return ""
    var needsQuotes = false
    var escaped = field
    if (escaped.contains("\"")) {
        escaped = escaped.replace("\"", "\"\"")
        needsQuotes = true
    }
    if (escaped.contains(",") || escaped.contains("\n") || escaped.contains("\r") || escaped.contains(";")) {
        needsQuotes = true
    }
    return if (needsQuotes) {
        "\"$escaped\""
    } else {
        escaped
    }
}

sealed class CsvImportResult {
    data class Success(val rows: List<ImportedLinkDraft>) : CsvImportResult()
    data class Error(val message: String) : CsvImportResult()
}

fun parseImportedCsv(
    csvText: String,
    categories: List<com.example.data.Category>
): CsvImportResult {
    val rows = parseCsvRows(csvText.removePrefix("﻿"))
    if (rows.isEmpty()) {
        return CsvImportResult.Error("File CSV đang trống.")
    }

    val header = rows.first().map { it.trim().removePrefix("﻿") }
    if (header != listOf("Link", "Note", "Category")) {
        return CsvImportResult.Error("File CSV sai định dạng. Dòng đầu tiên phải là: Link,Note,Category")
    }

    val importedRows = mutableListOf<ImportedLinkDraft>()
    rows.drop(1).forEachIndexed { index, row ->
        if (row.size != 3) {
            return CsvImportResult.Error("Dòng ${index + 2} không đúng 3 cột Link, Note, Category.")
        }
        val url = row[0].trim()
        if (url.isBlank()) {
            return CsvImportResult.Error("Dòng ${index + 2} thiếu đường dẫn.")
        }
        val categoryName = row[2].trim()
        val categoryId = categories.firstOrNull { it.name.equals(categoryName, ignoreCase = true) }?.id ?: 0
        importedRows += ImportedLinkDraft(
            url = url,
            note = row[1],
            categoryId = categoryId
        )
    }

    if (importedRows.isEmpty()) {
        return CsvImportResult.Error("File CSV không có dữ liệu để nhập.")
    }

    return CsvImportResult.Success(importedRows)
}

fun parseCsvRows(csvText: String): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    val currentRow = mutableListOf<String>()
    val currentField = StringBuilder()
    var inQuotes = false
    var index = 0

    while (index < csvText.length) {
        val char = csvText[index]
        when {
            char == '"' && inQuotes && index + 1 < csvText.length && csvText[index + 1] == '"' -> {
                currentField.append('"')
                index++
            }
            char == '"' -> inQuotes = !inQuotes
            char == ',' && !inQuotes -> {
                currentRow += currentField.toString()
                currentField.clear()
            }
            (char == '\n' || char == '\r') && !inQuotes -> {
                currentRow += currentField.toString()
                currentField.clear()
                if (currentRow.any { it.isNotEmpty() }) {
                    rows += currentRow.toList()
                }
                currentRow.clear()
                if (char == '\r' && index + 1 < csvText.length && csvText[index + 1] == '\n') {
                    index++
                }
            }
            else -> currentField.append(char)
        }
        index++
    }

    if (inQuotes) {
        throw IllegalArgumentException("File CSV có dấu ngoặc kép chưa được đóng.")
    }

    currentRow += currentField.toString()
    if (currentRow.any { it.isNotEmpty() }) {
        rows += currentRow.toList()
    }

    return rows
}

