# SmartFit BLE - Complete Architecture Documentation

## Table of Contents
1. [Project Overview](#project-overview)
2. [MVVM Architecture Explanation](#mvvm-architecture-explanation)
3. [Project Structure](#project-structure)
4. [Detailed File Documentation](#detailed-file-documentation)
   - [Application Layer](#application-layer)
   - [Dependency Injection](#dependency-injection)
   - [Data Layer](#data-layer)
   - [UI Layer (ViewModels)](#ui-layer-viewmodels)
   - [UI Layer (Views)](#ui-layer-views)
   - [Utilities](#utilities)
5. [Data Flow Diagrams](#data-flow-diagrams)
6. [Key Technologies Used](#key-technologies-used)

---

## Project Overview

**SmartFit BLE** is an Android application that connects to Bluetooth Low Energy (BLE) blood pressure monitoring devices, collects health data (heart rate, blood pressure), stores it locally using Room database, syncs with Firebase, and displays it through various UI screens including dashboard, history, and graphs.

### Key Features:
- BLE device scanning and connection
- Real-time health data collection from BLE devices
- Local data storage with Room database
- Cloud backup with Firebase Firestore
- User authentication (API and Google Sign-In)
- Dashboard with latest vitals
- Historical records with date grouping
- Interactive charts for trends
- Manual entry/editing of health records
- Dark mode and localization support

---

## MVVM Architecture Explanation

### What is MVVM?

**MVVM (Model-View-ViewModel)** is an architectural pattern that separates the UI (View) from the business logic (ViewModel) and data (Model). This separation makes the code more maintainable, testable, and scalable.

### How MVVM Works in This Project

```
┌─────────────────────────────────────────────────────────────┐
│                         VIEW LAYER                           │
│  (Activities, Fragments) - Only UI, no business logic        │
│  - Observes ViewModel StateFlows                             │
│  - Handles user interactions                                 │
│  - Updates UI based on ViewModel state                       │
└──────────────────────┬──────────────────────────────────────┘
                       │ Observes
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                      VIEWMODEL LAYER                         │
│  (ViewModels) - Business logic, state management             │
│  - Exposes StateFlows for UI to observe                      │
│  - Processes user actions                                    │
│  - Calls Repository methods                                  │
│  - Transforms data for UI consumption                        │
└──────────────────────┬──────────────────────────────────────┘
                       │ Uses
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                      REPOSITORY LAYER                        │
│  (Repositories) - Single source of truth                    │
│  - Combines data from multiple sources                       │
│  - Room Database (local)                                     │
│  - Firebase (cloud)                                          │
│  - BLE Repository (device communication)                     │
└──────────────────────┬──────────────────────────────────────┘
                       │ Uses
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                        DATA LAYER                            │
│  - Room Database (HealthDatabase, HealthRecordDao)          │
│  - Firebase (FirebaseHealthDataSource, FirebaseUserDataSource)│
│  - BLE (BleRepository)                                      │
│  - DataStore (SessionManager, UserPreferences)               │
└─────────────────────────────────────────────────────────────┘
```

### Key MVVM Concepts Used:

1. **StateFlow**: Reactive data streams that Views observe
   - ViewModels expose StateFlows
   - Views collect from StateFlows using `collectLatest`
   - UI automatically updates when data changes

2. **ViewModel**: Holds UI-related data that survives configuration changes
   - Created via ViewModelFactory
   - Lives independently of Views
   - Contains business logic

3. **Repository Pattern**: Abstracts data sources
   - Single source of truth
   - Combines local and remote data
   - Handles data synchronization

4. **Dependency Injection**: Manual DI via AppContainer
   - Centralized dependency management
   - Easy to test and maintain

---

## Project Structure

```
app/src/main/java/com/smartfit/ble/
├── SmartFitApp.kt                    # Application class
├── di/
│   └── AppContainer.kt                # Dependency injection container
├── data/
│   ├── LoginType.kt                   # Enum for login types
│   ├── SessionManager.kt              # Session management (DataStore)
│   ├── UserPreferences.kt            # UI preferences (DataStore)
│   ├── model/
│   │   ├── HealthRecord.kt           # Health data model
│   │   └── UserProfile.kt            # User profile model
│   ├── database/
│   │   ├── HealthDatabase.kt          # Room database
│   │   └── HealthRecordDao.kt        # Room DAO
│   ├── firebase/
│   │   ├── FirebaseHealthDataSource.kt # Firebase health data
│   │   └── FirebaseUserDataSource.kt  # Firebase user data
│   ├── ble/
│   │   ├── BleRepository.kt          # BLE device communication
│   │   ├── BleCharacteristicExtensions.kt # BLE helpers
│   │   └── model/
│   │       └── BleModels.kt          # BLE data models
│   └── repository/
│       └── HealthRepository.kt       # Main repository
└── ui/
    ├── splash/
    │   └── SplashActivity.kt          # Splash screen
    ├── login/
    │   └── LoginActivity.kt           # Login screen
    ├── main/
    │   └── MainActivity.kt            # Main activity with navigation
    ├── dashboard/
    │   ├── DashboardFragment.kt       # Dashboard view
    │   └── DashboardViewModel.kt      # Dashboard viewmodel
    ├── devices/
    │   ├── DevicesFragment.kt          # BLE devices view
    │   ├── DevicesViewModel.kt        # Devices viewmodel
    │   └── BleDeviceAdapter.kt         # RecyclerView adapter
    ├── history/
    │   ├── HistoryFragment.kt          # History view
    │   ├── HistoryViewModel.kt        # History viewmodel
    │   ├── HistoryAdapter.kt           # RecyclerView adapter
    │   └── HistoryItem.kt             # History item model
    ├── graphs/
    │   ├── GraphsFragment.kt           # Graphs view
    │   └── GraphsViewModel.kt         # Graphs viewmodel
    ├── addentry/
    │   ├── AddEntryDialogFragment.kt  # Add/edit entry dialog
    │   └── AddEntryViewModel.kt       # Add entry viewmodel
    ├── profile/
    │   └── ProfileFragment.kt          # Profile view
    └── information/
        └── InformationFragment.kt     # Information view
└── util/
    └── ToastUtil.kt                   # Toast utility
```

---

## Detailed File Documentation

### Application Layer

#### `SmartFitApp.kt`
**Purpose**: Application class that initializes the app and manages global settings.

**Key Components**:
- `appContainer`: Dependency injection container (lazy initialization)
- `userPreferences`: Manages UI preferences (dark mode, locale)
- `applicationScope`: Coroutine scope for app-level operations

**Main Functions**:
```kotlin
override fun onCreate() {
    super.onCreate()
    appContainer = AppContainer(this)  // Initialize DI container
    
    // Observe settings and apply them globally
    applicationScope.launch {
        userPreferences.settingsFlow.collect { settings ->
            // Apply dark mode
            AppCompatDelegate.setDefaultNightMode(...)
            // Apply locale (Hindi/English)
            AppCompatDelegate.setApplicationLocales(...)
        }
    }
}
```

**How it fits MVVM**: Provides the DI container that all ViewModels use to get repositories.

---

### Dependency Injection

#### `AppContainer.kt`
**Purpose**: Manual dependency injection container that creates and provides all dependencies.

**Key Components**:
- `database`: Room database instance (singleton)
- `sessionManager`: Session management
- `firebaseHealthDataSource`: Firebase health data operations
- `healthRepository`: Main repository combining Room + Firebase
- `bleRepository`: BLE device communication repository

**Main Functions**:
```kotlin
class AppContainer(context: Context) {
    // Lazy initialization of dependencies
    private val database: HealthDatabase by lazy {
        HealthDatabase.getDatabase(appContext)
    }
    
    val healthRepository: HealthRepository by lazy {
        HealthRepository(
            dao = database.healthRecordDao(),
            firebase = firebaseHealthDataSource,
            sessionManager = sessionManager
        )
    }
    
    val bleRepository: BleRepository by lazy {
        BleRepository(appContext, healthRepository)
    }
}
```

**How it fits MVVM**: Provides ViewModels with their dependencies (repositories) through ViewModelFactory.

---

### Data Layer

#### `HealthRecord.kt` (Model)
**Purpose**: Data class representing a health record with heart rate and blood pressure.

**Key Properties**:
- `id`: Primary key (auto-generated)
- `heartRate`: Heart rate in BPM
- `systolicBP`: Systolic blood pressure in mmHg
- `diastolicBP`: Diastolic blood pressure in mmHg
- `timestamp`: Unix timestamp

**Main Code**:
```kotlin
@Entity(tableName = "health_records")
data class HealthRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val heartRate: Int,
    val systolicBP: Int,
    val diastolicBP: Int,
    val timestamp: Long = System.currentTimeMillis()
) {
    val bloodPressure: String
        get() = "$systolicBP/$diastolicBP"
}
```

**How it fits MVVM**: Model layer - represents the data structure used throughout the app.

---

#### `HealthDatabase.kt`
**Purpose**: Room database definition - provides access to the DAO.

**Key Components**:
- Singleton pattern for database instance
- Defines entities and version

**Main Code**:
```kotlin
@Database(
    entities = [HealthRecord::class],
    version = 1,
    exportSchema = false
)
abstract class HealthDatabase : RoomDatabase() {
    abstract fun healthRecordDao(): HealthRecordDao
    
    companion object {
        @Volatile
        private var INSTANCE: HealthDatabase? = null
        
        fun getDatabase(context: Context): HealthDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(...).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
```

**How it fits MVVM**: Part of the Model layer - provides local data persistence.

---

#### `HealthRecordDao.kt`
**Purpose**: Data Access Object (DAO) for Room database operations.

**Key Functions**:
- `getAllRecords()`: Returns Flow of all records
- `observeLatestRecord()`: Returns Flow of latest record
- `getTodayRecordsCount()`: Returns count of today's records
- `insertRecord()`: Inserts a new record
- `updateRecord()`: Updates existing record
- `deleteRecord()`: Deletes a record
- `existsByTimestamp()`: Checks if record exists by timestamp

**Main Code**:
```kotlin
@Dao
interface HealthRecordDao {
    @Query("SELECT * FROM health_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<HealthRecord>>
    
    @Query("SELECT * FROM health_records ORDER BY timestamp DESC LIMIT 1")
    fun observeLatestRecord(): Flow<HealthRecord?>
    
    @Insert
    suspend fun insertRecord(record: HealthRecord): Long
    
    @Update
    suspend fun updateRecord(record: HealthRecord)
    
    @Delete
    suspend fun deleteRecord(record: HealthRecord)
}
```

**How it fits MVVM**: Part of the Model layer - provides reactive data access using Kotlin Flows.

---

#### `HealthRepository.kt`
**Purpose**: Repository that combines local (Room) and remote (Firebase) data sources.

**Key Responsibilities**:
- Single source of truth for health data
- Handles local-first approach
- Syncs with Firebase when user is logged in
- Restores data from Firebase on app start

**Main Functions**:

1. **Local Data Access**:
```kotlin
fun getAllRecords(): Flow<List<HealthRecord>> = dao.getAllRecords()
fun observeLatestRecord(): Flow<HealthRecord?> = dao.observeLatestRecord()
fun getTodayRecordsCount(): Flow<Int> { ... }
```

2. **Insert with Sync**:
```kotlin
suspend fun insertRecord(record: HealthRecord) {
    // 1. Save locally first
    dao.insertRecord(record)
    
    // 2. Get user UID
    val firebaseUid = sessionManager.sessionFlow.firstOrNull()?.firebaseUid
    
    // 3. Upload to Firebase if logged in
    if (!firebaseUid.isNullOrBlank()) {
        firebase.uploadHealthRecord(firebaseUid, record)
    }
}
```

3. **Backup Restore**:
```kotlin
suspend fun restoreFromFirebaseIfNeeded() {
    val firebaseUid = sessionManager.sessionFlow.firstOrNull()?.firebaseUid ?: return
    val remoteRecords = firebase.fetchAllHealthRecords(firebaseUid)
    
    // Insert only missing records (no duplicates)
    for (record in remoteRecords) {
        val exists = dao.existsByTimestamp(record.timestamp) > 0
        if (!exists) {
            dao.insertRecord(record)
        }
    }
}
```

**How it fits MVVM**: Core of the Model layer - ViewModels use this repository to get data, abstracting away the data source details.

---

#### `BleRepository.kt`
**Purpose**: Handles all Bluetooth Low Energy operations - scanning, connecting, and reading data from BLE devices.

**Key Components**:
- `devices`: StateFlow of discovered devices
- `isScanning`: StateFlow indicating scan status
- `connectionState`: StateFlow of connection state
- `connectedInfo`: StateFlow of connected device info
- `bpMeasurements`: SharedFlow of blood pressure readings

**Main Functions**:

1. **Device Scanning**:
```kotlin
fun startScan() {
    val scanner = adapter.bluetoothLeScanner ?: return
    
    val scanFilter = ScanFilter.Builder()
        .setServiceUuid(TARGET_SERVICE_UUID)  // Filter by service UUID
        .build()
    
    scanner.startScan(listOf(scanFilter), scanSettings, callback)
    _isScanning.value = true
}
```

2. **Device Connection**:
```kotlin
fun connectToDevice(device: BleDevice) {
    _connectionState.value = DeviceConnectionState.CONNECTING
    bluetoothGatt = adapter.getRemoteDevice(device.address)
        .connectGatt(context, false, gattCallback)
}
```

3. **Reading Health Data**:
```kotlin
override fun onCharacteristicChanged(
    gatt: BluetoothGatt,
    characteristic: BluetoothGattCharacteristic
) {
    if (characteristic.uuid == PRESSURE_MEASUREMENT_CHAR_UUID) {
        val data = characteristic.value ?: return
        val bp = parsePressureMeasurement(data) ?: return
        
        // Emit to UI
        _bpMeasurements.emit(bp)
        
        // Save to repository
        val record = HealthRecord(
            heartRate = bp.pulse ?: 0,
            systolicBP = bp.systolic,
            diastolicBP = bp.diastolic,
            timestamp = bp.timestamp
        )
        healthRepository.insertRecord(record)
    }
}
```

**Key UUIDs Used**:
- Blood Pressure Service: `56484AAE-A8EB-4A97-AC19-A8EA6373E05A`
- Pressure Measurement Characteristic: `2DB34480-BCE5-4BB7-9F56-55BD202317C5`
- Device Info Service: `0000180a-0000-1000-8000-00805f9b34fb`
- Battery Service: `0000180f-0000-1000-8000-00805f9b34fb`

**How it fits MVVM**: Part of the Model layer - provides BLE data as reactive streams that ViewModels can observe.

---

#### `FirebaseHealthDataSource.kt`
**Purpose**: Handles Firebase Firestore operations for health records.

**Main Functions**:

1. **Upload Record**:
```kotlin
suspend fun uploadHealthRecord(uid: String, record: HealthRecord) {
    firestore
        .collection("users")
        .document(uid)
        .collection("health_records")
        .add(mapOf(
            "heartRate" to record.heartRate,
            "systolicBP" to record.systolicBP,
            "diastolicBP" to record.diastolicBP,
            "timestamp" to record.timestamp
        ))
        .await()
}
```

2. **Fetch All Records**:
```kotlin
suspend fun fetchAllHealthRecords(uid: String): List<HealthRecord> {
    val snapshot = firestore
        .collection("users")
        .document(uid)
        .collection("health_records")
        .get()
        .await()
    
    return snapshot.documents.mapNotNull { doc ->
        HealthRecord(
            heartRate = doc.getLong("heartRate")?.toInt() ?: return@mapNotNull null,
            systolicBP = doc.getLong("systolicBP")?.toInt() ?: return@mapNotNull null,
            diastolicBP = doc.getLong("diastolicBP")?.toInt() ?: return@mapNotNull null,
            timestamp = doc.getLong("timestamp") ?: return@mapNotNull null
        )
    }
}
```

**How it fits MVVM**: Part of the Model layer - provides cloud data persistence.

---

#### `SessionManager.kt`
**Purpose**: Manages user session data using DataStore.

**Key Components**:
- `sessionFlow`: Flow of current session (null if not logged in)
- `saveSession()`: Saves API login session
- `saveGoogleSession()`: Saves Google login session
- `clearSession()`: Logs out user

**Main Code**:
```kotlin
class SessionManager(private val context: Context) {
    val sessionFlow: Flow<AuthSession?> = dataStore.data
        .map { prefs ->
            val accessToken = prefs[ACCESS_TOKEN] ?: return@map null
            AuthSession(
                id = prefs[USER_ID]?.toIntOrNull() ?: -1,
                firebaseUid = prefs[FIREBASE_UID].orEmpty(),
                username = prefs[USERNAME].orEmpty(),
                // ... other fields
            )
        }
    
    suspend fun saveSession(response: LoginResponse) {
        dataStore.edit { prefs ->
            prefs[USER_ID] = response.id.toString()
            prefs[FIREBASE_UID] = response.username
            // ... save all session data
        }
    }
}
```

**How it fits MVVM**: Part of the Model layer - provides session state that ViewModels can observe.

---

#### `UserPreferences.kt`
**Purpose**: Manages UI preferences (dark mode, locale) using DataStore.

**Main Code**:
```kotlin
class UserPreferences(private val context: Context) {
    val settingsFlow: Flow<UiSettings> = dataStore.data
        .map { preferences ->
            UiSettings(
                isDarkMode = preferences[DARK_MODE_ENABLED] ?: false,
                useHindi = preferences[USE_HINDI_LOCALE] ?: false
            )
        }
    
    suspend fun setDarkMode(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[DARK_MODE_ENABLED] = enabled
        }
    }
}
```

**How it fits MVVM**: Part of the Model layer - provides UI settings state.

---

### UI Layer (ViewModels)

#### `DashboardViewModel.kt`
**Purpose**: ViewModel for the dashboard screen - provides latest health data and statistics.

**Exposed StateFlows**:
- `latestRecord`: Latest health record (null if none)
- `todayCount`: Number of records today
- `totalCount`: Total number of records

**Main Code**:
```kotlin
class DashboardViewModel(private val repository: HealthRepository) : ViewModel() {
    val latestRecord: StateFlow<HealthRecord?> = repository
        .observeLatestRecord()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = null
        )
    
    val todayCount: StateFlow<Int> = repository
        .getTodayRecordsCount()
        .stateIn(...)
    
    val totalCount: StateFlow<Int> = repository
        .getAllRecords()
        .map { it.size }
        .stateIn(...)
}
```

**How it fits MVVM**: ViewModel layer - transforms repository data into UI-ready StateFlows. The Fragment observes these StateFlows.

---

#### `DevicesViewModel.kt`
**Purpose**: ViewModel for BLE device scanning and connection.

**Exposed StateFlows**:
- `devices`: List of discovered BLE devices
- `isScanning`: Whether scanning is active
- `connectionState`: Current connection state
- `connectedInfo`: Information about connected device
- `bpMeasurements`: Blood pressure readings from device

**Main Code**:
```kotlin
class DevicesViewModel(
    private val bleRepository: BleRepository
) : ViewModel() {
    val devices = bleRepository.devices
    val isScanning = bleRepository.isScanning
    val connectionState = bleRepository.connectionState
    val connectedInfo = bleRepository.connectedInfo
    val bpMeasurements = bleRepository.bpMeasurements
    
    fun startScan() = bleRepository.startScan()
    fun stopScan() = bleRepository.stopScan()
    fun connectToDevice(device: BleDevice) = bleRepository.connectToDevice(device)
    fun disconnect() = bleRepository.disconnect()
}
```

**How it fits MVVM**: ViewModel layer - acts as a pass-through to BLE repository, exposing its StateFlows to the View.

---

#### `HistoryViewModel.kt`
**Purpose**: ViewModel for the history screen - provides all health records.

**Exposed StateFlows**:
- `records`: List of all health records

**Main Functions**:
```kotlin
class HistoryViewModel(private val repository: HealthRepository) : ViewModel() {
    val records: StateFlow<List<HealthRecord>> = repository
        .getAllRecords()
        .stateIn(...)
    
    fun deleteRecord(record: HealthRecord) {
        viewModelScope.launch {
            repository.deleteRecord(record)
        }
    }
}
```

**How it fits MVVM**: ViewModel layer - provides records list and handles deletion operations.

---

#### `GraphsViewModel.kt`
**Purpose**: ViewModel for the graphs screen - transforms health records into chart data points.

**Exposed StateFlows**:
- `heartRatePoints`: Chart points for heart rate
- `systolicPoints`: Chart points for systolic BP
- `diastolicPoints`: Chart points for diastolic BP

**Main Code**:
```kotlin
class GraphsViewModel(private val repository: HealthRepository) : ViewModel() {
    private val sortedRecordsFlow = repository.getAllRecords()
        .map { it.sortedBy { r -> r.timestamp } }
        .stateIn(...)
    
    val heartRatePoints: StateFlow<List<ChartPoint>> = sortedRecordsFlow
        .map { list ->
            list.mapIndexed { index, record ->
                val color = when {
                    record.heartRate < 60 || record.heartRate > 100 -> 2  // abnormal
                    else -> 0  // normal
                }
                ChartPoint(
                    xIndex = index.toFloat(),
                    y = record.heartRate.toFloat(),
                    timestamp = record.timestamp,
                    colorType = color
                )
            }
        }
        .stateIn(...)
}
```

**How it fits MVVM**: ViewModel layer - transforms raw health records into chart-ready data points with color coding for normal/abnormal values.

---

#### `AddEntryViewModel.kt`
**Purpose**: ViewModel for adding/editing health records manually.

**Exposed StateFlows**:
- `isSaving`: Whether save operation is in progress
- `saveSuccess`: Whether save was successful
- `editingRecord`: Record being edited (null for new entry)
- `warningMessage`: Warning message for abnormal values
- `showConfirmationDialog`: Whether to show confirmation dialog

**Main Functions**:

1. **Load Record for Editing**:
```kotlin
fun loadRecord(recordId: Long) {
    viewModelScope.launch {
        _editingRecord.value = repository.getRecordById(recordId)
    }
}
```

2. **Submit Record with Validation**:
```kotlin
fun submitRecord(
    heartRate: Int,
    systolicBP: Int,
    diastolicBP: Int,
    timestamp: Long = System.currentTimeMillis(),
    forceSave: Boolean = false
) {
    // Check for warnings if not forced
    if (!forceSave && !checkForWarnings(heartRate, systolicBP, diastolicBP)) {
        return  // Show confirmation dialog
    }
    
    viewModelScope.launch {
        _isSaving.value = true
        val existing = _editingRecord.value
        if (existing == null) {
            repository.insertRecord(HealthRecord(...))
        } else {
            repository.updateRecord(existing.copy(...))
        }
        _saveSuccess.value = true
    }
}
```

3. **Warning Check**:
```kotlin
private fun checkForWarnings(...): Boolean {
    val warnings = mutableListOf<String>()
    
    if (heartRate !in NORMAL_HR_RANGE) {
        warnings += "Heart rate is outside the normal range..."
    }
    // Similar checks for BP
    
    return if (warnings.isNotEmpty()) {
        _warningMessage.value = warnings.joinToString("\n")
        _showConfirmationDialog.value = true
        false
    } else {
        true
    }
}
```

**How it fits MVVM**: ViewModel layer - handles form validation, warning logic, and save operations.

---

### UI Layer (Views)

#### `SplashActivity.kt`
**Purpose**: Initial screen that checks if user is logged in and navigates accordingly.

**Main Flow**:
```kotlin
private fun checkSessionAndNavigate() {
    lifecycleScope.launch {
        val session = sessionManager.sessionFlow.firstOrNull()
        if (session == null) {
            navigateToLogin()
            return@launch
        }
        
        val isValid = validateSession(session)
        if (isValid) {
            navigateToMain()
        } else {
            navigateToLogin()
        }
    }
}

private suspend fun validateSession(session: AuthSession): Boolean {
    return when (session.loginType) {
        LoginType.API -> {
            try {
                authClient.me(session.accessToken)
                true
            } catch (_: Exception) {
                sessionManager.clearSession()
                false
            }
        }
        LoginType.GOOGLE -> {
            FirebaseAuth.getInstance().currentUser != null
        }
    }
}
```

**How it fits MVVM**: View layer - checks session state and navigates. No ViewModel needed for this simple screen.

---

#### `LoginActivity.kt`
**Purpose**: Handles user authentication (API and Google Sign-In).

**Main Functions**:

1. **API Login**:
```kotlin
private fun attemptApiLogin() {
    lifecycleScope.launch {
        setLoading(true)
        try {
            val response = authClient.login(username, password)
            
            // Save session
            sessionManager.saveSession(response)
            
            // Create Firebase profile
            firebaseUserDataSource.createUserIfNotExists(UserProfile(...))
            
            // Clear local data (fresh start for new user)
            healthRepository.clearLocalData()
            
            navigateToMain()
        } catch (e: Exception) {
            ToastUtil.showError(...)
        } finally {
            setLoading(false)
        }
    }
}
```

2. **Google Login**:
```kotlin
private fun firebaseLogin(idToken: String) {
    lifecycleScope.launch {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val firebaseUser = FirebaseAuth.getInstance()
            .signInWithCredential(credential)
            .await()
            .user ?: throw IllegalStateException("Firebase user null")
        
        // Create/update profile
        firebaseUserDataSource.createUserIfNotExists(UserProfile(...))
        
        // Save session
        sessionManager.saveGoogleSession(AuthSession(...))
        
        // Clear local data
        healthRepository.clearLocalData()
        
        navigateToMain()
    }
}
```

**How it fits MVVM**: View layer - handles authentication directly. No ViewModel needed as it's a simple form submission.

---

#### `MainActivity.kt`
**Purpose**: Main activity that hosts all fragments and manages navigation.

**Key Responsibilities**:
- Sets up Navigation Component
- Checks session on startup
- Restores backup from Firebase
- Manages bottom navigation and top app bar

**Main Code**:
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Check session and restore backup
    lifecycleScope.launch {
        val session = sessionManager.sessionFlow.firstOrNull()
        if (session == null) {
            redirectToLogin()
            return@launch
        }
        
        // Restore backup from Firebase
        healthRepository.restoreFromFirebaseIfNeeded()
    }
    
    setupNavigation()
}

private fun setupNavigation() {
    val navHost = supportFragmentManager
        .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
    val navController = navHost.navController
    
    // Setup bottom navigation
    binding.bottomNav.setupWithNavController(navController)
    
    // Setup top app bar
    binding.topAppBar.setupWithNavController(navController, appBarConfiguration)
}
```

**How it fits MVVM**: View layer - container for fragments, handles navigation.

---

#### `DashboardFragment.kt`
**Purpose**: Displays latest health data and statistics.

**MVVM Flow**:
1. Fragment observes ViewModel StateFlows
2. ViewModel exposes data from Repository
3. Repository provides data from Room/Firebase
4. UI updates automatically when data changes

**Main Code**:
```kotlin
class DashboardFragment : Fragment() {
    private val viewModel: DashboardViewModel by viewModels {
        DashboardViewModelFactory(appContainer.healthRepository)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        observeViewModel()
        loadUserGreeting()
        loadStats()
    }
    
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.latestRecord.collectLatest { record ->
                updateDashboard(record)
            }
        }
    }
    
    private fun updateDashboard(record: HealthRecord?) {
        if (record != null) {
            binding.tvHeartRate.text = "${record.heartRate} ${getString(R.string.bpm)}"
            binding.tvBloodPressure.text = record.bloodPressure
        } else {
            binding.tvHeartRate.text = "-- ${getString(R.string.bpm)}"
            binding.tvBloodPressure.text = "--/--"
        }
    }
}
```

**How it fits MVVM**: 
- **View**: Fragment observes ViewModel StateFlows
- **ViewModel**: Transforms repository data into UI-ready StateFlows
- **Model**: Repository provides data from Room database

---

#### `DevicesFragment.kt`
**Purpose**: Handles BLE device scanning, connection, and displays connected device info.

**Key Features**:
- Permission handling (Bluetooth, Location)
- Device scanning with timeout
- Device connection/disconnection
- Real-time display of connected device info
- Shows blood pressure readings as they arrive

**Main Code**:
```kotlin
class DevicesFragment : Fragment() {
    private val viewModel: DevicesViewModel by viewModels {
        DevicesViewModelFactory(appContainer.bleRepository)
    }
    
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            // Observe devices list
            viewModel.devices.collectLatest { devices ->
                deviceAdapter.submitList(devices)
            }
            
            // Observe scanning state
            viewModel.isScanning.collectLatest { isScanning ->
                updateScanUi(isScanning, viewModel.connectionState.value)
            }
            
            // Observe connection info
            viewModel.connectedInfo.collectLatest { info ->
                if (info != null) {
                    binding.tvConnectedDevice.text = info.displayName
                    binding.tvSerial.text = info.serialNumber ?: "--"
                    binding.tvBattery.text = "${info.batteryPercent}%"
                }
            }
            
            // Observe BP measurements
            viewModel.bpMeasurements.collectLatest {
                showSnackbar("Reading collected successfully")
            }
        }
    }
    
    private fun toggleScan() {
        if (viewModel.isScanning.value) {
            viewModel.stopScan()
        } else {
            if (hasAllPermissions()) {
                viewModel.startScan()
            } else {
                requestPermissions { viewModel.startScan() }
            }
        }
    }
}
```

**How it fits MVVM**:
- **View**: Fragment observes BLE StateFlows and handles user interactions
- **ViewModel**: Exposes BLE repository StateFlows
- **Model**: BLE repository handles device communication and emits data

---

#### `HistoryFragment.kt`
**Purpose**: Displays all health records grouped by date (Today, Yesterday, Previous 7 days, Older).

**Key Features**:
- Groups records by date
- Swipe-to-delete with undo
- Click to edit record
- Empty state handling

**Main Code**:
```kotlin
class HistoryFragment : Fragment() {
    private val viewModel: HistoryViewModel by viewModels {
        HistoryViewModelFactory(appContainer.healthRepository)
    }
    
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.records.collectLatest { records ->
                if (records.isEmpty()) {
                    binding.rvHistory.visibility = View.GONE
                    binding.tvEmptyState.visibility = View.VISIBLE
                } else {
                    binding.rvHistory.visibility = View.VISIBLE
                    binding.tvEmptyState.visibility = View.GONE
                    historyAdapter.submitList(groupRecordsByDate(records))
                }
            }
        }
    }
    
    private fun groupRecordsByDate(records: List<HealthRecord>): List<HistoryItem> {
        val items = mutableListOf<HistoryItem>()
        
        // Group into Today, Yesterday, Previous 7 days, Older
        val todayRecords = records.filter { ... }
        val yesterdayRecords = records.filter { ... }
        // ... more grouping logic
        
        if (todayRecords.isNotEmpty()) {
            items.add(HistoryItem.Header("Today"))
            todayRecords.forEach { items.add(HistoryItem.Record(it)) }
        }
        // ... add other groups
        
        return items
    }
    
    private fun deleteRecordWithUndo(record: HealthRecord) {
        viewModel.deleteRecord(record)
        
        Snackbar.make(binding.root, "Entry deleted", Snackbar.LENGTH_LONG)
            .setAction("Undo") {
                lifecycleScope.launch {
                    appContainer.healthRepository.insertRecord(record)
                }
            }
            .show()
    }
}
```

**How it fits MVVM**:
- **View**: Fragment observes records StateFlow, groups them, handles UI interactions
- **ViewModel**: Provides records list and delete function
- **Model**: Repository manages data persistence

---

#### `GraphsFragment.kt`
**Purpose**: Displays interactive charts for heart rate and blood pressure trends.

**Key Features**:
- Line charts using MPAndroidChart library
- Color-coded points (green=normal, orange=elevated, red=high)
- Interactive charts (zoom, pan)
- Date formatting on X-axis

**Main Code**:
```kotlin
class GraphsFragment : Fragment() {
    private val viewModel: GraphsViewModel by viewModels {
        GraphsViewModelFactory(appContainer.healthRepository)
    }
    
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            combine(
                viewModel.heartRatePoints,
                viewModel.systolicPoints,
                viewModel.diastolicPoints
            ) { hr, sbp, dbp -> Triple(hr, sbp, dbp) }
                .collectLatest { (hr, sbp, dbp) ->
                    if (hr.isEmpty()) {
                        binding.tvNoData.visibility = View.VISIBLE
                    } else {
                        updateHeartRateChart(hr)
                        updateBloodPressureChart(sbp, dbp)
                    }
                }
        }
    }
    
    private fun updateHeartRateChart(points: List<ChartPoint>) {
        val entries = points.map { Entry(it.xIndex, it.y) }
        
        val dataSet = LineDataSet(entries, "Heart Rate").apply {
            color = requireContext().getColor(R.color.smartfit_primary)
            circleColors = points.map {
                when (it.colorType) {
                    0 -> R.color.smartfit_green      // normal
                    1 -> R.color.smartfit_orange      // elevated
                    else -> R.color.error_background  // high
                }
            }
        }
        
        binding.chartHeartRate.data = LineData(dataSet)
        binding.chartHeartRate.invalidate()
    }
}
```

**How it fits MVVM**:
- **View**: Fragment observes chart data StateFlows and renders charts
- **ViewModel**: Transforms health records into chart points with color coding
- **Model**: Repository provides health records

---

#### `AddEntryDialogFragment.kt`
**Purpose**: Dialog for manually adding or editing health records.

**Key Features**:
- Form validation
- Date/time picker
- Warning for abnormal values
- Confirmation dialog for abnormal values
- Edit existing records

**Main Code**:
```kotlin
class AddEntryDialogFragment : DialogFragment() {
    private val viewModel: AddEntryViewModel by viewModels {
        AddEntryViewModelFactory(appContainer.healthRepository)
    }
    
    private fun observeViewModel() {
        // Observe save success
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.saveSuccess.collectLatest { success ->
                if (success) {
                    ToastUtil.showSuccess(...)
                    dismiss()
                }
            }
        }
        
        // Observe editing record
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.editingRecord.collectLatest { record ->
                if (record != null) {
                    binding.etHeartRate.setText(record.heartRate.toString())
                    binding.etSystolicBP.setText(record.systolicBP.toString())
                    binding.etDiastolicBP.setText(record.diastolicBP.toString())
                }
            }
        }
        
        // Observe confirmation dialog
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.showConfirmationDialog.collectLatest { shouldShow ->
                if (shouldShow) {
                    AlertDialog.Builder(requireContext())
                        .setTitle("Warning")
                        .setMessage(viewModel.warningMessage.value)
                        .setPositiveButton("Save") { _, _ ->
                            viewModel.submitRecord(..., forceSave = true)
                        }
                        .show()
                }
            }
        }
    }
    
    private fun saveEntry() {
        val hr = binding.etHeartRate.text.toString().toInt()
        val sys = binding.etSystolicBP.text.toString().toInt()
        val dia = binding.etDiastolicBP.text.toString().toInt()
        
        if (!validateInput(hr, sys, dia)) return
        
        viewModel.submitRecord(
            heartRate = hr,
            systolicBP = sys,
            diastolicBP = dia,
            timestamp = selectedTimestamp
        )
    }
}
```

**How it fits MVVM**:
- **View**: Dialog observes ViewModel StateFlows, handles form input
- **ViewModel**: Validates input, checks for warnings, saves to repository
- **Model**: Repository persists the data

---

#### `ProfileFragment.kt`
**Purpose**: Displays user profile and settings (dark mode, locale).

**Main Code**:
```kotlin
class ProfileFragment : Fragment() {
    private val sessionManager by lazy { SessionManager(...) }
    private val userPreferences by lazy { UserPreferences(...) }
    
    private fun loadUserSessionData() {
        viewLifecycleOwner.lifecycleScope.launch {
            sessionManager.sessionFlow.collectLatest { session ->
                if (session != null) {
                    binding.tvName.text = session.fullName
                    binding.tvEmailValue.text = session.email
                    binding.tvUsernameValue.text = "@${session.username}"
                    
                    // Load profile image or show initials
                    if (session.imageUrl.isNotEmpty()) {
                        binding.ivProfileImage.load(session.imageUrl)
                    } else {
                        binding.tvInitials.text = session.initials
                    }
                }
            }
        }
    }
    
    private fun loadSettings() {
        viewLifecycleOwner.lifecycleScope.launch {
            userPreferences.settingsFlow.collectLatest { settings ->
                binding.switchDarkMode.isChecked = settings.isDarkMode
                binding.switchLocalization.isChecked = settings.useHindi
            }
        }
    }
    
    private fun logout() {
        viewLifecycleOwner.lifecycleScope.launch {
            sessionManager.clearSession()
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            requireActivity().finish()
        }
    }
}
```

**How it fits MVVM**: View layer - directly observes SessionManager and UserPreferences flows. No ViewModel needed for simple profile display.

---

### Utilities

#### `ToastUtil.kt`
**Purpose**: Utility class for showing toast messages consistently.

**How it fits MVVM**: Utility - used by Views to show user feedback.

---

## Data Flow Diagrams

### Adding a Health Record (Manual Entry)

```
User Input (AddEntryDialogFragment)
    ↓
AddEntryViewModel.submitRecord()
    ↓
HealthRepository.insertRecord()
    ├─→ HealthRecordDao.insertRecord()  (Local - Room)
    └─→ FirebaseHealthDataSource.uploadHealthRecord()  (Cloud - Firebase)
    ↓
StateFlow Updates
    ↓
All Observers Notified:
    - DashboardFragment (latestRecord)
    - HistoryFragment (records list)
    - GraphsFragment (chart points)
```

### BLE Device Reading Flow

```
BLE Device sends data
    ↓
BleRepository.onCharacteristicChanged()
    ↓
Parse blood pressure data
    ↓
Emit to bpMeasurements SharedFlow
    ↓
Create HealthRecord
    ↓
HealthRepository.insertRecord()
    ├─→ Room Database (local)
    └─→ Firebase (cloud)
    ↓
StateFlow Updates
    ↓
UI Updates:
    - DevicesFragment (shows "Reading collected")
    - DashboardFragment (shows latest vitals)
    - HistoryFragment (adds to list)
    - GraphsFragment (adds point to chart)
```

### Login Flow

```
User enters credentials
    ↓
LoginActivity.attemptApiLogin()
    ↓
AuthClient.login()  (API call)
    ↓
SessionManager.saveSession()
    ├─→ DataStore (local persistence)
    └─→ FirebaseUserDataSource.createUserIfNotExists()
    ↓
HealthRepository.clearLocalData()  (Fresh start)
    ↓
Navigate to MainActivity
    ↓
MainActivity.onCreate()
    ↓
HealthRepository.restoreFromFirebaseIfNeeded()
    ├─→ Fetch from Firebase
    └─→ Insert missing records to Room
```

---

## Key Technologies Used

### Architecture & Patterns
- **MVVM (Model-View-ViewModel)**: Separation of concerns
- **Repository Pattern**: Single source of truth
- **Dependency Injection**: Manual DI via AppContainer
- **Reactive Programming**: Kotlin Flows and StateFlow

### Android Components
- **Room Database**: Local data persistence
- **DataStore**: Key-value and typed data storage
- **Navigation Component**: Fragment navigation
- **ViewBinding**: Type-safe view references
- **Lifecycle Components**: Lifecycle-aware components

### Libraries
- **Firebase**: Authentication and Firestore
- **MPAndroidChart**: Chart rendering
- **Coil**: Image loading
- **Kotlin Coroutines**: Asynchronous programming
- **Kotlin Flow**: Reactive streams

### BLE
- **Android Bluetooth Low Energy API**: Device communication
- **GATT (Generic Attribute Profile)**: BLE protocol

---

## Summary

This SmartFit BLE application follows a clean MVVM architecture where:

1. **Views (Fragments/Activities)**: Only handle UI and user interactions, observe ViewModel StateFlows
2. **ViewModels**: Contain business logic, transform data for UI, expose StateFlows
3. **Repositories**: Single source of truth, combine local and remote data sources
4. **Data Sources**: Room (local), Firebase (cloud), BLE (device), DataStore (preferences)

The architecture ensures:
- **Separation of Concerns**: Each layer has a clear responsibility
- **Testability**: ViewModels can be tested independently
- **Maintainability**: Changes in one layer don't affect others
- **Reactivity**: UI automatically updates when data changes
- **Scalability**: Easy to add new features or data sources

---

*Document Generated: 2024*
*Project: SmartFit BLE v1.2.1*

