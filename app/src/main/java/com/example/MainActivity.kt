package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import coil.compose.rememberAsyncImagePainter
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import androidx.compose.ui.text.font.FontStyle
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: LinkViewModel
    private var isFromShareIntent = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize repository and viewmodel using constructor injection
        val database = LinkDatabase.getDatabase(this)
        val repository = LinkRepository(database.savedLinkDao, database.categoryDao)
        val factory = LinkViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[LinkViewModel::class.java]

        // Read intent data if app was started via Share Sheet
        handleSharedIntent(intent)

        setContent {
            val sharedPrefs = LocalContext.current.getSharedPreferences("linkvault_prefs", Context.MODE_PRIVATE)

            // Setup state synced with SharedPreferences
            var isLoggedIn by remember { mutableStateOf(sharedPrefs.getBoolean("is_logged_in", false)) }
            var userEmail by remember { mutableStateOf(sharedPrefs.getString("user_email", "") ?: "") }
            var themeMode by remember { mutableStateOf(sharedPrefs.getString("theme_mode", "light") ?: "light") }
            var themeSelection by remember { mutableStateOf(sharedPrefs.getString("theme_name", "denim") ?: "denim") }

            val isSystemDark = isSystemInDarkTheme()
            val isDark = when(themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemDark
            }

            DisposableEffect(isDark) {
                enableEdgeToEdge(
                    statusBarStyle = if (isDark) {
                        androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (isDark) {
                        androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    }
                )
                onDispose {}
            }

            MyApplicationTheme(
                darkTheme = isDark,
                themeName = themeSelection
            ) {
                if (!isLoggedIn) {
                    LoginScreen { email ->
                        sharedPrefs.edit()
                            .putBoolean("is_logged_in", true)
                            .putString("user_email", email)
                            .apply()
                        userEmail = email
                        isLoggedIn = true
                    }
                } else {
                    MainAppScreen(
                        viewModel = viewModel,
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
                viewModel.setSharedText(sharedText)
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
    onLogout: () -> Unit,
    isFromShareIntent: Boolean = false,
    onFinishActivity: () -> Unit = {}
) {
    val context = LocalContext.current

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
                            text = "Links",
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
                            text = "Categories",
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
                            text = "Settings",
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
                            Toast.makeText(context, "Deleted link successfully!", Toast.LENGTH_SHORT).show()
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
                                    Toast.makeText(context, "No browser app detected.", Toast.LENGTH_SHORT).show()
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
                        }
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
                                Toast.makeText(context, "Deleted link successfully!", Toast.LENGTH_SHORT).show()
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
                                        Toast.makeText(context, "No browser app detected.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    } else {
                        CategoriesDirectoryView(
                            categories = categories,
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
                        categories = categories
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
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .imePadding() // Dynamically elevates card completely above any virtual keyboard
                                .clickable(enabled = false, onClick = {}) // stop click propagation
                                .testTag("add_link_form_container"),
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
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
                                Text(
                                    text = "URL / Link",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NaturalSecondary
                                )

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
                                                Toast.makeText(context, "Please enter a valid link URL.", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            if (editingLink != null) {
                                                viewModel.updateLink(editingLink!!.copy(
                                                    url = inputUrl,
                                                    note = inputNote,
                                                    categoryId = selectedCategoryId
                                                ))
                                                Toast.makeText(context, "Link edited successfully!", Toast.LENGTH_SHORT).show()
                                            } else {
                                                viewModel.saveLink(
                                                    title = "",
                                                    url = inputUrl,
                                                    note = inputNote,
                                                    tags = emptyList(),
                                                    categoryId = selectedCategoryId
                                                )
                                                Toast.makeText(context, "Link saved with note successfully!", Toast.LENGTH_SHORT).show()
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
                                            text = "Save",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }

                            // Supplemental notes field
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Your Note (Ghi chú)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NaturalSecondary
                                )
                                OutlinedTextField(
                                    value = inputNote,
                                    onValueChange = { if (it.length <= 60) inputNote = it },
                                    placeholder = { Text("Please take note here...", color = NaturalTertiary) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(110.dp)
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
                                    maxLines = 4
                                )
                            }

                            // Category selection Dropdown
                            var dropdownExpanded by remember { mutableStateOf(false) }
                            val activeCategory = categories.find { it.id == selectedCategoryId }

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Category (Danh mục)",
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
                                        .clickable { dropdownExpanded = true }
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
                                                    fontWeight = FontWeight.Medium
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.List,
                                                    contentDescription = null,
                                                    tint = NaturalTertiary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Text(
                                                    text = "None (Không có)",
                                                    fontSize = 14.sp,
                                                    color = NaturalTertiary
                                                )
                                            }
                                        }
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = "Expand categories",
                                            tint = NaturalSecondary
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = dropdownExpanded,
                                        onDismissRequest = { dropdownExpanded = false },
                                        modifier = Modifier
                                            .fillMaxWidth(0.85f)
                                            .background(colors.surface)
                                            .border(1.dp, NaturalBorder, RoundedCornerShape(8.dp))
                                    ) {
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Default.List, null, tint = NaturalTertiary)
                                                    Text("None (Không có)", color = NaturalText)
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
                                                        CategoryLogoDisplay(logoKey = category.logo, size = 24.dp, tint = NaturalPrimary)
                                                        Text(category.name, color = NaturalText)
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
                        }
                    }
                }

                // Popup Save / Discard alert if changes are pending
                if (showDiscardConfirmation) {
                    AlertDialog(
                        onDismissRequest = { showDiscardConfirmation = false },
                        title = { Text("Save Changes?", color = NaturalText, fontWeight = FontWeight.Bold) },
                        text = { Text("You have unsaved details in your draft form. Do you want to save or discard them?", color = NaturalTertiary) },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showDiscardConfirmation = false
                                    if (inputUrl.isBlank()) {
                                        Toast.makeText(context, "Please enter a valid link URL.", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    if (editingLink != null) {
                                        viewModel.updateLink(editingLink!!.copy(
                                            url = inputUrl,
                                            note = inputNote,
                                            categoryId = selectedCategoryId
                                        ))
                                        Toast.makeText(context, "Link edited successfully!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        viewModel.saveLink(
                                            title = "",
                                            url = inputUrl,
                                            note = inputNote,
                                            tags = emptyList(),
                                            categoryId = selectedCategoryId
                                        )
                                        Toast.makeText(context, "Link saved with note successfully!", Toast.LENGTH_SHORT).show()
                                    }
                                    resetForm()
                                    if (isFromShareIntent) {
                                        onFinishActivity()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NaturalPrimary)
                            ) {
                                Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
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
                                Text("Discard", color = Color.Red, fontWeight = FontWeight.Bold)
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
                    onDismissRequest = { showAddCategoryForm = false },
                    title = { Text("Create Category (Tạo Danh mục mới)", fontWeight = FontWeight.Bold, color = NaturalText) },
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
                                label = { Text("Category Name (Tên danh mục)") },
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
                                text = "Logo / Icon Selection",
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
                                        text = "Change",
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
                                    Toast.makeText(context, "Please enter a category name.", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                viewModel.saveCategory(categoryDraftName, categoryDraftLogo)
                                Toast.makeText(context, "Category created successfully!", Toast.LENGTH_SHORT).show()
                                showAddCategoryForm = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NaturalPrimary)
                        ) {
                            Text("Create", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showAddCategoryForm = false }
                        ) {
                            Text("Cancel", color = NaturalTertiary)
                        }
                    }
                )
            }

            if (showLogoLibraryDialog) {
                AlertDialog(
                    onDismissRequest = { showLogoLibraryDialog = false },
                    title = { Text("Choose Minimalism Icon", fontWeight = FontWeight.Bold, color = NaturalText) },
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
                                    Text("Choose from Gallery", color = NaturalPrimary, fontWeight = FontWeight.SemiBold)
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
                            Text("Close", color = NaturalPrimary)
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
    onEditLink: (SavedLink) -> Unit
) {
    val context = LocalContext.current
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
                        text = "${selectedLinks.size} Selected",
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
                        Text("Select All", color = NaturalPrimary, fontWeight = FontWeight.Bold)
                    }

                    IconButton(
                        onClick = {
                            if (selectedLinks.isEmpty()) {
                                Toast.makeText(context, "No links selected.", Toast.LENGTH_SHORT).show()
                                return@IconButton
                            }
                            selectedLinks.forEach { linkItem ->
                                onDeleteLink(linkItem)
                            }
                            Toast.makeText(context, "Deleted ${selectedLinks.size} links.", Toast.LENGTH_SHORT).show()
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
                            text = "Safe storage for bookmarks & shared feeds",
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
                                text = "Search saved links...",
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
                                text = { Text("Sort by Date (Recent)", color = NaturalText) },
                                onClick = {
                                    onUpdateSort(SortOrder.RECENT)
                                    showSortDropdown = false
                                },
                                leadingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = NaturalSecondary) }
                            )
                            DropdownMenuItem(
                                text = { Text("Sort Alphabetically (A-Z)", color = NaturalText) },
                                onClick = {
                                    onUpdateSort(SortOrder.TITLE)
                                    showSortDropdown = false
                                },
                                leadingIcon = { Icon(Icons.Default.Home, contentDescription = null, tint = NaturalSecondary) }
                            )
                            DropdownMenuItem(
                                text = { Text("Sort by Domain Host", color = NaturalText) },
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
                text = if (searchQuery.isNotEmpty()) "FILTERED RESULTS" else "RECENTLY SAVED",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = NaturalTertiary,
                letterSpacing = 1.sp
            )

            Text(
                text = "${links.size} link" + if (links.size != 1) "s" else "",
                fontSize = 11.sp,
                color = NaturalTertiary
            )
        }

        // List Content
        if (links.isEmpty()) {
            EmptyListPlaceholder()
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
                        getDomain = getDomain
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
    getDomain: (String) -> String
) {
    val density = LocalDensity.current
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
                    Text("Edit", fontSize = 11.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
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
                    Text("Delete", fontSize = 11.sp, color = Color(0xFFC62828), fontWeight = FontWeight.Bold)
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
                        text = if (link.note.isNotEmpty()) link.note else "Blank Note",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = NaturalText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Domain and timestamp below
                    Text(
                        text = "${getDomain(link.url)} • ${formatRelativeTime(link.timestamp)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = NaturalTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyListPlaceholder() {
    val colors = MaterialTheme.colorScheme
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
            text = "No Links Saved",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = NaturalText
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Go ahead and share any browser URL, social post, or blog page from other apps to LinkVault. Tap + to save one manually so you can categorize it with unique notes!",
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
    categories: List<com.example.data.Category>
) {
    // Dynamic override
    val colors = MaterialTheme.colorScheme
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
            text = "Settings",
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
                        text = "GOOGLE ACCOUNT",
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
                    Text("Logout", color = Color(0xFFC62828), fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                Text(
                    text = "APPEARANCE MODE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = NaturalPrimary,
                    letterSpacing = 1.sp
                )

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
                            "Light Mode",
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
                            "Dark Mode",
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
                            "System",
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
                Text(
                    text = "THEME STYLES",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = NaturalPrimary,
                    letterSpacing = 1.sp
                )

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

        // SharedPreferences Sync Integration
        val context = LocalContext.current
        val sharedPrefs = remember { context.getSharedPreferences("linkvault_prefs", Context.MODE_PRIVATE) }
        var syncStatus by remember { mutableStateOf(sharedPrefs.getString("google_sync_status", "disconnected") ?: "disconnected") }
        var sheetUrl by remember { mutableStateOf(sharedPrefs.getString("google_sheet_url", "") ?: "") }
        var sheetFileId by remember { mutableStateOf(sharedPrefs.getString("google_sheet_file_id", "") ?: "") }
        var sharedPublicly by remember { mutableStateOf(sharedPrefs.getBoolean("google_shared_publicly", false)) }
        var oauthToken by remember { mutableStateOf(sharedPrefs.getString("google_oauth_token", "") ?: "") }

        var showSyncDialog by remember { mutableStateOf(false) }
        val coroutineScope = rememberCoroutineScope()

        // Google Drive Integration Card
        Card(
            modifier = Modifier.fillMaxWidth().testTag("google_drive_card"),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, NaturalBorder)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "GOOGLE DRIVE INTEGRATION",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = NaturalPrimary,
                    letterSpacing = 1.sp
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (syncStatus == "synced") Color(0xFFE8F5E9) else colors.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (syncStatus == "synced") Icons.Default.CloudDone else Icons.Default.CloudQueue,
                            contentDescription = null,
                            tint = if (syncStatus == "synced") Color(0xFF2E7D32) else NaturalTertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (syncStatus == "synced") "Synced Successfully" else "No Spreadsheet Synced",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = NaturalText
                        )
                        Text(
                            text = if (syncStatus == "synced") {
                                if (sharedPublicly) "Status: Shared to anyone with link" else "Status: Private to me"
                            } else {
                                "Export your saved links directly to Google Drive"
                            },
                            fontSize = 11.sp,
                            color = NaturalTertiary
                        )
                    }
                }

                if (syncStatus == "synced") {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Sheet: LinkVault",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = NaturalText
                    )
                    Text(
                        text = sheetUrl,
                        fontSize = 11.sp,
                        color = NaturalPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable {
                            try {
                                val openIntent = Intent(Intent.ACTION_VIEW, Uri.parse(sheetUrl))
                                context.startActivity(openIntent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Cannot open URL", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Sync Button
                    Button(
                        onClick = { showSyncDialog = true },
                        modifier = Modifier.weight(1f).height(40.dp).testTag("sync_to_drive_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = NaturalPrimary),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (syncStatus == "synced") "Sync Now" else "Sync to Drive",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // Share Button
                    Button(
                        onClick = {
                            if (syncStatus != "synced") return@Button
                            
                            coroutineScope.launch {
                                var shareSucceeded = true
                                if (oauthToken.isNotBlank() && !sheetFileId.contains("mock")) {
                                    val result = com.example.data.GoogleWorkspaceSyncManager.shareFileToAnyoneReal(
                                        token = oauthToken,
                                        fileId = sheetFileId
                                    )
                                    if (result is com.example.data.ShareResult.Failure) {
                                        shareSucceeded = false
                                        Toast.makeText(context, "API Share failed: ${result.message}", Toast.LENGTH_LONG).show()
                                    }
                                }

                                if (shareSucceeded) {
                                    sharedPrefs.edit().putBoolean("google_shared_publicly", true).apply()
                                    sharedPublicly = true
                                    Toast.makeText(context, "Updated Sheet permissions to: Anyone with link!", Toast.LENGTH_SHORT).show()
                                    
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "LinkVault Spreadsheet Index")
                                        putExtra(Intent.EXTRA_TEXT, "Here is my customized LinkVault Index Spreadsheet:\n$sheetUrl")
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share LinkVault Index Direct"))
                                }
                            }
                        },
                        enabled = syncStatus == "synced",
                        modifier = Modifier.weight(1f).height(40.dp).testTag("share_drive_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (syncStatus == "synced") Color(0xFFE3F2FD) else colors.surfaceVariant,
                            disabledContainerColor = colors.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (syncStatus == "synced") Color(0xFF1565C0) else NaturalTertiary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Share Index",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (syncStatus == "synced") Color(0xFF1565C0) else NaturalTertiary
                        )
                    }
                }
            }
        }

        // Sync dialog options
        if (showSyncDialog) {
            var inputToken by remember { mutableStateOf(oauthToken) }
            var isExecutingSync by remember { mutableStateOf(false) }
            var errorMessage by remember { mutableStateOf<String?>(null) }

            AlertDialog(
                onDismissRequest = { if (!isExecutingSync) showSyncDialog = false },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CloudUpload, null, tint = NaturalPrimary)
                        Text("Drive Workspace Synchronize", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = NaturalText)
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "This automatically creates a Google Sheet named \"LinkVault\" in your Google Drive with columns: Link / Note / Category.",
                            fontSize = 13.sp,
                            color = NaturalText
                        )

                        Text(
                            text = "To sync live using your true storage, enter a Google OAuth Access Token below. Otherwise, use the built-in Sandbox simulator.",
                            fontSize = 11.sp,
                            color = NaturalTertiary
                        )

                        BasicTextField(
                            value = inputToken,
                            onValueChange = { inputToken = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .border(1.dp, NaturalBorder, RoundedCornerShape(8.dp))
                                .background(colors.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 14.dp)
                                .testTag("oauth_token_field"),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = NaturalText),
                            decorationBox = { innerTextField ->
                                if (inputToken.isEmpty()) {
                                    Text("PASTE GOOGLE OAUTH TOKEN HERE (OPTIONAL)", fontSize = 10.sp, color = NaturalTertiary)
                                }
                                innerTextField()
                            }
                        )

                        if (errorMessage != null) {
                            Text(
                                text = errorMessage!!,
                                color = Color(0xFFC62828),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (isExecutingSync) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = NaturalPrimary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Connecting Workspace APIs...", fontSize = 12.sp, color = NaturalPrimary, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                },
                confirmButton = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Sandbox offline Option
                        TextButton(
                            onClick = {
                                if (isExecutingSync) return@TextButton
                                val mockFileId = "1_vault_sheet_mock_" + (1000..9999).random()
                                val mockUrl = "https://docs.google.com/spreadsheets/d/$mockFileId/view"
                                
                                sharedPrefs.edit()
                                    .putString("google_sync_status", "synced")
                                    .putString("google_sheet_url", mockUrl)
                                    .putString("google_sheet_file_id", mockFileId)
                                    .apply()
                                
                                syncStatus = "synced"
                                sheetUrl = mockUrl
                                sheetFileId = mockFileId
                                errorMessage = null
                                showSyncDialog = false
                                Toast.makeText(context, "Successfully exported ${links.size} items to LinkVault (Sandbox)!", Toast.LENGTH_LONG).show()
                            },
                            enabled = !isExecutingSync,
                            colors = ButtonDefaults.textButtonColors(contentColor = NaturalPrimary)
                        ) {
                            Text("Simulate", fontWeight = FontWeight.Bold)
                        }

                        // Real sync option calling workspace API via Retrofit
                        Button(
                            onClick = {
                                if (isExecutingSync) return@Button
                                if (inputToken.isBlank()) {
                                    errorMessage = "Please enter an OAuth access token to perform real API requests"
                                    return@Button
                                }
                                isExecutingSync = true
                                errorMessage = null

                                coroutineScope.launch {
                                    val result = com.example.data.GoogleWorkspaceSyncManager.syncToDriveReal(
                                        token = inputToken.trim(),
                                        links = links,
                                        categories = categories
                                    )

                                    isExecutingSync = false
                                    when (result) {
                                        is com.example.data.SyncResult.Success -> {
                                            sharedPrefs.edit()
                                                .putString("google_sync_status", "synced")
                                                .putString("google_sheet_url", result.sheetUrl)
                                                .putString("google_sheet_file_id", result.fileId)
                                                .putString("google_oauth_token", inputToken.trim())
                                                .apply()
                                            
                                            syncStatus = "synced"
                                            sheetUrl = result.sheetUrl
                                            sheetFileId = result.fileId
                                            oauthToken = inputToken.trim()
                                            showSyncDialog = false
                                            Toast.makeText(context, "Live Google Workspace Synchronization Successful!", Toast.LENGTH_LONG).show()
                                        }
                                        is com.example.data.SyncResult.Failure -> {
                                            errorMessage = result.message
                                        }
                                    }
                                }
                            },
                            enabled = !isExecutingSync,
                            colors = ButtonDefaults.buttonColors(containerColor = NaturalPrimary)
                        ) {
                            Text("Real API Sync", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showSyncDialog = false },
                        enabled = !isExecutingSync
                    ) {
                        Text("Cancel", color = NaturalTertiary)
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

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "USER GUIDE",
                        fontSize = 11.sp,
                        color = NaturalPrimary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Learn how to use gestures to manage links",
                        fontSize = 13.sp,
                        color = NaturalTertiary
                    )
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Open guide",
                    tint = NaturalTertiary
                )
            }
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
                    Text("User Guide", fontWeight = FontWeight.Bold, color = NaturalText)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "LinkVault is designed with intuitive gestures to help you manage your links incredibly fast:",
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
                            Text("Swipe Right", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = NaturalText)
                            Text("Edit and update notes", fontSize = 12.sp, color = NaturalTertiary)
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
                            Text("Swipe Left", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = NaturalText)
                            Text("Fast deletion of notes & categories", fontSize = 12.sp, color = NaturalTertiary)
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
                            Text("Long Press", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = NaturalText)
                            Text("Select multiple notes to bulk delete or move", fontSize = 12.sp, color = NaturalTertiary)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showUserGuideDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = NaturalPrimary)
                ) {
                    Text("Got It", color = Color.White, fontWeight = FontWeight.Bold)
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

// Relative date converter returning user-friendly descriptions
fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        diff < 0 -> "Just now"
        seconds < 60 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days == 1L -> "Yesterday"
        days < 7 -> "${days}d ago"
        else -> {
            val sdf = java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault())
            sdf.format(java.util.Date(timestamp))
        }
    }
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
    onCategoryClick: (com.example.data.Category) -> Unit,
    onAddCategory: () -> Unit,
    onReorder: (from: Int, to: Int) -> Unit
) {
    val colors = MaterialTheme.colorScheme
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
            text = "Categories",
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
    onCategoryClick: (com.example.data.Category) -> Unit,
    onAddClick: () -> Unit,
    onReorder: (from: Int, to: Int) -> Unit
) {
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
                                        text = "New",
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
    onPickGalleryLogo: (((String) -> Unit) -> Unit)? = null
) {
    val context = LocalContext.current
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
                Icon(Icons.Default.ArrowBack, "Go back", tint = NaturalText)
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
                    text = "Notes",
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
                    text = "Settings",
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
                    placeholder = { Text("Search assigned notes...", color = NaturalTertiary) },
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
                            text = "No notes assigned to this category.",
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
                                getDomain = { viewModel.getDomainName(it) }
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
                        text = "Category Name",
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
                        text = "Category Icon (Minimalist)",
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
                                text = "Change",
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
                        Text("Delete", color = Color(0xFFC62828), fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            if (editName.isBlank()) {
                                Toast.makeText(context, "Category name cannot be empty.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            viewModel.updateCategory(category.copy(name = editName, logo = editLogo))
                            Toast.makeText(context, "Category updated successfully!", Toast.LENGTH_SHORT).show()
                            onBack()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NaturalPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("save_category_button")
                    ) {
                        Text("Save Changes", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Logo library popover Dialog
    if (showLogoPicker) {
        AlertDialog(
            onDismissRequest = { showLogoPicker = false },
            title = { Text("Select Minimalism Logo", fontWeight = FontWeight.Bold, color = NaturalText) },
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
                            Text("Choose from Gallery", color = NaturalPrimary, fontWeight = FontWeight.SemiBold)
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
            title = { Text("Delete Category?", color = NaturalText, fontWeight = FontWeight.Bold) },
            text = { Text("Choose how you want to handle the links assigned to this category:", color = NaturalTertiary) },
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
                            Toast.makeText(context, "Deleted category successfully (Notes preserved).", Toast.LENGTH_SHORT).show()
                            onBack()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NaturalSecondary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Delete Category Only", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            showDeleteConfirmDialog = false
                            viewModel.deleteCategoryAndAllContent(category)
                            Toast.makeText(context, "Deleted category and associated content successfully.", Toast.LENGTH_SHORT).show()
                            onBack()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Delete Category & All Content", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    TextButton(
                        onClick = { showDeleteConfirmDialog = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel", color = NaturalPrimary, textAlign = TextAlign.Center)
                    }
                }
            },
            dismissButton = {}
        )
    }
}

