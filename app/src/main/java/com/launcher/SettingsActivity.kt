package com.simplylauncher

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var transparencyLabel: TextView
    private lateinit var transparencySeekBar: SeekBar
    private lateinit var columnsChipGroup: ChipGroup
    private lateinit var rowsChipGroup: ChipGroup
    private lateinit var colorPalette: LinearLayout

    private val paletteColors = listOf(
        Color.WHITE,
        Color.BLACK,
        Color.rgb(30, 136, 229),
        Color.rgb(67, 160, 71),
        Color.rgb(229, 57, 53)
    )

    private var selectedColor: Int = Color.BLACK

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        transparencyLabel = findViewById(R.id.transparencyLabel)
        transparencySeekBar = findViewById(R.id.transparencySeekBar)
        columnsChipGroup = findViewById(R.id.columnsChipGroup)
        rowsChipGroup = findViewById(R.id.rowsChipGroup)
        colorPalette = findViewById(R.id.colorPalette)

        loadCurrentSettings()
        setupListeners()
    }

    private fun loadCurrentSettings() {
        lifecycleScope.launch {
            val color = Prefs.colorFlow(this@SettingsActivity).first()
            val alpha = Prefs.alphaFlow(this@SettingsActivity).first()
            val cols = Prefs.colsFlow(this@SettingsActivity).first()
            val rows = Prefs.rowsFlow(this@SettingsActivity).first()

            selectedColor = color
            setupPalette()

            transparencySeekBar.progress = (alpha * 100).toInt()
            transparencyLabel.text = getString(R.string.settings_transparency, (alpha * 100).toInt())

            when (cols) {
                3 -> columnsChipGroup.check(R.id.chipCol3)
                4 -> columnsChipGroup.check(R.id.chipCol4)
            }

            when (rows) {
                5 -> rowsChipGroup.check(R.id.chipRow5)
                6 -> rowsChipGroup.check(R.id.chipRow6)
                7 -> rowsChipGroup.check(R.id.chipRow7)
            }
        }
    }

    private fun setupPalette() {
        colorPalette.removeAllViews()

        val size = (44 * resources.displayMetrics.density).toInt()
        val margin = (12 * resources.displayMetrics.density).toInt()

        paletteColors.forEach { color ->
            val container = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = margin
                }
            }

            // Cerchio colorato
            val circle = View(this).apply {
                layoutParams = FrameLayout.LayoutParams(size, size).apply {
                    gravity = Gravity.CENTER
                }
                val drawable = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                }
                background = drawable
            }

            // Spunta centrata SOPRA il cerchio
            val check = TextView(this).apply {
                text = "✓"
                textSize = 20f
                gravity = Gravity.CENTER
                // Nera sul bianco, bianca sugli altri
                setTextColor(if (color == Color.WHITE) Color.BLACK else Color.WHITE)
                visibility = if (color == selectedColor) View.VISIBLE else View.GONE
                layoutParams = FrameLayout.LayoutParams(size, size).apply {
                    gravity = Gravity.CENTER
                }
            }

            container.addView(circle)
            container.addView(check)

            container.setOnClickListener {
                selectedColor = color
                lifecycleScope.launch {
                    Prefs.setColor(this@SettingsActivity, color)
                }
                setupPalette()
            }

            colorPalette.addView(container)
        }
    }

    private fun setupListeners() {
        transparencySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val alpha = progress / 100f
                    transparencyLabel.text = getString(R.string.settings_transparency, progress)
                    lifecycleScope.launch {
                        Prefs.setAlpha(this@SettingsActivity, alpha)
                    }
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        columnsChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val cols = when (checkedIds.firstOrNull()) {
                R.id.chipCol3 -> 3
                R.id.chipCol4 -> 4
                else -> return@setOnCheckedStateChangeListener
            }
            lifecycleScope.launch {
                Prefs.setCols(this@SettingsActivity, cols)
            }
        }

        rowsChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val rows = when (checkedIds.firstOrNull()) {
                R.id.chipRow5 -> 5
                R.id.chipRow6 -> 6
                R.id.chipRow7 -> 7
                else -> return@setOnCheckedStateChangeListener
            }
            lifecycleScope.launch {
                Prefs.setRows(this@SettingsActivity, rows)
            }
        }
    }
}
