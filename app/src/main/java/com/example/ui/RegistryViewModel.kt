package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.data.AppDatabase
import com.example.data.AppConfigEntity
import com.example.data.StudentEntity
import com.example.data.StudentRepository
import com.example.network.NetworkClient
import com.example.network.OtpRequest
import com.example.utils.ArabicNormalizer
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.gotrue.*
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.realtime
import com.squareup.moshi.Moshi
import kotlinx.serialization.Serializable
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.example.data.ConnectionData
import kotlinx.serialization.json.Json
import java.io.FileOutputStream
import okhttp3.OkHttpClient
import okhttp3.Request

class RegistryViewModel(application: Application) : AndroidViewModel(application) {

    class Factory(private val application: Application) : androidx.lifecycle.ViewModelProvider.Factory {
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(RegistryViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return RegistryViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    private val repository: StudentRepository
    private val supabase = createSupabaseClient(
        supabaseUrl = com.example.BuildConfig.SUPABASE_URL,
        supabaseKey = com.example.BuildConfig.SUPABASE_ANON_KEY
    ) {
        install(Postgrest)
        install(Auth)
        install(Realtime)
    }
    
    val staffRequests = MutableStateFlow<List<StaffRequest>>(emptyList())
    
    init {
        val db = AppDatabase.getDatabase(application)
        val studentDao = db.studentDao()
        val appConfigDao = db.appConfigDao()
        repository = StudentRepository(studentDao)
        
        // Listen to Realtime updates (placeholders)
        viewModelScope.launch {
            // supabase.realtime.channel("staff_requests")
        }
    }
    
    // Helper to get DAOs
    private fun getStudentDao() = AppDatabase.getDatabase(getApplication()).studentDao()
    private fun getAppConfigDao() = AppDatabase.getDatabase(getApplication()).appConfigDao()
    
    // ... rest of the code ...

    // List of all students loaded as a reactive flow
    val allStudents = repository.allStudentsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Form fields input states
    val studentNameInput = MutableStateFlow("")
    val registrationIdInput = MutableStateFlow("")
    val pageNumberInput = MutableStateFlow("")
    val registryPhotoPath = MutableStateFlow<String?>(null)

    // Last student saved overall reference
    val lastSavedStudentName = MutableStateFlow("")

    // Last student saved under the currently selected/typed registry number
    val lastStudentInSelectedRegistry = MutableStateFlow<String?>(null)

    // Settings
    val numberingSystem = MutableStateFlow("Manual") // "Manual" (يدوي), "Automatic" (تلقائي), "Semi-Automatic" (نصف تلقائي)
    val showPageNumberColumn = MutableStateFlow(true)
    val isNightMode = MutableStateFlow(false)
    val showRecordsScreen = MutableStateFlow(true)
    val showDuplicatesScreen = MutableStateFlow(true)
    val isPhotoCaptureEnabled = MutableStateFlow(true)
    
    // User Mode (Persisted)
    val sharedPrefs = application.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    
    val showDashboard = MutableStateFlow(sharedPrefs.getBoolean("show_dashboard", true))
    val isGuestMode = MutableStateFlow(sharedPrefs.getBoolean("is_guest_mode", false))
    val isModeSelected = MutableStateFlow(sharedPrefs.getBoolean("is_mode_selected", false))

    fun toggleDashboard() {
        val newVal = !showDashboard.value
        showDashboard.value = newVal
        sharedPrefs.edit().putBoolean("show_dashboard", newVal).apply()
    }

    fun toggleGuestMode() {
        val newVal = !isGuestMode.value
        isGuestMode.value = newVal
        sharedPrefs.edit().putBoolean("is_guest_mode", newVal).putBoolean("is_mode_selected", true).apply()
    }

    fun setModeSelected(isGuest: Boolean) {
        isGuestMode.value = isGuest
        isModeSelected.value = true
        sharedPrefs.edit().putBoolean("is_guest_mode", isGuest).putBoolean("is_mode_selected", true).apply()
    }

    fun resetUserMode() {
        isModeSelected.value = false
        sharedPrefs.edit().putBoolean("is_mode_selected", false).apply()
    }

    fun switchToGuestMode() {
        setModeSelected(true)
        loginError.value = null
        remainingAttempts.value = 3
    }
    
    // OTP State
    val otpCode = MutableStateFlow("")
    val isOtpValid = MutableStateFlow(false)
    val resendTimer = MutableStateFlow(60)
    var timerJob: kotlinx.coroutines.Job? = null
    val isLoginScreenVisible = MutableStateFlow(false)

    private val _pendingConflicts = MutableStateFlow<List<com.example.data.Conflict>>(emptyList())
    val pendingConflicts = _pendingConflicts.asStateFlow()

    fun resolveConflict(conflict: com.example.data.Conflict, action: com.example.data.ResolutionAction) {
        viewModelScope.launch(Dispatchers.IO) {
            when (action) {
                com.example.data.ResolutionAction.OVERWRITE_LOCAL -> {
                    repository.updateStudent(conflict.incomingStudent.copy(id = conflict.existingStudent.id))
                }
                com.example.data.ResolutionAction.KEEP_LOCAL -> {
                    // Do nothing, keep existing
                }
                com.example.data.ResolutionAction.CREATE_DUPLICATE -> {
                    repository.insertStudent(conflict.incomingStudent)
                }
            }
            _pendingConflicts.value = _pendingConflicts.value.filter { it != conflict }
        }
    }

    fun startTimer() {
        timerJob?.cancel()
        resendTimer.value = 60
        timerJob = viewModelScope.launch {
            while (resendTimer.value > 0) {
                kotlinx.coroutines.delay(1000)
                resendTimer.value--
            }
        }
    }
    
    fun formatPhoneNumber(phone: String): String {
        var clean = phone.replace(Regex("\\s+"), "")
        if (clean.startsWith("00")) {
            clean = "+" + clean.substring(2)
        } else if (clean.startsWith("0")) {
            clean = "+964" + clean.substring(1)
        } else if (!clean.startsWith("+")) {
            clean = "+$clean"
        }
        return clean
    }

    fun resendOtp(phoneNumber: String) {
        val raw = phoneNumber.replace(" ", "")
        val formattedPhone = "+964" + raw.removePrefix("00964").removePrefix("0")
        android.util.Log.d("TWILIO_DEBUG", "Original: $phoneNumber -> Formatted: $formattedPhone")
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val request = OtpRequest(phone = formattedPhone)
                val response = NetworkClient.superTaskService.sendPhoneNumber(request)
                
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        startTimer()
                    } else {
                        val errorBody = response.errorBody()?.string()
                        android.util.Log.e("TWILIO_DEBUG", "Server error (code ${response.code()}): $errorBody")
                        loginError.value = "رفض السيرفر (كود ${response.code()}): $errorBody"
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("TWILIO_DEBUG", "Network exception", e)
                withContext(Dispatchers.Main) {
                    loginError.value = "خطأ شبكة صريح: ${e.javaClass.simpleName} - ${e.message}"
                }
            }
        }
    }

    fun verifyOtp(phoneNumber: String, otp: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            // Simulation: Only allow "123456" in development
            val isValid = otp == "123456"
            withContext(Dispatchers.Main) { onResult(isValid) }
        }
    }

    fun registerManager(schoolName: String, province: String, managerName: String, phone: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // TODO: Actual Supabase API call here to register manager
                // val response = supabaseApi.registerManager(schoolName, province, managerName, phone)
                // if (response.isSuccessful) withContext(Dispatchers.Main) { onResult(true) }
                // else withContext(Dispatchers.Main) { onResult(false) }
                
                withContext(Dispatchers.Main) { onResult(true) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(false) }
            }
        }
    }

    fun sendPhoneNumberToEdgeFunction(phone: String, onResult: (Boolean) -> Unit) {
        val raw = phone.replace(" ", "")
        val formattedPhone = "+964" + raw.removePrefix("00964").removePrefix("0")
        android.util.Log.d("TWILIO_DEBUG", "Original: $phone -> Formatted: $formattedPhone")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val request = OtpRequest(phone = formattedPhone)
                val response = NetworkClient.superTaskService.sendPhoneNumber(request)
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        onResult(true)
                    } else {
                        val errorBody = response.errorBody()?.string()
                        android.util.Log.e("TWILIO_DEBUG", "Edge function error: ${response.code()} - $errorBody")
                        onResult(false)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("TWILIO_DEBUG", "Edge function exception", e)
                withContext(Dispatchers.Main) {
                    onResult(false)
                }
            }
        }
    }

    fun upgradeGuestToManager(verifiedSchoolId: String, managerName: String, schoolName: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val appConfigDao = getAppConfigDao()
                val studentDao = getStudentDao()
                
                // 1. Update AppConfig
                val config = AppConfigEntity(
                    id = 1,
                    currentRole = "ADMIN",
                    schoolId = verifiedSchoolId,
                    schoolName = schoolName,
                    managerName = managerName
                )
                appConfigDao.insertConfig(config)
                
                // 2. Bulk update students
                studentDao.bulkUpdateSchoolId(verifiedSchoolId)
                
                // 3. Fetch stamped records
                val stampedStudents = studentDao.getAllBySchoolId(verifiedSchoolId)
                
                // 4. Construct payload and trigger sync
                val studentDtos = stampedStudents.map {
                    com.example.network.StudentDto(
                        studentName = it.name,
                        registrationNumber = it.registrationId,
                        pageNumber = it.pageNumber.toString(),
                        schoolId = it.schoolId!!
                    )
                }
                
                val response = NetworkClient.superTaskService.syncStudents(studentDtos)
                
                withContext(Dispatchers.Main) {
                    onComplete(response.isSuccessful)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onComplete(false)
                }
            }
        }
    }

    // Login Error and Attempts
    val loginError = MutableStateFlow<String?>(null)
    val remainingAttempts = MutableStateFlow(3)

    fun verifyAdminLogin(otp: String) {
        if (otp == "123456") {
            // Success
            loginError.value = null
            setModeSelected(false) // Admin mode
            isLoginScreenVisible.value = false
            remainingAttempts.value = 3
        } else {
            // Failure
            remainingAttempts.value--
            if (remainingAttempts.value > 0) {
                loginError.value = "رمز خاطئ. المحاولات المتبقية: ${remainingAttempts.value}"
            } else {
                loginError.value = "تم استنفاد المحاولات. يرجى إعادة المحاولة لاحقاً."
            }
        }
    }

    fun markAsRealDuplicate(studentId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateStudentStatus(studentId, "Duplicate_Pending")
        }
    }

    fun markAsFalsePositive(id1: Int, id2: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertWhitelistedPair(com.example.data.WhitelistedPair(studentId1 = id1, studentId2 = id2))
        }
    }

    val searchQuery = MutableStateFlow("")
    val sortOrder = MutableStateFlow("Newest") // "Newest" (القيد الأحدث), "Oldest" (القيد الأقدم)
    val listFilterMode = MutableStateFlow("Registry") // "Registry" (حسب رقم القيد) or "Alphabetical" (أبجدي أ-ي)

    // Duplication Discovery checkboxes
    val filterBinary = MutableStateFlow(false)
    val filterTrinary = MutableStateFlow(false)
    val filterQuad = MutableStateFlow(false)
    val filterExact = MutableStateFlow(false)

    // Validation Status Dialog state
    data class ValidationError(val title: String, val message: String)
    val validationError = MutableStateFlow<ValidationError?>(null)

    // Tracks if Page Number text field is specifically locked
    val isPageNumberLocked = combine(
        numberingSystem,
        registrationIdInput,
        allStudents
    ) { mode, registry, students ->
        when (mode) {
            "Manual" -> false
            "Automatic" -> true
            "Semi-Automatic" -> {
                if (registry.isBlank()) {
                    false
                } else {
                    // Check if this registry already exists in the database
                    val registryExists = students.any { it.registrationId == registry }
                    // Locked if it exists; Unlocked ONLY ONCE if it is a NEW registry number
                    registryExists
                }
            }
            else -> false
        }
    }.stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = false)

    // Automatically compute the next page number when inputs change
    init {
        viewModelScope.launch(Dispatchers.Default) {
            combine(
                numberingSystem,
                registrationIdInput,
                allStudents
            ) { mode, registry, students ->
                Triple(mode, registry, students)
            }.collect { (mode, registry, students) ->
                if (registry.isBlank()) return@collect
                
                // Fetch last student in this specific registry
                val studentsInRegistry = students.filter { it.registrationId == registry }
                val lastStudentInReg = studentsInRegistry.maxByOrNull { it.id }
                withContext(Dispatchers.Main) {
                    lastStudentInSelectedRegistry.value = lastStudentInReg?.name
                }

                when (mode) {
                    "Automatic" -> {
                        if (lastStudentInReg != null) {
                            val nextPg = lastStudentInReg.pageNumber + 1
                            val finalPg = if (nextPg > 400) 1 else nextPg // clamp/wrap as per 1-400 rule
                            withContext(Dispatchers.Main) { pageNumberInput.value = finalPg.toString() }
                        } else {
                            withContext(Dispatchers.Main) { pageNumberInput.value = "1" }
                        }
                    }
                    "Semi-Automatic" -> {
                        val registryExists = studentsInRegistry.isNotEmpty()
                        if (registryExists) {
                            // If registry exists, sequential counting continues
                            val latestInReg = studentsInRegistry.maxByOrNull { it.pageNumber }
                            val overallMaxPg = students.maxOfOrNull { it.pageNumber } ?: 1
                            val basePage = latestInReg?.pageNumber ?: overallMaxPg
                            withContext(Dispatchers.Main) { pageNumberInput.value = (basePage + 1).toString() }
                        } else {
                            // NEW REGISTRY: user can type freely, so we don't overwrite if they are typing.
                            // However, we suggest a sequential continuation from last overall student as default.
                            if (pageNumberInput.value.isBlank()) {
                                val overallMaxPg = students.maxOfOrNull { it.pageNumber } ?: 5000
                                withContext(Dispatchers.Main) { pageNumberInput.value = (overallMaxPg + 1).toString() }
                            }
                        }
                    }
                    "Manual" -> {
                        // Do not interfere with manual entry
                    }
                }
            }
        }
    }

    // Handles changes to registry number input
    fun onRegistrationIdChanged(number: String) {
        registrationIdInput.value = number
    }

    // Saves the active entry to Room database after passing strict validation guards
    fun saveStudentRecord(context: Context, onSuccess: () -> Unit) {
        val name = studentNameInput.value.trim()
        val registryStr = registrationIdInput.value.trim()
        val pageStr = pageNumberInput.value.trim()
        val photoPath = registryPhotoPath.value

        if (name.isBlank()) {
            validationError.value = ValidationError("حقل فارغ", "يرجى إدخال اسم الطالب أولاً.")
            return
        }
        if (registryStr.isBlank()) {
            validationError.value = ValidationError("حقل فارغ", "يرجى إدخال رقم القيد.")
            return
        }
        val pageNum = pageStr.toIntOrNull()
        if (pageNum == null || pageNum <= 0) {
            validationError.value = ValidationError("رقم صفحة غير صالح", "يرجى إدخال رقم صفحة صحيح أكبر من الصفر.")
            return
        }

        // Apply strict limit for automatic numbering
        if (numberingSystem.value == "Automatic" && pageNum > 400) {
            validationError.value = ValidationError("تجاوز الحد", "يتيح الترقيم التلقائي صفحات من 1 إلى 400 فقط لكل سجل.")
            return
        }

        val normalized = ArabicNormalizer.normalize(name)

        viewModelScope.launch {
            // 1. Double check duplicate NAMES (Strict validation)
            val existingNameDup = repository.getStudentByNormalizedName(normalized)
            if (existingNameDup != null) {
                validationError.value = ValidationError(
                    "اسم مكرر",
                    "هذا الطالب مسجل مسبقاً في القيد: ${existingNameDup.registrationId}، الصفحة: ${existingNameDup.pageNumber}"
                )
                return@launch
            }

            // 2. Double check duplicate PAGE within the same registry
            val existingPageDup = repository.getStudentByRegistryAndPage(registryStr, pageStr)
            if (existingPageDup != null) {
                validationError.value = ValidationError(
                    "صفحة مكررة",
                    "الصفحة $pageNum مستخدمة مسبقاً في القيد $registryStr للطالب: ${existingPageDup.name}"
                )
                return@launch
            }

            // Save student record
            val student = StudentEntity(
                name = name,
                normalizedName = normalized,
                registrationId = registryStr,
                pageNumber = pageNum,
                photoPath = photoPath
            )

            repository.insertStudent(student)
            clearDraft(context)

            // Execute post-save styling and UX flows:
            // - User remains on the exact same page
            // - Keep registry number persisted
            // - Update Reference Indicators
            lastSavedStudentName.value = name
            studentNameInput.value = "" // Ready for next student
            registryPhotoPath.value = null // Clean up photo for next

            // Re-trigger update of last student name
            val lastStudentInReg = repository.getLastStudentInRegistry(registryStr)
            lastStudentInSelectedRegistry.value = lastStudentInReg?.name

            // Force increment calculations for next continuous entry
            if (numberingSystem.value == "Automatic") {
                val nextPg = pageNum + 1
                pageNumberInput.value = (if (nextPg > 400) 1 else nextPg).toString()
            } else if (numberingSystem.value == "Semi-Automatic") {
                pageNumberInput.value = (pageNum + 1).toString()
            }

            onSuccess()
        }
    }

    // Handles single item deletions
    fun deleteStudent(student: StudentEntity) {
        viewModelScope.launch {
            repository.deleteStudent(student)
        }
    }

    // Handles student detail updates
    fun updateStudentName(student: StudentEntity, newName: String, context: Context, onComplete: (Boolean) -> Unit) {
        if (newName.isBlank()) {
            onComplete(false)
            return
        }
        val normalized = ArabicNormalizer.normalize(newName)
        viewModelScope.launch {
            val existing = repository.getStudentByNormalizedName(normalized)
            if (existing != null && existing.id != student.id) {
                validationError.value = ValidationError(
                    "اسم مكرر",
                    "هذا الطالب مسجل مسبقاً في القيد: ${existing.registrationId}، الصفحة: ${existing.pageNumber}"
                )
                onComplete(false)
                return@launch
            }

            repository.updateStudent(student.copy(name = newName, normalizedName = normalized))
            onComplete(true)
        }
    }

    // Capture or update photo
    fun updateStudentPhoto(student: StudentEntity, newPath: String?) {
        viewModelScope.launch {
            repository.updateStudent(student.copy(photoPath = newPath))
        }
    }

    // DB Utilities - Wipe DB
    fun clearDatabase(confirmText: String, onComplete: (Boolean) -> Unit) {
        if (confirmText == "احذف") {
            viewModelScope.launch {
                repository.deleteAllStudents()
                lastSavedStudentName.value = ""
                lastStudentInSelectedRegistry.value = null
                onComplete(true)
            }
        } else {
            onComplete(false)
        }
    }

    // Saves camera image to reliable application local storage directory
    fun saveCameraImage(context: Context, uri: Uri): String? {
        try {
            val appDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
            val destFile = File(appDir, "registry_${System.currentTimeMillis()}.jpg")
            
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            return destFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    // Reactive duplicate results
    val duplicateResults = combine(
        allStudents,
        filterBinary,
        filterTrinary,
        filterQuad,
        filterExact
    ) { students, fb, ft, fq, fe ->
        if (!fb && !ft && !fq && !fe) return@combine emptyList()

        val n = when {
            fe -> -1 // Use full name
            fq -> 4
            ft -> 3
            fb -> 2
            else -> 0
        }
        if (n == 0) return@combine emptyList()

        // Helper to get the key
        fun getKey(name: String): String? {
            if (n == -1) return name
            val parts = name.split(" ").filter { it.isNotBlank() }
            if (parts.size < n) return null
            return parts.take(n).joinToString(" ")
        }

        // Group by key
        val groups = students.mapNotNull { student ->
            val key = getKey(student.normalizedName)
            if (key != null) key to student else null
        }.groupBy({ it.first }, { it.second })

        // Find clusters with > 1 student
        groups.filter { it.value.size > 1 }
            .map { it.value[0] to it.value.drop(1) }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Duplication discovery logic (REPLACED by duplicateResults flow)
    // fun getDiscoveredDuplicates(students: List<StudentEntity>): List<Pair<StudentEntity, List<StudentEntity>>> ... 
    // [Removed]

    // Export CSV functionality
    fun exportToCSV(context: Context): File? {
        val list = allStudents.value
        val file = File(context.cacheDir, "registry_export_${System.currentTimeMillis()}.csv")
        try {
            FileOutputStream(file).use { out ->
                // Write CSV headers (UTF-8 BOM to ensure Excel opens Arabic correctly)
                out.write(0xEF)
                out.write(0xBB)
                out.write(0xBF)
                val header = "الاسم,رقم القيد,رقم الصفحة,مسار الصورة\n"
                out.write(header.toByteArray(Charsets.UTF_8))

                for (student in list) {
                    val row = "\"${student.name}\",\"${student.registrationId}\",${student.pageNumber},\"${student.photoPath ?: ""}\"\n"
                    out.write(row.toByteArray(Charsets.UTF_8))
                }
            }
            return file
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    // JSON Backup / Restore
    fun exportToJSON(context: Context): File? {
        val list = allStudents.value
        val file = File(context.cacheDir, "registry_backup_${System.currentTimeMillis()}.json")
        try {
            val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            val listType = Types.newParameterizedType(List::class.java, StudentEntity::class.java)
            val jsonAdapter = moshi.adapter<List<StudentEntity>>(listType)
            val jsonString = jsonAdapter.toJson(list)
            FileOutputStream(file).use { it.write(jsonString.toByteArray(Charsets.UTF_8)) }
            return file
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    // Dashboard Statistics
    val totalRecordsCount = allStudents.map { it.size }.stateIn(viewModelScope, SharingStarted.Lazily, 0)
    val duplicateRecordsCount = allStudents.map { list ->
        list.groupBy { it.registrationId }.count { it.value.size > 1 }
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0)
    val needsReviewCount = allStudents.map { list ->
        list.count { it.name.isBlank() || it.registrationId.isBlank() }
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0)

    fun importFromJSON(context: Context, uri: Uri, onComplete: (Int, Int, String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                importFromInputStream(inputStream, onComplete)
            }
        }
    }

    fun importFromInputStream(inputStream: java.io.InputStream, onComplete: (Int, Int, String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            var imported = 0
            var skipped = 0
            val newConflicts = mutableListOf<com.example.data.Conflict>()
            try {
                val jsonString = inputStream.bufferedReader().use { it.readText() } ?: ""
                val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                val listType = Types.newParameterizedType(List::class.java, StudentEntity::class.java)
                val jsonAdapter = moshi.adapter<List<StudentEntity>>(listType)
                val records: List<StudentEntity>? = jsonAdapter.fromJson(jsonString)
                
                val db = AppDatabase.getDatabase(getApplication())
                db.withTransaction {
                    val existingStudents = repository.getAllStudents()
                    val normalizedNameMap = existingStudents.associateBy { it.normalizedName }.toMutableMap()
                    
                    records?.forEach { rec ->
                        kotlinx.coroutines.ensureActive()
                        val dupName = normalizedNameMap[rec.normalizedName]
                        if (dupName != null) {
                            val conflict = com.example.data.Conflict(rec, dupName)
                            newConflicts.add(conflict)
                            skipped++
                        } else {
                            repository.insertStudent(rec)
                            normalizedNameMap[rec.normalizedName] = rec
                            imported++
                        }
                    }
                }
                
                if (newConflicts.isNotEmpty()) {
                    _pendingConflicts.value = _pendingConflicts.value + newConflicts
                }
                
                withContext(Dispatchers.Main) {
                    onComplete(imported, skipped, null)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onComplete(0, 0, e.message)
                }
            }
        }
    }

    // Auto-save logic
    fun saveDraft(context: Context) {
        sharedPrefs.edit()
            .putString("draft_name", studentNameInput.value)
            .putString("draft_registry", registrationIdInput.value)
            .putString("draft_page", pageNumberInput.value)
            .putString("draft_photo", registryPhotoPath.value ?: "")
            .apply()
    }

    fun loadDraft(context: Context) {
        studentNameInput.value = sharedPrefs.getString("draft_name", "") ?: ""
        registrationIdInput.value = sharedPrefs.getString("draft_registry", "") ?: ""
        pageNumberInput.value = sharedPrefs.getString("draft_page", "") ?: ""
        val photo = sharedPrefs.getString("draft_photo", "")
        registryPhotoPath.value = if (photo.isNullOrBlank()) null else photo
    }

    fun clearDraft(context: Context) {
        sharedPrefs.edit()
            .remove("draft_name")
            .remove("draft_registry")
            .remove("draft_page")
            .remove("draft_photo")
            .apply()
    }

    // Import CSV content (runs names through ArabicNormalizer)
    fun importFromCSV(context: Context, uri: Uri, onComplete: (importedCount: Int, skippedCount: Int, errorMsg: String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            var imported = 0
            var skipped = 0
            var errorMsg: String? = null

            try {
                val records = mutableListOf<StudentEntity>()
                val reader = context.contentResolver.openInputStream(uri)?.bufferedReader()
                
                reader?.use { br ->
                    var isFirstLine = true
                    var line: String?
                    while (br.readLine().also { line = it } != null) {
                        val currentLine = line ?: continue
                        if (isFirstLine) {
                            isFirstLine = false
                            // Optional: verify header
                            if (currentLine.contains("الاسم") || currentLine.contains("Name")) {
                                continue
                            }
                        }

                        // Simple CSV parser supporting quotes
                        val tokens = parseCSVLine(currentLine)
                        if (tokens.size >= 3) {
                            val name = tokens[0].trim().replace("\"", "")
                            val registryNum = tokens[1].trim().replace("\"", "")
                            val pageNum = tokens[2].trim().toIntOrNull()
                            val photoPath = if (tokens.size >= 4) tokens[3].trim().replace("\"", "") else null

                            if (name.isNotEmpty() && registryNum.isNotEmpty() && pageNum != null) {
                                val normalized = ArabicNormalizer.normalize(name)
                                records.add(
                                    StudentEntity(
                                        name = name,
                                        normalizedName = normalized,
                                        registrationId = registryNum,
                                        pageNumber = pageNum,
                                        photoPath = if (photoPath.isNullOrBlank()) null else photoPath
                                    )
                                )
                            } else {
                                skipped++
                            }
                        } else {
                            if (currentLine.isNotBlank()) {
                                skipped++
                            }
                        }
                    }
                }

                // Batch insert into Database to preserve speed while enforcing checks
                val existingStudents = repository.getAllStudents()
                val normalizedNameMap = existingStudents.associateBy { it.normalizedName }.toMutableMap()
                val registryPageSet = existingStudents.map { "${it.registrationId}-${it.pageNumber}" }.toMutableSet()

                for (rec in records) {
                    var studentToInsert = rec
                    
                    if (normalizedNameMap.containsKey(studentToInsert.normalizedName)) {
                        val newName = studentToInsert.name + " (مكرر)"
                        val newNormalized = ArabicNormalizer.normalize(newName)
                        studentToInsert = studentToInsert.copy(name = newName, normalizedName = newNormalized)
                    }
                    
                    val regPageKey = "${studentToInsert.registrationId}-${studentToInsert.pageNumber}"
                    if (registryPageSet.contains(regPageKey)) {
                        val newName = studentToInsert.name + " (مكرر سجلي)"
                        val newNormalized = ArabicNormalizer.normalize(newName)
                        studentToInsert = studentToInsert.copy(name = newName, normalizedName = newNormalized)
                    }
                    
                    repository.insertStudent(studentToInsert)
                    normalizedNameMap[studentToInsert.normalizedName] = studentToInsert
                    registryPageSet.add("${studentToInsert.registrationId}-${studentToInsert.pageNumber}")
                    imported++
                }

            } catch (e: Exception) {
                e.printStackTrace()
                errorMsg = e.localizedMessage ?: "حدث خطأ غير معروف."
            }

            withContext(Dispatchers.Main) {
                onComplete(imported, skipped, errorMsg)
            }
        }
    }

    private fun parseCSVLine(line: String): List<String> {
        val list = mutableListOf<String>()
        var inQuotes = false
        val sb = StringBuilder()
        for (c in line) {
            when (c) {
                ',' -> {
                    if (inQuotes) {
                        sb.append(c)
                    } else {
                        list.add(sb.toString())
                        sb.setLength(0)
                    }
                }
                '"' -> {
                    inQuotes = !inQuotes
                }
                else -> {
                    sb.append(c)
                }
            }
        }
        list.add(sb.toString())
        return list
    }

    fun downloadAndMerge(context: Context, jsonString: String, onProgress: (Float) -> Unit, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val connectionData = try {
                Json.decodeFromString<ConnectionData>(jsonString)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onComplete(false, "Invalid QR Data") }
                return@launch
            }
            
            // Check storage space (e.g., require at least 100MB free)
            val stat = android.os.StatFs(context.cacheDir.path)
            val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
            if (freeBytes < 100 * 1024 * 1024) {
                withContext(Dispatchers.Main) { onComplete(false, "Not enough free storage space") }
                return@launch
            }

            val url = "http://${connectionData.ip}:${connectionData.port}/"
            val client = OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.MINUTES)
                .writeTimeout(10, java.util.concurrent.TimeUnit.MINUTES)
                .build()
            val request = Request.Builder().url(url).build()
            val zipFile = File(context.cacheDir, "downloaded.zip")
            
            var attempt = 0
            val maxAttempts = 3
            var success = false
            var lastError: String? = null

            while (attempt < maxAttempts && !success) {
                attempt++
                try {
                    kotlinx.coroutines.ensureActive()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            lastError = "Download failed: ${response.code}"
                            return@use
                        }
                        val body = response.body
                        if (body == null) {
                            lastError = "No body"
                            return@use
                        }
                        val totalBytes = body.contentLength()
                        var bytesRead = 0L
                        val buffer = ByteArray(8192)
                        var read: Int

                        body.byteStream().use { input ->
                            FileOutputStream(zipFile).use { output ->
                                while (input.read(buffer).also { read = it } != -1) {
                                    kotlinx.coroutines.ensureActive()
                                    output.write(buffer, 0, read)
                                    bytesRead += read
                                    if (totalBytes > 0) {
                                        val progress = bytesRead.toFloat() / totalBytes
                                        withContext(Dispatchers.Main) { onProgress(progress) }
                                    }
                                }
                            }
                        }
                        success = true
                    }
                } catch (e: Exception) {
                    lastError = e.message ?: "Unknown error"
                    kotlinx.coroutines.delay(1000)
                }
            }

            if (!success) {
                withContext(Dispatchers.Main) { onComplete(false, lastError) }
                return@launch
            }

            // Verify checksum if available
            if (connectionData.checksum != null) {
                try {
                    val md = java.security.MessageDigest.getInstance("SHA-256")
                    zipFile.inputStream().use { input ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            md.update(buffer, 0, read)
                        }
                    }
                    val downloadedChecksum = md.digest().joinToString("") { "%02x".format(it) }
                    if (downloadedChecksum != connectionData.checksum) {
                        zipFile.delete()
                        withContext(Dispatchers.Main) { onComplete(false, "Checksum mismatch! Download corrupted.") }
                        return@launch
                    }
                } catch (e: Exception) {
                    zipFile.delete()
                    withContext(Dispatchers.Main) { onComplete(false, "Failed to verify checksum: ${e.message}") }
                    return@launch
                }
            }

            try {
                com.example.utils.DataUnarchiver.unzipAndMerge(context, zipFile, this@RegistryViewModel) { mergeSuccess, error ->
                    zipFile.delete()
                    viewModelScope.launch(Dispatchers.Main) {
                        onComplete(mergeSuccess, error)
                    }
                }
            } catch (e: Exception) {
                zipFile.delete()
                withContext(Dispatchers.Main) { onComplete(false, e.message) }
            }
        }
    }
}

@Serializable
data class StaffRequest(val id: Int, val name: String, val status: String)
