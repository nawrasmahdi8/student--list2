package com.example

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.StudentEntity
import com.example.ui.RegistryViewModel
import com.example.ui.P2PSharingScreen
import com.example.DataStoreManager
import com.example.ui.theme.MyApplicationTheme
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.net.NetworkInterface
// QR imports
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder

enum class Screen(val titleArabic: String) {
    Search("البحث"),
    Add("إضافة"),
    Records("القيود"),
    Duplicates("تكرار"),
    Review("مراجعة"),
    CSV("بيانات"),
    P2P("مشاركة سريعة"),
    Settings("الإعدادات")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val registryViewModel: RegistryViewModel = viewModel(
                factory = RegistryViewModel.Factory(application)
            )
            val isDarkTheme by registryViewModel.isNightMode.collectAsState()

            MyApplicationTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAppFrame(registryViewModel)
                }
            }
        }
    }
}

@Composable
fun ModeSelectionScreen(onModeSelected: (Boolean) -> Unit, onLoginRequested: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(stringResource(R.string.welcome_to_registry), fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = { onModeSelected(true) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("guest_login_button")
            ) {
                Text(stringResource(R.string.guest_login), fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { onLoginRequested() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("admin_login_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text(stringResource(R.string.user_login), fontSize = 18.sp)
            }
        }
        
        Text(
            text = stringResource(R.string.created_by),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(bottom = 16.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun AdminLoginFlow(viewModel: RegistryViewModel) {
    val context = LocalContext.current
    val dataStoreManager = remember { DataStoreManager(context) }
    val savedData by dataStoreManager.registrationData.collectAsState(initial = emptyMap())
    val coroutineScope = rememberCoroutineScope()
    
    var step by remember { mutableStateOf(0) }
    var phoneNumber by remember { mutableStateOf("") }
    var registrationData by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    
    LaunchedEffect(savedData) {
        if (savedData["phoneNumber"]?.isNotEmpty() == true) {
            phoneNumber = savedData["phoneNumber"]!!
            registrationData = Triple(savedData["schoolName"]!!, savedData["province"]!!, savedData["managerName"]!!)
            step = 1
        }
    }
    
    when(step) {
        0 -> {
            val (school, prov, name) = registrationData ?: Triple("", "", "")
            ManagerRegistrationScreen(
                onRegister = { school, prov, name, phone ->
                    phoneNumber = phone
                    registrationData = Triple(school, prov, name)
                    coroutineScope.launch(Dispatchers.IO) {
                        dataStoreManager.saveRegistrationData(school, prov, name, phone)
                    }
                    viewModel.resendOtp(phone)
                    step = 1
                },
                initialSchoolName = school,
                initialProvince = prov,
                initialManagerName = name
            )
        }
        1 -> OtpVerificationScreen(
            phoneNumber = viewModel.formatPhoneNumber(phoneNumber), 
            viewModel = viewModel, 
            onVerified = {
                val (school, prov, name) = registrationData!!
                viewModel.registerManager(school, prov, name, phoneNumber) { success ->
                    if (success) {
                        coroutineScope.launch(Dispatchers.IO) {
                            dataStoreManager.saveRegistrationData("", "", "", "")
                        }
                        Toast.makeText(context, "تم تسجيل البيانات بنجاح في قاعدة البيانات", Toast.LENGTH_LONG).show()
                        viewModel.setModeSelected(false)
                        viewModel.isLoginScreenVisible.value = false
                    }
                }
            },
            onEditNumber = { step = 0 }
        )
    }
}

@Composable
fun MainAppFrame(viewModel: RegistryViewModel) {
    // Determine the gateway state
    var entranceScreen by remember { mutableStateOf("app") } // "app"
    
    when(entranceScreen) {
        "app" -> {
            AppContent(viewModel)
        }
    }
}



@Composable
fun AppContent(viewModel: RegistryViewModel) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    var currentScreen by remember { mutableStateOf(Screen.Search) }
    val showRecords by viewModel.showRecordsScreen.collectAsState()
    val showDuplicates by viewModel.showDuplicatesScreen.collectAsState()
    val availableScreens = remember(showRecords, showDuplicates) {
        Screen.values().filter { screen ->
            if (screen == Screen.Records && !showRecords) false
            else if (screen == Screen.Duplicates && !showDuplicates) false
            else true
        }
    }

    // Auto-switch away if current screen becomes disabled
    LaunchedEffect(availableScreens, currentScreen) {
        if (!availableScreens.contains(currentScreen)) {
            currentScreen = Screen.Search
        }
    }

    // Floating full-screen photo viewer
    var previewPhotoPath by remember { mutableStateOf<String?>(null) }
    
    if (previewPhotoPath != null) {
        Dialog(onDismissRequest = { previewPhotoPath = null }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(Color.Black, shape = RoundedCornerShape(16.dp))
                    .padding(8.dp)
            ) {
                LocalImageLoader(
                    filePath = previewPhotoPath!!,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                )
                IconButton(
                    onClick = {
                        val file = File(previewPhotoPath!!)
                        if (file.exists()) {
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "image/*"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "مشاركة صورة الطالب"))
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Filled.Share, contentDescription = "مشاركة", tint = Color.White)
                }

                IconButton(
                    onClick = { previewPhotoPath = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "إغلاق", tint = Color.White)
                }
            }
        }
    }
    
    // Alert dialogs observed from viewModel
    val validationErr by viewModel.validationError.collectAsState()
    if (validationErr != null) {
        AlertDialog(
            onDismissRequest = { viewModel.validationError.value = null },
            confirmButton = {
                TextButton(onClick = { viewModel.validationError.value = null }) {
                    Text("موافق", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(validationErr!!.title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Text(validationErr!!.message, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            if (!isLandscape) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    tonalElevation = 8.dp
                ) {
                    availableScreens.forEach { screen ->
                        val selected = currentScreen == screen
                        NavigationBarItem(
                            selected = selected,
                            onClick = { currentScreen = screen },
                            icon = {
                                Icon(
                                    imageVector = getScreenIcon(screen),
                                    contentDescription = screen.titleArabic
                                )
                            },
                            label = if (selected) {
                                { Text(screen.titleArabic, fontSize = 11.sp) }
                            } else null,
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isLandscape) {
                NavigationRail(
                    modifier = Modifier.fillMaxHeight(),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    availableScreens.forEach { screen ->
                        NavigationRailItem(
                            selected = currentScreen == screen,
                            onClick = { currentScreen = screen },
                            icon = {
                                Icon(
                                    imageVector = getScreenIcon(screen),
                                    contentDescription = screen.titleArabic
                                )
                            },
                            label = { Text(screen.titleArabic, fontSize = 12.sp) }
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                Crossfade(targetState = currentScreen, label = "ScreenTransition") { screen ->
                    when (screen) {
                        Screen.Search -> SearchScreen(viewModel, onImageClick = { previewPhotoPath = it })
                        Screen.Add -> AddStudentScreen(viewModel, onImageClick = { previewPhotoPath = it })
                        Screen.Records -> RecordsScreen(viewModel, onImageClick = { previewPhotoPath = it }, onNavigate = { currentScreen = it })
                        Screen.Duplicates -> DuplicatesScreen(viewModel, onImageClick = { previewPhotoPath = it })
                        Screen.Review -> ReviewScreen(viewModel, onImageClick = { previewPhotoPath = it })
                        Screen.CSV -> CSVScreen(viewModel)
                        Screen.P2P -> P2PSharingScreen(viewModel)
                        Screen.Settings -> SettingsScreen(viewModel)
                    }
                }
            }
        }
    }
}




@Composable
fun ReviewScreen(viewModel: RegistryViewModel, onImageClick: (String) -> Unit) {
    val allStudents by viewModel.allStudents.collectAsState()
    val reviewList = remember(allStudents) {
        allStudents.filter { it.name.isBlank() || it.registrationId.isBlank() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.End
    ) {
        Text(
            "قائمة السجلات التي تحتاج مراجعة",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (reviewList.isEmpty()) {
            Text("لا توجد سجلات تحتاج إلى مراجعة حالياً.", color = MaterialTheme.colorScheme.outline)
        } else {
            // Reusing a list display composable if exists, or simple LazyColumn
            // Assuming StudentList or similar exists, but I will use a simple LazyColumn for now
            androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(reviewList) { student ->
                    // Simplified display for now
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(text = "رقم القيد: ${student.registrationId}, الاسم: ${student.name}", modifier = Modifier.padding(16.dp))
                    }
                }
            }
        }
    }
}

private fun getScreenIcon(screen: Screen): androidx.compose.ui.graphics.vector.ImageVector {
    return when (screen) {
        Screen.Search -> Icons.Filled.Search
        Screen.Add -> Icons.Filled.Add
        Screen.Records -> Icons.Filled.Home
        Screen.Duplicates -> Icons.Filled.Warning
        Screen.Review -> Icons.Filled.Info
        Screen.CSV -> Icons.Filled.Share
        Screen.P2P -> Icons.Filled.Send
        Screen.Settings -> Icons.Filled.Settings
    }
}

// Camera photo display box loader directly using BitmapFactory
@Composable
fun LocalImageLoader(filePath: String, modifier: Modifier = Modifier) {
    val bitmap = remember(filePath) {
        try {
            BitmapFactory.decodeFile(filePath)
        } catch (e: Exception) {
            null
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "صورة القيد",
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text("لا تتوفر صورة", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

// -------------------------------------------------------------
// 1. Search Screen Implementation
// -------------------------------------------------------------
@Composable
fun SearchScreen(viewModel: RegistryViewModel, onImageClick: (String) -> Unit) {
    val context = LocalContext.current
    val query by viewModel.searchQuery.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val allStudents by viewModel.allStudents.collectAsState()

    // Temporary values for editing names
    var editingStudent by remember { mutableStateOf<StudentEntity?>(null) }
    var editNameText by remember { mutableStateOf("") }

    val displayStudents = remember(allStudents, query, sortOrder) {
        if (query.trim().length < 3) {
            emptyList()
        } else {
            val normalizedQuery = com.example.utils.ArabicNormalizer.normalize(query)
            val filtered = allStudents.filter {
                it.normalizedName.contains(normalizedQuery, ignoreCase = true) ||
                        it.registrationId.contains(query, ignoreCase = true)
            }
            if (sortOrder == "Newest") {
                filtered.sortedByDescending { it.id }
            } else {
                filtered.sortedBy { it.id }
            }
        }
    }

    if (editingStudent != null) {
        AlertDialog(
            onDismissRequest = { editingStudent = null },
            title = { Text("تعديل اسم الطالب", fontWeight = FontWeight.Bold) },
            confirmButton = {
                Button(onClick = {
                    viewModel.updateStudentName(editingStudent!!, editNameText, context) { ok ->
                        if (ok) {
                            Toast.makeText(context, "تم تعديل اسم الطالب!", Toast.LENGTH_SHORT).show()
                            editingStudent = null
                        }
                    }
                }) {
                    Text(stringResource(R.string.save_changes))
                }
            },
            dismissButton = {
                TextButton(onClick = { editingStudent = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            text = {
                Column {
                    Text("أدخل الاسم الجديد للطالب (سيتم تطبيق المعالجة الأبجدية تلقائياً):", fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = editNameText,
                        onValueChange = { editNameText = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = androidx.compose.material3.LocalTextStyle.current.copy(textAlign = TextAlign.Right)
                    )
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.End
    ) {
        Text(
            "فهرس البحث السريع",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.searchQuery.value = it },
            label = { Text("ابحث باسم الطالب أو رقم القيد...") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                        Icon(Icons.Filled.Clear, contentDescription = "مسح")
                    }
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            singleLine = true
        )

        // Show a helpful label if search is current inactive (<3 chars typed)
        if (query.isNotEmpty() && query.trim().length < 3) {
            Text(
                "⚠️ يتم تفعيل ميزة التصفية بالبحث بمجرد كتابة 3 أحرف على الأقل.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                textAlign = TextAlign.Right
            )
        }

        // Sorting Switcher Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("الترتيب الحالي: " + if (sortOrder == "Newest") "القيد الأحدث" else "القيد الأقدم", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Button(
                onClick = {
                    viewModel.sortOrder.value = if (sortOrder == "Newest") "Oldest" else "Newest"
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (sortOrder == "Newest") "القيد الأقدم" else "القيد الأحدث", fontSize = 13.sp)
            }
        }

        if (query.trim().length < 3) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "يرجى كتابة 3 أحرف على الأقل في حقل البحث لعرض وتصفية الأسماء.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 14.sp
                )
            }
        } else if (displayStudents.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "لم يتم العثور على سجلات طالب تطابق البحث.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(displayStudents, key = { it.id }) { student ->
                    CompactStudentCard(
                        student = student,
                        onEditClick = {
                            editingStudent = student
                            editNameText = student.name
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CompactStudentCard(
    student: StudentEntity,
    onEditClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Edit Button (Right aligned in RTL)
            IconButton(
                onClick = onEditClick,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("edit_button")
            ) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.edit),
                    tint = MaterialTheme.colorScheme.secondary
                )
            }

            // Name + Details (With weight to fill space)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    student.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Right
                )
                Row(horizontalArrangement = Arrangement.End) {
                    Text(
                        stringResource(R.string.registration_id, student.registrationId),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        " | ",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    Text(
                        stringResource(R.string.page_number, student.pageNumber),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Composable
fun StudentCard(
    student: StudentEntity,
    onImageClick: (String) -> Unit,
    showPageNumber: Boolean,
    onEditClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Quick Edit Action (Left hand side)
            IconButton(onClick = onEditClick) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "تعديل الاسم",
                    tint = MaterialTheme.colorScheme.secondary
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Text Details (Align Right for Arabic layout)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    student.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Right
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showPageNumber) {
                        Text("صفحة: ${student.pageNumber}", fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("|", fontSize = 13.sp, color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("قيد رقم: ${student.registrationId}", fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
                }
            }

            // Thumbnail Image on Left/End (Standard spacing)
            if (student.photoPath != null) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onImageClick(student.photoPath) }
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                ) {
                    LocalImageLoader(student.photoPath, modifier = Modifier.fillMaxSize())
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.AccountBox,
                        contentDescription = "بدون صورة",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 2. Add Student Screen (Ultra-Fast Entry)
// -------------------------------------------------------------
@Composable
fun AddStudentScreen(viewModel: RegistryViewModel, onImageClick: (String) -> Unit) {
    val context = LocalContext.current
    val name by viewModel.studentNameInput.collectAsState()
    val registrationId by viewModel.registrationIdInput.collectAsState()
    val page by viewModel.pageNumberInput.collectAsState()
    val isLocked by viewModel.isPageNumberLocked.collectAsState()
    val numberingMode by viewModel.numberingSystem.collectAsState()
    val isPhotoEnabled by viewModel.isPhotoCaptureEnabled.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadDraft(context)
    }

    LaunchedEffect(name, registrationId, page) {
        viewModel.saveDraft(context)
    }

    // Local indicators for constant rapid referencing
    val lastSavedOverall by viewModel.lastSavedStudentName.collectAsState()
    val lastInReg by viewModel.lastStudentInSelectedRegistry.collectAsState()

    // Photo flow capture setups
    val selectedPhotoPath by viewModel.registryPhotoPath.collectAsState()
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempPhotoUri != null) {
            val path = viewModel.saveCameraImage(context, tempPhotoUri!!)
            if (path != null) {
                viewModel.registryPhotoPath.value = path
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.End
    ) {
        Text(
            "إدخال السجلات الفائق السريع",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Upper reference bar: Last saved reference
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    "📌 مرجع الحفظ السريع الأخير",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    if (lastSavedOverall.isNotEmpty()) "الاسم الأخير المحفوظ: $lastSavedOverall" else "لم يتم حفظ سجلات في هذه الجلسة بعد",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    textAlign = TextAlign.Right
                )
            }
        }

        // Form Title
        Text("بيانات وثيقة القيد", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))

        // Student Name Field (Focus point)
        OutlinedTextField(
            value = name,
            onValueChange = { viewModel.studentNameInput.value = it },
            label = { Text("اسم الطالب الرباعي (مطلوب)") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            textStyle = androidx.compose.material3.LocalTextStyle.current.copy(textAlign = TextAlign.Right),
            singleLine = true
        )

        // Registry Number (Persisted automatically)
        OutlinedTextField(
            value = registrationId,
            onValueChange = { viewModel.onRegistrationIdChanged(it) },
            label = { Text("رقم السجل / القيد (محفوظ تلقائياً)") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            textStyle = androidx.compose.material3.LocalTextStyle.current.copy(textAlign = TextAlign.Right),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        // Dynamic lookup helper
        if (registrationId.isNotEmpty()) {
            Text(
                if (lastInReg != null) "آخر اسم محفوظ في هذا القيد: $lastInReg" else "لا توجد أسماء مسجلة حالياً في هذا القيد",
                fontSize = 12.sp,
                color = if (lastInReg != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                textAlign = TextAlign.Right
            )
        } else {
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Page Number Field (Locked dynamically if Auto/Semi-Auto increments active)
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = page,
                onValueChange = { viewModel.pageNumberInput.value = it },
                label = { Text("رقم الصفحة" + if (isLocked) " (مغفل - ترقيم تلقائي)" else "") },
                enabled = !isLocked,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                textStyle = androidx.compose.material3.LocalTextStyle.current.copy(textAlign = TextAlign.Right),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                trailingIcon = {
                    if (isLocked) {
                        Icon(Icons.Filled.Lock, contentDescription = "مغلق تلقائياً", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        }
        Text(
            "نظام الترقيم النشط: " + when (numberingMode) {
                "Automatic" -> "تلقائي (1-400 لكل سجل)"
                "Semi-Automatic" -> "نصف تلقائي (تسلسلي ممتد)"
                else -> "يدوي بالكامل"
            },
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            textAlign = TextAlign.Right
        )

        // Camera Block
        if (isPhotoEnabled) {
            Text("صورة وثيقة القيد (اختياري)", fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectedPhotoPath != null) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onImageClick(selectedPhotoPath!!) }
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                    ) {
                        LocalImageLoader(selectedPhotoPath!!, modifier = Modifier.fillMaxSize())
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Button(
                    onClick = {
                        val tempFile = File(context.cacheDir, "temp_camera.jpg")
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
                        tempPhotoUri = uri
                        cameraLauncher.launch(uri)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                ) {
                    Icon(Icons.Filled.AccountBox, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (selectedPhotoPath != null) "استبدل الصورة" else "التقاط من الكاميرا")
                }
            }
        }

        // Save Button (Enforces rules and does NOT clear key fields or navigate away)
        Button(
            onClick = {
                viewModel.saveStudentRecord(context) {
                    Toast.makeText(context, "تم حفظ سجل الطالب بنجاح!", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Filled.Check, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("حفظ السجل (الحالي)", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// -------------------------------------------------------------
// 3. Records Page (View, Edit & Media)
// -------------------------------------------------------------
@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Card(modifier = modifier.clickable { onClick() }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun RecordsScreen(viewModel: RegistryViewModel, onImageClick: (String) -> Unit, onNavigate: (Screen) -> Unit) {
    val context = LocalContext.current
    val allStudents by viewModel.allStudents.collectAsState()
    val isGuestMode by viewModel.isGuestMode.collectAsState()
    val filterMode by viewModel.listFilterMode.collectAsState() // "Registry" or "Alphabetical"
    val showPageCol by viewModel.showPageNumberColumn.collectAsState()
    val showDashboard by viewModel.showDashboard.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val totalRecords by viewModel.totalRecordsCount.collectAsState()
    val dupeRecords by viewModel.duplicateRecordsCount.collectAsState()
    val reviewRecords by viewModel.needsReviewCount.collectAsState()

    var selectedRegistryInput by remember { mutableStateOf("") }
    var selectedLetter by remember { mutableStateOf("") }
    

    // Temporary values for editing names
    var editingStudent by remember { mutableStateOf<StudentEntity?>(null) }
    var editNameText by remember { mutableStateOf("") }

    // Camera launcher specifically for updating photo inline
    var activePhotoStudent by remember { mutableStateOf<StudentEntity?>(null) }
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }
    val inlinePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempPhotoUri != null && activePhotoStudent != null) {
            val path = viewModel.saveCameraImage(context, tempPhotoUri!!)
            if (path != null) {
                viewModel.updateStudentPhoto(activePhotoStudent!!, path)
                Toast.makeText(context, "تم تحديث الصورة السجلية بنجاح!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (editingStudent != null) {
        AlertDialog(
            onDismissRequest = { editingStudent = null },
            title = { Text("تعديل اسم الطالب", fontWeight = FontWeight.Bold) },
            confirmButton = {
                Button(onClick = {
                    viewModel.updateStudentName(editingStudent!!, editNameText, context) { ok ->
                        if (ok) {
                            Toast.makeText(context, "تم تعديل اسم الطالب!", Toast.LENGTH_SHORT).show()
                            editingStudent = null
                        }
                    }
                }) {
                    Text(stringResource(R.string.save_changes))
                }
            },
            dismissButton = {
                TextButton(onClick = { editingStudent = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            text = {
                Column {
                    Text("أدخل الاسم الجديد للطالب (سيتم تطبيق المعالجة الأبجدية تلقائياً):", fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = editNameText,
                        onValueChange = { editNameText = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = androidx.compose.material3.LocalTextStyle.current.copy(textAlign = TextAlign.Right)
                    )
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.End
    ) {
        Text(
            "أرشيف سجلات القيود",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Dashboard (shown if query < 3 and dashboard is enabled)
        if (showDashboard && searchQuery.length < 3) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Text("لوحة المعلومات", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard(stringResource(R.string.total_records), "$totalRecords", Modifier.weight(1f))
                    StatCard(stringResource(R.string.duplicate_records), "$dupeRecords", Modifier.weight(1f), onClick = { onNavigate(Screen.Duplicates) })
                    StatCard(stringResource(R.string.review_records), "$reviewRecords", Modifier.weight(1f), onClick = { onNavigate(Screen.Review) })
                }
            }
        }

        // Filters toggler
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = { viewModel.listFilterMode.value = "Alphabetical" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (filterMode == "Alphabetical") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (filterMode == "Alphabetical") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("أبجدي (أ - ي)")
            }
            Button(
                onClick = { viewModel.listFilterMode.value = "Registry" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (filterMode == "Registry") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (filterMode == "Registry") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("رقم القيد والأقدمية")
            }
        }

        if (allStudents.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("الأرشيف فارغ بالكامل حالياً. ابدأ بإضافة بعض الطلاب والسجلات.", color = MaterialTheme.colorScheme.outline)
            }
        } else {
            // 1. Get unique registries
            val uniqueRegistries = remember(allStudents) {
                allStudents.map { it.registrationId }.distinct().sortedWith(compareBy({ it.toLongOrNull() ?: Long.MAX_VALUE }, { it }))
            }

            // 2. Arabic Alphabet
            val arabicAlphabet = remember {
                listOf("أ", "ب", "ت", "ث", "ج", "ح", "خ", "د", "ذ", "ر", "ز", "س", "ش", "ص", "ض", "ط", "ظ", "ع", "غ", "ف", "ق", "ك", "ل", "م", "ن", "هـ", "و", "ي")
            }

            // 3. Render Custom Filtering Selectors
            if (filterMode == "Registry") {
                OutlinedTextField(
                    value = selectedRegistryInput,
                    onValueChange = { selectedRegistryInput = it },
                    label = { Text("أدخل رقم القيد المطلوب عرضه") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    textStyle = androidx.compose.material3.LocalTextStyle.current.copy(textAlign = TextAlign.Right),
                    trailingIcon = {
                        if (selectedRegistryInput.isNotEmpty()) {
                            IconButton(onClick = { selectedRegistryInput = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "مسح")
                            }
                        }
                    }
                )

                if (uniqueRegistries.isNotEmpty()) {
                    Text(
                        "أرقام القيود المسجلة حالياً (انقر للاختيار السريع):",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        textAlign = TextAlign.Right
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        reverseLayout = true
                    ) {
                        items(uniqueRegistries) { regNum ->
                            val isSelected = selectedRegistryInput == regNum
                            val color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            Surface(
                                onClick = { selectedRegistryInput = regNum },
                                modifier = Modifier.clip(RoundedCornerShape(16.dp)),
                                color = color,
                                contentColor = contentColor,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = "قيد $regNum",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    "اختر الحرف الهجائي لعرض الأسماء التي تبدأ به:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    textAlign = TextAlign.Right
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    reverseLayout = true
                ) {
                    items(arabicAlphabet) { letter ->
                        val isSelected = selectedLetter == letter
                        val color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        Surface(
                            onClick = { selectedLetter = letter },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape),
                            color = color,
                            contentColor = contentColor,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text(
                                    text = letter,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // 4. Calculate display filtered students
            val processedStudents = remember(allStudents, filterMode, selectedRegistryInput, selectedLetter) {
                if (filterMode == "Alphabetical") {
                    if (selectedLetter.isBlank()) {
                        emptyList()
                    } else {
                        val normLetter = com.example.utils.ArabicNormalizer.normalize(selectedLetter)
                        allStudents.filter {
                            it.normalizedName.startsWith(normLetter) || it.name.trim().startsWith(selectedLetter, ignoreCase = true)
                        }.sortedBy { it.normalizedName }
                    }
                } else {
                    if (selectedRegistryInput.isBlank()) {
                        emptyList()
                    } else {
                        val targetReg = selectedRegistryInput.trim()
                        allStudents.filter {
                            it.registrationId == targetReg
                        }.sortedWith(compareBy({ it.registrationId.toLongOrNull() ?: Long.MAX_VALUE }, { it.pageNumber }))
                    }
                }
            }

            // 5. Render list or placeholder
            if (filterMode == "Registry" && selectedRegistryInput.isBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "⚠️ الرجاء اختيار أو كتابة رقم القيد أعلاه لعرض أسماء الطلاب المسجلين فيه.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            } else if (filterMode == "Alphabetical" && selectedLetter.isBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "⚠️ الرجاء اختيار أحد الحروف الهجائية أعلاه لعرض الأسماء التي تبدأ به.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            } else if (processedStudents.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (filterMode == "Registry") "لا توجد أي أسماء مسجلة تحت رقم القيد ($selectedRegistryInput)." else "لا توجد أسماء مسجلة تبدأ بالحرف ($selectedLetter).",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(processedStudents, key = { it.id }) { student ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Quick Action Tools (Left hand side)
                            if (!isGuestMode) {
                                Row {
                                    IconButton(onClick = { viewModel.deleteStudent(student) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "حذف سجل", tint = MaterialTheme.colorScheme.error)
                                    }
                                    IconButton(onClick = {
                                        activePhotoStudent = student
                                        val tempFile = File(context.cacheDir, "temp_inline.jpg")
                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
                                        tempPhotoUri = uri
                                        inlinePhotoLauncher.launch(uri)
                                    }) {
                                        Icon(Icons.Filled.AccountBox, contentDescription = "تحديث الصورة", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = {
                                        editingStudent = student
                                        editNameText = student.name
                                    }) {
                                        Icon(Icons.Filled.Edit, contentDescription = "تعديل الاسم", tint = MaterialTheme.colorScheme.secondary)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            // Details
                            Column(horizontalAlignment = Alignment.End) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(student.getPublicRegistrationDetails(isGuestMode), fontSize = 12.sp)
                                }
                                Text(student.getPublicName(isGuestMode), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
}

// -------------------------------------------------------------
// 4. Duplicate Discovery Screen
// -------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicatesScreen(viewModel: RegistryViewModel, onImageClick: (String) -> Unit) {
    val fb by viewModel.filterBinary.collectAsState()
    val ft by viewModel.filterTrinary.collectAsState()
    val fq by viewModel.filterQuad.collectAsState()
    val fe by viewModel.filterExact.collectAsState()

    val duplicatesList by viewModel.duplicateResults.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.End
    ) {
        Text(
            "صفحة كشف تكرار الأسماء ديموغرافياً",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Filters selectors Dropdown
        var expanded by remember { mutableStateOf(false) }
        val options = listOf(
            "تكرار ثنائي (الاسم الأول والثاني)",
            "تكرار ثلاثي",
            "تكرار رباعي",
            "تكرار تام (مطابق تماماً)"
        )
        // Derive select state from current filters
        var selectedOption by remember {
            mutableStateOf(
                if (fe) options[3]
                else if (fq) options[2]
                else if (ft) options[1]
                else if (fb) options[0]
                else ""
            )
        }

        fun updateFilters(option: String) {
            selectedOption = option
            viewModel.filterBinary.value = (option == options[0])
            viewModel.filterTrinary.value = (option == options[1])
            viewModel.filterQuad.value = (option == options[2])
            viewModel.filterExact.value = (option == options[3])
        }

        if (selectedOption.isNotEmpty()) {
            val totalGroups = duplicatesList.size
            val totalStudentsInDuplicates = duplicatesList.sumOf { 1 + it.second.size }
            Text(
                text = "عدد مجموعات التكرار: $totalGroups, إجمالي الطلاب المتضمنين: $totalStudentsInDuplicates",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = selectedOption,
                onValueChange = {},
                readOnly = true,
                label = { Text("اختر معيار البحث") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { selectionOption ->
                    DropdownMenuItem(
                        text = { Text(selectionOption) },
                        onClick = {
                            updateFilters(selectionOption)
                            expanded = false
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (duplicatesList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("لم يتم اكتشاف عينات مكررة بناءً على الفلاتر المحددة حالياً.", color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(duplicatesList) { (student, duplicates) ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.End) {
                            Text("السجل الأصلي المكتشف:", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.height(4.dp))
                            StudentBasicRow(student)

                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f))

                            Text("السجلات المتطابقة مكررة:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.height(4.dp))
                            duplicates.forEach { dup ->
                                StudentBasicRow(dup)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { viewModel.markAsRealDuplicate(dup.id) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                                        Text("🔴 مكرر حقيقي")
                                    }
                                    Button(onClick = { viewModel.markAsFalsePositive(student.id, dup.id) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                                        Text("🟢 غير مكرر")
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagerRegistrationScreen(
    onRegister: (String, String, String, String) -> Unit,
    initialSchoolName: String = "",
    initialProvince: String = "",
    initialManagerName: String = ""
) {
    var schoolName by remember { mutableStateOf(initialSchoolName) }
    var province by remember { mutableStateOf(initialProvince) }
    var managerName by remember { mutableStateOf(initialManagerName) }
    var phoneNumber by remember { mutableStateOf("") }
    var showErrors by remember { mutableStateOf(false) }
    var isProvinceExpanded by remember { mutableStateOf(false) }
    
    val provinces = listOf(
        "بغداد", "البصرة", "نينوى", "أربيل", "كركوك", "السليمانية", 
        "الأنبار", "بابل", "كربلاء", "النجف", "واسط", "ذي قار", 
        "ميسان", "المثنى", "القادسية", "ديالى", "صلاح الدين", "دهوك"
    )
    
    // Validation
    val isNameValid = managerName.trim().split(Regex("\\s+")).size >= 2
    val isPhoneValid = phoneNumber.length == 10 && phoneNumber.all { it.isDigit() }
    val isFormValid = isNameValid && isPhoneValid && schoolName.isNotEmpty() && province.isNotEmpty()
    
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("تسجيل المدير", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        
        OutlinedTextField(
            value = schoolName,
            onValueChange = { schoolName = it },
            label = { Text("اسم المدرسة") },
            modifier = Modifier.fillMaxWidth()
        )
        
        ExposedDropdownMenuBox(
            expanded = isProvinceExpanded,
            onExpandedChange = { isProvinceExpanded = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = province,
                onValueChange = {},
                readOnly = true,
                label = { Text("اسم المحافظة") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isProvinceExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(expanded = isProvinceExpanded, onDismissRequest = { isProvinceExpanded = false }) {
                provinces.forEach { p ->
                    DropdownMenuItem(text = { Text(p) }, onClick = { province = p; isProvinceExpanded = false })
                }
            }
        }
        
        OutlinedTextField(
            value = managerName,
            onValueChange = { managerName = it },
            label = { Text("اسم المدير الثنائي") },
            isError = showErrors && !isNameValid,
            modifier = Modifier.fillMaxWidth()
        )
        if (showErrors && !isNameValid) {
            Text("يجب إدخال الاسم الأول والثاني على الأقل", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
        
        OutlinedTextField(
            value = phoneNumber,
            onValueChange = {
                if (it.all { digit -> digit.isDigit() } && it.length <= 10) {
                    phoneNumber = it
                }
            },
            label = { Text("رقم الهاتف") },
            prefix = { Text("00964 ") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = showErrors && !isPhoneValid,
            modifier = Modifier.fillMaxWidth()
        )
        
        Button(
            onClick = {
                if (isFormValid) {
                    onRegister(schoolName, province, managerName, "00964$phoneNumber")
                } else {
                    showErrors = true
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("تسجيل")
        }
    }
}

@Composable
fun OtpVerificationScreen(
    phoneNumber: String,
    viewModel: RegistryViewModel,
    onVerified: () -> Unit,
    onEditNumber: () -> Unit
) {
    val otpDigits = remember { List(6) { mutableStateOf("") } }
    val focusRequesters = remember { List(6) { FocusRequester() } }
    val timer by viewModel.resendTimer.collectAsState()

    LaunchedEffect(Unit) { viewModel.startTimer() }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("أدخل رمز التحقق المرسل لـ $phoneNumber", textAlign = TextAlign.Center)
        
        Row(Modifier.padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            otpDigits.forEachIndexed { index, digit ->
                OutlinedTextField(
                    value = digit.value,
                    onValueChange = {
                        if (it.length <= 1 && it.all { c -> c.isDigit() }) {
                            digit.value = it
                            if (it.isNotEmpty() && index < 5) focusRequesters[index + 1].requestFocus()
                        }
                    },
                    modifier = Modifier.size(48.dp).focusRequester(focusRequesters[index]),
                    textStyle = TextStyle(textAlign = TextAlign.Center),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }
        }
        
        Text("الوقت المتبقي: ${String.format("%02d:%02d", timer / 60, timer % 60)}")
        
        Button(
            onClick = {
                val code = otpDigits.joinToString("") { it.value }
                viewModel.verifyOtp(phoneNumber, code) { success -> if (success) onVerified() }
            },
            enabled = otpDigits.all { it.value.isNotEmpty() }
        ) {
            Text("تحقق")
        }
        
        TextButton(
            onClick = { viewModel.startTimer(); viewModel.resendOtp(phoneNumber) },
            enabled = timer == 0
        ) {
            Text(if (timer == 0) "إعادة إرسال رمز التحقق" else "إعادة إرسال بعد ${timer} ثانية")
        }
        
        TextButton(onClick = onEditNumber) {
            Text("تعديل رقم الهاتف")
        }
    }
}

@Composable
fun LoginScreen(viewModel: RegistryViewModel) {
    val otp by viewModel.otpCode.collectAsState()
    val error by viewModel.loginError.collectAsState()
    val attempts by viewModel.remainingAttempts.collectAsState()

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("تسجيل دخول المدير", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = otp,
            onValueChange = { viewModel.otpCode.value = it },
            label = { Text("رمز OTP") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        
        if (error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(error!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = { viewModel.verifyAdminLogin(otp) },
            enabled = attempts > 0,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("دخول")
        }
        
        TextButton(onClick = { 
            viewModel.setModeSelected(true)
            viewModel.isLoginScreenVisible.value = false 
        }) {
            Text("الدخول كضيف")
        }
    }
}

@Composable
fun StudentBasicRow(student: StudentEntity) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
            Text(student.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text("قيد رقم: ${student.registrationId} - صفحة: ${student.pageNumber}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
fun StudentInlineRow(student: StudentEntity, onImageClick: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
            Text(student.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text("قيد رقم: ${student.registrationId} - صفحة: ${student.pageNumber}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(4.dp))
                .clickable(enabled = student.photoPath != null) { onImageClick(student.photoPath!!) }
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
        ) {
            if (student.photoPath != null) {
                LocalImageLoader(student.photoPath, modifier = Modifier.fillMaxSize())
            } else {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
            }
        }
    }
}

// -------------------------------------------------------------
// 5. CSV Data Screen (Import / Export)
// -------------------------------------------------------------
@Composable
fun CSVScreen(viewModel: RegistryViewModel) {
    val context = LocalContext.current

    // JSON Import Launcher
    val importJSONLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importFromJSON(context, uri) { imported, skipped, error ->
                if (error != null) {
                    Toast.makeText(context, "فشل استيراد: $error", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "تم الاستيراد بنجاح! تم حفظ $imported سجلات JSON، وتم تخطي $skipped.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Set up file launchers
    val importCSVLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importFromCSV(context, uri) { imported, skipped, error ->
                if (error != null) {
                    Toast.makeText(context, "فشل استيراد: $error", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(
                        context,
                        "تم الاستيراد بنجاح! تم حفظ $imported سجلات، وتم تخطي $skipped مكررة.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.End
    ) {
        Text(
            "معالجة واستيراد البيانات",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    "🔒 موثوقية استيراد البيانات:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "عند استيراد أي ملف CSV للمكتب، سيتم بالكامل تصفية أي اسم طالب مكرر وتشغيل نظام المعالجة والتحليل الأبجدي للنصوص العربية تلقائياً لمنع أي تكرار وتحديد السجلات بدقة.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Right
                )
            }
        }


        OutlinedButton(
            onClick = {
                val csvFile = viewModel.exportToCSV(context)
                if (csvFile != null && csvFile.exists()) {
                    val shareUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", csvFile)
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/csv"
                        putExtra(Intent.EXTRA_STREAM, shareUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "مشاركة ملف قاعدة البيانات الاحتياطي"))
                } else {
                    Toast.makeText(context, "فشل تصدير البيانات.", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(bottom = 12.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Filled.Share, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("مشاركة أو نسخ ملف النسخ الاحتياطي (CSV)")
        }

        OutlinedButton(
            onClick = {
                val jsonFile = viewModel.exportToJSON(context)
                if (jsonFile != null && jsonFile.exists()) {
                    val shareUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", jsonFile)
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_STREAM, shareUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "مشاركة ملف النسخة الاحتياطية (JSON)"))
                } else {
                    Toast.makeText(context, "فشل تصدير البيانات.", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(bottom = 12.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Filled.Share, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("مشاركة ملف النسخة الاحتياطية (JSON)")
        }

        Button(
            onClick = { importJSONLauncher.launch(arrayOf("application/json")) },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(bottom = 12.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("استيراد نسخة احتياطية (JSON)")
        }

        Button(
            onClick = {
                importCSVLauncher.launch(arrayOf("text/comma-separated-values", "text/csv", "application/csv"))
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("استيراد سجلات من ملف خارجي (CSV)")
        }
    }
}

// -------------------------------------------------------------
// 6. Settings Screen
// -------------------------------------------------------------
@Composable
fun SettingsScreen(viewModel: RegistryViewModel) {
    val context = LocalContext.current
    val isNight by viewModel.isNightMode.collectAsState()
    val showRecords by viewModel.showRecordsScreen.collectAsState()
    val showDuplicates by viewModel.showDuplicatesScreen.collectAsState()
    val showPageCol by viewModel.showPageNumberColumn.collectAsState()
    val showDashboard by viewModel.showDashboard.collectAsState()
    val isGuestMode by viewModel.isGuestMode.collectAsState()
    val activeNumberingSystem by viewModel.numberingSystem.collectAsState()

    // Confirmation Wipe DB fields
    var openWipeDialog by remember { mutableStateOf(false) }
    var wipeTextConfirm by remember { mutableStateOf("") }

    if (openWipeDialog) {
        AlertDialog(
            onDismissRequest = { openWipeDialog = false },
            title = { Text("⚠️ هل أنت متأكد من مسح البيانات بالكامل؟", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearDatabase(wipeTextConfirm) { ok ->
                            if (ok) {
                                Toast.makeText(context, "تم مسح وتصفير قاعدة البيانات بالكامل!", Toast.LENGTH_SHORT).show()
                                openWipeDialog = false
                            } else {
                                Toast.makeText(context, "خطأ! يرجى كتابة الكلمة الصحيحة تأكيداً للحذف.", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("نعم، احذف الكل")
                }
            },
            dismissButton = {
                TextButton(onClick = { openWipeDialog = false }) {
                    Text("إلغاء")
                }
            },
            text = {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "هذا الإجراء سيقوم بإزالة كافة أسماء الطلاب والوثائق نهائياً من قاعدة البيانات المحلية ولا يمكن التراجع عنها.",
                        textAlign = TextAlign.Right,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "لتأكيد الحذف النهائي، اكتب الكلمة العربية 'احذف' في الحقل أدناه:",
                        textAlign = TextAlign.Right,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = wipeTextConfirm,
                        onValueChange = { wipeTextConfirm = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = androidx.compose.material3.LocalTextStyle.current.copy(textAlign = TextAlign.Right)
                    )
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.End
    ) {
        Text(
            "خيارات وتفضيلات النظام",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Day/Night switch
        Text("الواجهات", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(checked = isNight, onCheckedChange = { viewModel.isNightMode.value = it })
            Text(if (isNight) "النمط الليلي مفعّل (قراءة واضحة)" else "النمط النهاري مفعّل", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
        }


        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(checked = showDashboard, onCheckedChange = { viewModel.toggleDashboard() })
            Text("إظهار لوحة المعلومات (Dashboard)", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(checked = showRecords, onCheckedChange = { viewModel.showRecordsScreen.value = it })
            Text("إظهار صفحة السجلات", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(checked = showDuplicates, onCheckedChange = { viewModel.showDuplicatesScreen.value = it })
            Text("إظهار صفحة التكرار", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        // Show/Hide Page number column
        Text("تخصيص العرض في القوائم والأرشيف", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(checked = showPageCol, onCheckedChange = { viewModel.showPageNumberColumn.value = it })
            Text("إظهار رقم الصفحة في القوائم", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(checked = viewModel.isPhotoCaptureEnabled.collectAsState().value, onCheckedChange = { viewModel.isPhotoCaptureEnabled.value = it })
            Text("تفعيل التقاط صور الوثائق", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        // The 3 Numbering systems
        Text("نظام تسلسل ترقيم الصفحة الذكي", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(modifier = Modifier.height(8.dp))

        listOf(
            "Manual" to "يدوي بالكامل: تقوم بتسجيل رقم القيد ورقم الصفحة بحرية تامة.",
            "Automatic" to "تلقائي ذكي: تبدأ الصفحة من 1 وتصعد لـ 400 بشكل منفصل لكل سجل.",
            "Semi-Automatic" to "نصف تلقائي ممتد: يتم متابعة تسلسل الصفحات عبر السجلات ككل، مع فك قفل كتابة البداية للمرة الأولى في أي سجل جديد."
        ).forEach { (systemCode, systemDesc) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.numberingSystem.value = systemCode }
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = when (systemCode) {
                            "Manual" -> "يدوي (منفرد)"
                            "Automatic" -> "تلقائي بالكامل (محدود 400)"
                            else -> "نصف تلقائي (تسلسلي ممتد)"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (activeNumberingSystem == systemCode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    Text(systemDesc, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline, textAlign = TextAlign.Right)
                }
                RadioButton(
                    selected = activeNumberingSystem == systemCode,
                    onClick = { viewModel.numberingSystem.value = systemCode }
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        // Wipe database button
        Text("صيانة النظام ومسح البيانات", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                wipeTextConfirm = ""
                openWipeDialog = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.Filled.Delete, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("حملة التنظيف: تصفير وتفريغ الأرشيف بالكامل", fontSize = 14.sp)
        }
    }
}
