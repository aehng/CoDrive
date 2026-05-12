package com.codrive.ai.bootstrap

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.codrive.ai.R
import com.codrive.ai.ChatActivity
import com.codrive.ai.MainActivity
import com.codrive.ai.SettingsActivity
import com.codrive.ai.modeldownload.DownloadState
import com.codrive.ai.modeldownload.ModelDownloadScheduler
import com.codrive.ai.modeldownload.ModelStorage
import com.codrive.ai.models.ModelAsset
import com.codrive.ai.settings.VoiceSettingsStore
import java.io.File
import kotlinx.coroutines.launch

class ModelBootstrapActivity : AppCompatActivity() {
    private lateinit var viewModel: ModelBootstrapViewModel
    private lateinit var modelRows: Map<String, LinearLayout>
    private lateinit var scheduler: ModelDownloadScheduler
    private lateinit var wifiOnlySwitch: Switch
    private lateinit var chargingOnlySwitch: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_model_bootstrap)

        val storageRoot = File(noBackupFilesDir, "models")
        val storage = ModelStorage(storageRoot)
        scheduler = ModelDownloadScheduler(this)

        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ModelBootstrapViewModel(storage, scheduler) as T
            }
        }).get(ModelBootstrapViewModel::class.java)

        val statusText = findViewById<TextView>(R.id.bootstrapStatus)
        val backButton = findViewById<ImageButton>(R.id.bootstrapBackButton)
        val menuButton = findViewById<ImageButton>(R.id.bootstrapMenuButton)
        val downloadAllButton = findViewById<Button>(R.id.bootstrapDownloadAllButton)
        val useSherpaSwitch = findViewById<Switch>(R.id.bootstrapUseSherpaSwitch)
        val sherpaHint = findViewById<TextView>(R.id.bootstrapSherpaHint)
        wifiOnlySwitch = findViewById(R.id.bootstrapWifiOnlySwitch)
        chargingOnlySwitch = findViewById(R.id.bootstrapChargingOnlySwitch)
        val listContainer = findViewById<LinearLayout>(R.id.bootstrapModelList)

        backButton.setOnClickListener { finish() }
        menuButton.setOnClickListener { showNavigationMenu(it) }

        val voiceSettings = VoiceSettingsStore.create(this)
        useSherpaSwitch.isChecked = voiceSettings.isSherpaEnabled()
        useSherpaSwitch.setOnCheckedChangeListener { _, isChecked ->
            voiceSettings.setSherpaEnabled(isChecked)
        }

        downloadAllButton.setOnClickListener {
            viewModel.startDownloadAll(wifiOnlySwitch.isChecked, chargingOnlySwitch.isChecked)
        }

        modelRows = inflateModelRows(listContainer, viewModel.assets)

        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                launch {
                    viewModel.states.collect { states ->
                        updateRows(states)
                    }
                }
                launch {
                    viewModel.isDownloading.collect { downloading ->
                        downloadAllButton.isEnabled = !downloading
                        statusText.setText(
                            if (downloading) R.string.bootstrap_status_downloading
                            else R.string.bootstrap_status_idle
                        )
                    }
                }
                launch {
                    viewModel.allModelsReady.collect { ready ->
                        if (ready) {
                            statusText.setText(R.string.bootstrap_status_done)
                        }
                        useSherpaSwitch.isEnabled = ready
                        sherpaHint.visibility = if (ready) View.GONE else View.VISIBLE
                        if (!ready) {
                            useSherpaSwitch.isChecked = false
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun inflateModelRows(container: LinearLayout, assets: List<ModelAsset>): Map<String, LinearLayout> {
        val inflater = LayoutInflater.from(container.context)
        val rows = mutableMapOf<String, LinearLayout>()
        for (asset in assets) {
            val row = inflater.inflate(R.layout.item_model_asset, container, false) as LinearLayout
            row.findViewById<TextView>(R.id.modelItemName).text = asset.fileName
            row.findViewById<TextView>(R.id.modelItemStatus).setText(R.string.bootstrap_model_pending)
            row.findViewById<ProgressBar>(R.id.modelItemProgress).progress = 0
            row.setOnClickListener {
                viewModel.startDownload(asset, wifiOnlySwitch.isChecked, chargingOnlySwitch.isChecked)
            }
            container.addView(row)
            rows[asset.fileName] = row
        }
        return rows
    }

    private fun updateRows(states: Map<String, DownloadState>) {
        for ((fileName, state) in states) {
            val row = modelRows[fileName] ?: continue
            val statusView = row.findViewById<TextView>(R.id.modelItemStatus)
            val progressBar = row.findViewById<ProgressBar>(R.id.modelItemProgress)
            when (state) {
                is DownloadState.Idle -> {
                    statusView.setText(R.string.bootstrap_model_pending)
                    progressBar.progress = 0
                }
                is DownloadState.Downloading -> {
                    statusView.text = getString(R.string.bootstrap_model_downloading_format, state.progress)
                    progressBar.progress = state.progress
                }
                is DownloadState.Verifying -> {
                    statusView.setText(R.string.bootstrap_model_verifying)
                    progressBar.progress = 100
                }
                is DownloadState.Success -> {
                    statusView.setText(R.string.bootstrap_model_ready)
                    progressBar.progress = 100
                }
                is DownloadState.Error -> {
                    statusView.setText(R.string.bootstrap_model_error)
                    progressBar.progress = 0
                }
            }
        }
    }

    private fun showNavigationMenu(anchor: View) {
        val popupMenu = PopupMenu(this, anchor)
        popupMenu.menuInflater.inflate(R.menu.chat_navigation_menu, popupMenu.menu)
        popupMenu.setOnMenuItemClickListener(this::onNavigationItemClicked)
        popupMenu.show()
    }

    private fun onNavigationItemClicked(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_chat_home -> {
                startActivity(Intent(this, MainActivity::class.java))
                true
            }
            R.id.menu_chat_chat -> {
                startActivity(Intent(this, ChatActivity::class.java))
                true
            }
            R.id.menu_chat_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> false
        }
    }
}




