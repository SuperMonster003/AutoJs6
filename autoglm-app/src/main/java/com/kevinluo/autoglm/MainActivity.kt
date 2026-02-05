package com.kevinluo.autoglm

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.widget.Toast
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.kevinluo.autoglm.ui.MainUiEvent
import com.kevinluo.autoglm.ui.MainViewModel
import com.kevinluo.autoglm.ui.ShizukuStatus
import com.kevinluo.autoglm.util.Logger
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

/**
 * Main Activity that hosts the bottom navigation and Fragment container.
 *
 * Responsible for:
 * - Setting up Navigation with BottomNavigationView
 * - Managing Shizuku service connection
 * - Coordinating cross-Fragment state via MainViewModel
 */
class MainActivity : BaseActivity() {
    private lateinit var navController: NavController
    private lateinit var bottomNav: BottomNavigationView
    private val viewModel: MainViewModel by viewModels()

    private val componentManager: ComponentManager by lazy {
        ComponentManager.getInstance(applicationContext)
    }

    // Shizuku service connection
    private var userService: IUserService? = null
    private var isServiceBound = false

    private val userServiceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                Logger.i(TAG, "UserService connected")
                userService = IUserService.Stub.asInterface(service)
                userService?.let { svc ->
                    componentManager.onServiceConnected(svc)
                    // TaskExecutionManager is now the sole PhoneAgentListener
                    viewModel.updateShizukuStatus(ShizukuStatus.CONNECTED)
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                Logger.i(TAG, "UserService disconnected")
                userService = null
                componentManager.onServiceDisconnected()
                viewModel.updateShizukuStatus(ShizukuStatus.NOT_RUNNING)
            }
        }

    private val userServiceArgs =
        Shizuku.UserServiceArgs(
            ComponentName(
                BuildConfig.APPLICATION_ID,
                UserService::class.java.name,
            ),
        ).daemon(false)
            .processNameSuffix("service")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)

    private val onRequestPermissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    Logger.i(TAG, "Shizuku permission granted")
                    Toast.makeText(this, R.string.toast_shizuku_permission_granted, Toast.LENGTH_SHORT).show()
                    bindUserService()
                } else {
                    Logger.w(TAG, "Shizuku permission denied")
                    Toast.makeText(this, R.string.toast_shizuku_permission_denied, Toast.LENGTH_SHORT).show()
                    viewModel.updateShizukuStatus(ShizukuStatus.NO_PERMISSION)
                }
            }
        }

    private val onBinderReceivedListener =
        Shizuku.OnBinderReceivedListener {
            Logger.i(TAG, "Shizuku binder received")
            checkShizukuPermission()
        }

    private val onBinderDeadListener =
        Shizuku.OnBinderDeadListener {
            Logger.w(TAG, "Shizuku binder dead")
            viewModel.updateShizukuStatus(ShizukuStatus.NOT_RUNNING)
            unbindUserService()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Logger.d(TAG, "MainActivity created")

        setupNavigation()
        setupEdgeToEdge()
        setupShizuku()
        observeEvents()
        checkOverlayPermission()

        // Handle incoming Intent for backward compatibility
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /**
     * Handles incoming Intents and routes to the appropriate Fragment.
     *
     * Supports backward compatibility with existing deep links and Intents
     * that previously targeted HistoryActivity or SettingsActivity.
     *
     * @param intent The incoming Intent to handle
     *
     * _Requirements: 6.5_
     */
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        val action = intent.action
        val targetFragment = intent.getStringExtra(EXTRA_TARGET_FRAGMENT)

        Logger.d(TAG, "Handling intent: action=$action, targetFragment=$targetFragment")

        // Determine target destination based on action or extra
        val destinationId =
            when {
                action == ACTION_NAVIGATE_SETTINGS -> R.id.settingsFragment
                action == ACTION_NAVIGATE_HISTORY -> R.id.historyFragment
                targetFragment == TARGET_SETTINGS -> R.id.settingsFragment
                targetFragment == TARGET_HISTORY -> R.id.historyFragment
                targetFragment == TARGET_TASK -> R.id.taskFragment
                else -> null
            }

        // Navigate to the target destination if specified
        destinationId?.let { destId ->
            // Post navigation to ensure NavController is ready
            bottomNav.post {
                try {
                    if (navController.currentDestination?.id != destId) {
                        navController.navigate(destId)
                        Logger.d(TAG, "Navigated to destination: $destId")
                    }
                } catch (e: Exception) {
                    Logger.e(TAG, "Failed to navigate to destination: $destId", e)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkOverlayPermission()
    }

    override fun onDestroy() {
        super.onDestroy()
        Logger.d(TAG, "MainActivity destroyed")

        Shizuku.removeRequestPermissionResultListener(onRequestPermissionResultListener)
        Shizuku.removeBinderReceivedListener(onBinderReceivedListener)
        Shizuku.removeBinderDeadListener(onBinderDeadListener)

        unbindUserService()
    }

    /**
     * Sets up the Navigation component with BottomNavigationView.
     *
     * Configures state preservation to ensure Fragment state is maintained
     * when switching between tabs.
     *
     * _Requirements: 1.4, 5.1_
     */
    private fun setupNavigation() {
        val navHostFragment =
            supportFragmentManager
                .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        bottomNav = findViewById(R.id.bottom_nav)

        // Configure BottomNavigationView to save and restore Fragment state
        // This ensures that when switching tabs, the previous Fragment's state is preserved
        bottomNav.setupWithNavController(navController)

        // Set up item selection listener to handle state preservation
        bottomNav.setOnItemSelectedListener { item ->
            // Use NavigationUI to handle navigation with state saving
            val builder =
                androidx.navigation.NavOptions.Builder()
                    .setLaunchSingleTop(true)
                    .setRestoreState(true)
                    .setPopUpTo(
                        navController.graph.startDestinationId,
                        inclusive = false,
                        saveState = true,
                    )

            val options = builder.build()

            try {
                navController.navigate(item.itemId, null, options)
                true
            } catch (e: IllegalArgumentException) {
                Logger.w(TAG, "Navigation failed for item: ${item.itemId}", e)
                false
            }
        }

        // Sync the selected item with the current destination
        navController.addOnDestinationChangedListener { _, destination, _ ->
            bottomNav.menu.findItem(destination.id)?.isChecked = true
        }

        Logger.d(TAG, "Navigation setup complete with state preservation")
    }

    /**
     * Sets up edge-to-edge display with proper insets handling.
     *
     * Applies padding to the root layout for status bar and
     * to the bottom navigation for navigation bar.
     */
    private fun setupEdgeToEdge() {
        val rootLayout = findViewById<androidx.coordinatorlayout.widget.CoordinatorLayout>(R.id.rootLayout)
        setupEdgeToEdgeInsets(rootLayout, applyTop = true, applyBottom = false)
        setupEdgeToEdgeInsets(bottomNav, applyTop = false, applyBottom = true)
    }

    /**
     * Sets up Shizuku listeners and checks initial state.
     */
    private fun setupShizuku() {
        Shizuku.addRequestPermissionResultListener(onRequestPermissionResultListener)
        Shizuku.addBinderReceivedListener(onBinderReceivedListener)
        Shizuku.addBinderDeadListener(onBinderDeadListener)

        // Check initial Shizuku state
        if (Shizuku.pingBinder()) {
            checkShizukuPermission()
        } else {
            viewModel.updateShizukuStatus(ShizukuStatus.NOT_RUNNING)
        }
    }

    /**
     * Checks Shizuku permission and binds service if granted.
     */
    private fun checkShizukuPermission() {
        try {
            if (Shizuku.isPreV11()) {
                Logger.w(TAG, "Shizuku version too low")
                Toast.makeText(this, R.string.toast_shizuku_version_low, Toast.LENGTH_SHORT).show()
                viewModel.updateShizukuStatus(ShizukuStatus.NOT_RUNNING)
                return
            }

            when {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> {
                    Logger.i(TAG, "Shizuku permission already granted")
                    bindUserService()
                }

                Shizuku.shouldShowRequestPermissionRationale() -> {
                    Logger.i(TAG, "Should show Shizuku permission rationale")
                    viewModel.updateShizukuStatus(ShizukuStatus.NO_PERMISSION)
                }

                else -> {
                    Logger.i(TAG, "Requesting Shizuku permission")
                    viewModel.updateShizukuStatus(ShizukuStatus.NO_PERMISSION)
                    Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error checking Shizuku permission", e)
            viewModel.updateShizukuStatus(ShizukuStatus.NOT_RUNNING)
        }
    }

    /**
     * Binds to the UserService via Shizuku.
     */
    private fun bindUserService() {
        if (isServiceBound) {
            Logger.d(TAG, "UserService already bound")
            return
        }

        try {
            viewModel.updateShizukuStatus(ShizukuStatus.CONNECTING)
            Shizuku.bindUserService(userServiceArgs, userServiceConnection)
            isServiceBound = true
            Logger.i(TAG, "UserService bind requested")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to bind UserService", e)
            viewModel.updateShizukuStatus(ShizukuStatus.NOT_RUNNING)
        }
    }

    /**
     * Unbinds from the UserService.
     */
    private fun unbindUserService() {
        if (!isServiceBound) return

        try {
            Shizuku.unbindUserService(userServiceArgs, userServiceConnection, true)
            isServiceBound = false
            Logger.i(TAG, "UserService unbound")
        } catch (e: Exception) {
            Logger.e(TAG, "Error unbinding UserService", e)
        }
    }

    /**
     * Checks and updates overlay permission status.
     */
    private fun checkOverlayPermission() {
        val hasPermission = Settings.canDrawOverlays(this)
        viewModel.updateOverlayPermission(hasPermission)
    }

    /**
     * Observes ViewModel events and handles them.
     */
    private fun observeEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    handleEvent(event)
                }
            }
        }
    }

    /**
     * Handles UI events from the ViewModel.
     */
    private fun handleEvent(event: MainUiEvent) {
        when (event) {
            is MainUiEvent.ShowToast -> {
                Toast.makeText(this, event.messageResId, Toast.LENGTH_SHORT).show()
            }

            is MainUiEvent.ShowToastText -> {
                Toast.makeText(this, event.message, Toast.LENGTH_SHORT).show()
            }

            is MainUiEvent.TaskCompleted -> {
                Logger.i(TAG, "Task completed: ${event.message}")
            }

            is MainUiEvent.TaskFailed -> {
                Logger.w(TAG, "Task failed: ${event.error}")
            }

            is MainUiEvent.MinimizeApp -> {
                moveTaskToBack(true)
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val SHIZUKU_PERMISSION_REQUEST_CODE = 1001

        // Intent actions for backward compatibility
        const val ACTION_NAVIGATE_SETTINGS = "com.kevinluo.autoglm.NAVIGATE_SETTINGS"
        const val ACTION_NAVIGATE_HISTORY = "com.kevinluo.autoglm.NAVIGATE_HISTORY"

        // Extra key for specifying target fragment
        const val EXTRA_TARGET_FRAGMENT = "target_fragment"

        // Target fragment values
        const val TARGET_TASK = "task"
        const val TARGET_HISTORY = "history"
        const val TARGET_SETTINGS = "settings"
    }
}
