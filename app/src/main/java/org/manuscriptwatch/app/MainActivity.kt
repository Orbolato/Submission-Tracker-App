package org.manuscriptwatch.app

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    private val ink = Color.rgb(20, 38, 63)
    private val navy = Color.rgb(18, 63, 105)
    private val teal = Color.rgb(13, 124, 132)
    private val light = Color.rgb(245, 249, 252)
    private val gray = Color.rgb(91, 107, 122)
    private var opened = false
    private var dashboardScroll: ScrollView? = null

    /**
     * A check is asynchronous: observe precisely the work we started rather
     * than redrawing immediately or guessing how long publisher pages take.
     */
    private fun checkAndRefresh(
        manuscriptId: String? = null,
        sourceUrl: String? = null,
        onComplete: (() -> Unit)? = null
    ) {
        val workId = MonitorScheduler.checkNow(this, manuscriptId, sourceUrl)
        val liveData = WorkManager.getInstance(this).getWorkInfoByIdLiveData(workId)
        val observer = object : Observer<WorkInfo> {
            override fun onChanged(info: WorkInfo?) {
                if (info == null || !info.state.isFinished) return
                liveData.removeObserver(this)
                if (isFinishing || isDestroyed) return
                // A completed worker may have saved a status, reviewer count,
                // source-specific error, or simply a failed login.
                draw()
                onComplete?.invoke()
                if (info.state != WorkInfo.State.SUCCEEDED) {
                    Toast.makeText(this@MainActivity,
                        "Website check did not complete. Try again when connected.",
                        Toast.LENGTH_LONG).show()
                }
            }
        }
        liveData.observe(this, observer)
    }

    private val backupDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            val json = ManuscriptBackup.export(this)
            contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                stream.write(json.toByteArray(Charsets.UTF_8))
                stream.flush()
            } ?: error("Could not open the selected backup destination.")
        }.onSuccess {
            Toast.makeText(this, "Manuscript backup saved. Protect the file: tracking links are private.",
                Toast.LENGTH_LONG).show()
        }.onFailure {
            AlertDialog.Builder(this).setTitle("Backup failed")
                .setMessage(it.message ?: "Could not write the selected file.")
                .setPositiveButton("Close", null).show()
        }
    }

    private val restoreDocument = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            val stream = contentResolver.openInputStream(uri)
                ?: error("Could not read the selected backup file.")
            val contents = stream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8_192)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    if (output.size() + read > ManuscriptBackup.MAX_BYTES) {
                        throw IllegalArgumentException("Backup exceeds the 2 MB safety limit.")
                    }
                    output.write(buffer, 0, read)
                }
                output.toString(Charsets.UTF_8.name())
            }
            ManuscriptBackup.decode(contents)
        }.onSuccess { snapshot ->
            val existing = SubmissionStore.all(this).size
            AlertDialog.Builder(this)
                .setTitle("Replace local manuscript records?")
                .setMessage("The selected backup contains ${snapshot.manuscriptCount} manuscript(s). " +
                    "Restoring replaces all $existing manuscript(s) currently stored on this device, " +
                    "including local statuses and histories. This cannot be undone unless you " +
                    "have another backup.\n\nORCID credentials and journal login sessions " +
                    "are not in the backup and will not be restored. " +
                    "Make sure you trust the source of this JSON file.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Restore records") { _, _ ->
                    runCatching {
                        check(ManuscriptBackup.restore(this, snapshot)) {
                            "Android could not save the restored records."
                        }
                        MonitorScheduler.schedule(this)
                    }.onSuccess {
                        draw()
                        Toast.makeText(this, "Manuscript records restored successfully.",
                            Toast.LENGTH_LONG).show()
                    }.onFailure {
                        AlertDialog.Builder(this).setTitle("Restore failed")
                            .setMessage(it.message ?: "Local records were not restored.")
                            .setPositiveButton("Close", null).show()
                    }
                }.show()
        }.onFailure {
            AlertDialog.Builder(this).setTitle("Invalid backup")
                .setMessage(it.message ?: "This file could not be read as a valid backup.")
                .setPositiveButton("Close", null).show()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MonitorScheduler.schedule(this)
        opened = true
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 102)
        }
        draw()
    }

    override fun onResume() {
        super.onResume()
        if (opened) draw()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun text(value: String, size: Float = 14f, bold: Boolean = false, color: Int = ink): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
        }

    private fun pillButton(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 12f
        isAllCaps = false
        setTextColor(navy)
        setOnClickListener { action() }
        minHeight = 0
        minimumHeight = 0
    }

    private fun rounded(bg: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(bg)
        cornerRadius = dp(14).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(Color.WHITE, Color.rgb(227, 234, 241))
        setPadding(dp(16), dp(14), dp(16), dp(14))
        elevation = dp(1).toFloat()
    }

    private fun Line(text: TextView, target: LinearLayout, top: Int = 0) {
        target.addView(text, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) })
    }

    private fun draw() {
        val previousScrollY = dashboardScroll?.scrollY ?: 0
        val items = SubmissionStore.all(this).sortedWith(compareBy<Manuscript> { it.journal }.thenBy { it.title })
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(light)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(28), dp(16), dp(36))
        }
        scroll.addView(container)
        setContentView(scroll)
        dashboardScroll = scroll
        scroll.post { scroll.scrollTo(0, previousScrollY) }
        keepContentInsideSystemBars(scroll)

        Line(text("Manuscript Watch", 28f, bold = true, color = navy), container)
        Line(text("Track your manuscripts directly on journal websites", 13f, color = gray), container, 4)
        val overview = card()
        val active = items.count { !it.currentStatus.contains("accept", true) && !it.currentStatus.contains("reject", true) }
        Line(text("${items.size} manuscripts  •  $active not marked final", 16f, bold = true), overview)
        Line(text("Background check: every ${SubmissionStore.intervalLabel(SubmissionStore.intervalMinutes(this))} (best effort; Android may delay)", 12f, color = gray), overview, 6)
        Line(text("To connect a manuscript, open its journal, sign in, and select the page showing its status. No HTML settings needed.", 12f, color = gray), overview, 4)
        container.addView(overview, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(pillButton("+ Add paper") { editManuscript(null) }, LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(pillButton("Check now") {
            checkAndRefresh()
            Toast.makeText(this, "Checking manuscripts. Results update automatically.", Toast.LENGTH_LONG).show()
        }, LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(pillButton("Refresh") { draw() }, LinearLayout.LayoutParams(0, -2, 0.75f))
        container.addView(actions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        val options = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        options.addView(pillButton("Website check interval") { chooseInterval() },
            LinearLayout.LayoutParams(-1, -2))
        container.addView(options)
        val vaultRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        vaultRow.addView(pillButton(if (SecureOrcidCredentials.isSaved(this))
            "ORCID vault: configured" else "ORCID vault (optional)") { showOrcidVault() },
            LinearLayout.LayoutParams(-1, -2))
        container.addView(vaultRow)

        val backupRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        backupRow.addView(pillButton("Backup records") {
            AlertDialog.Builder(this)
                .setTitle("Export private manuscript data?")
                .setMessage("Create a JSON backup of manuscripts, tracking links, status and " +
                    "peer-review histories, and the check interval. This backup includes " +
                    "private manuscript identifiers and tracking URLs; store it securely. " +
                    "ORCID passwords and publisher login sessions are not included.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Choose destination") { _, _ ->
                    val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                        .format(Date())
                    backupDocument.launch("manuscript-watch-backup-$date.json")
                }.show()
        }, LinearLayout.LayoutParams(0, -2, 1f))
        backupRow.addView(pillButton("Restore records") {
            restoreDocument.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        container.addView(backupRow)
        Line(text("Backups contain sensitive manuscript links. Keep exported JSON files private.",
            11f, color = gray), container, 2)
        Line(text("Tracked manuscripts", 18f, bold = true), container, 20)
        if (items.isEmpty()) {
            val empty = card()
            Line(text("No manuscripts yet", 17f, true), empty)
            Line(text("Add a paper, open its journal, sign in and select the page showing its status. You do not need any technical setup.", 13f, color = gray), empty, 8)
            container.addView(empty, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        }
        items.forEach { item ->
            val c = card()
            Line(text(item.journal.uppercase(), 10f, bold = true, color = teal), c)
            Line(text(item.title, 16f, bold = true), c, 5)
            Line(text(item.manuscriptId, 12f, color = gray), c, 3)
            Line(text(item.currentStatus, 17f, bold = true, color = statusColor(item.currentStatus)), c, 12)
            val count = TrackingSources.effective(item).size
            val problems = TrackingSources.effective(item).count { it.lastError.isNotBlank() }
            Line(text("$count tracking source${if (count == 1) "" else "s"}" +
                (if (problems > 0) " · $problems need attention" else "") +
                (if (item.currentFolder.isNotBlank()) " · Folder: ${item.currentFolder}" else ""),
                11f, color = if (problems > 0) Color.rgb(160, 77, 34) else gray), c, 4)
            if (!item.reviewProgress.isEmpty()) {
                val r = item.reviewProgress
                val details = listOfNotNull(r.invitedLabel()?.let { "Invited: $it" },
                    r.accepted?.let { "Accepted: $it" }, "Completed: ${r.completedLabel()}")
                Line(text("Peer review · ${details.joinToString("  |  ")}", 12f, color = teal), c, 7)
            }
            val checked = if (item.lastChecked == 0L) "Not checked yet" else
                "Last observation: ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(item.lastChecked))}"
            Line(text(checked, 11f, color = gray), c, 3)
            if (item.lastError.isNotBlank()) Line(text("Attention: ${item.lastError}", 12f, color = Color.rgb(160, 77, 34)), c, 8)
            val topButtons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            topButtons.addView(pillButton("Open journal") { openSite(item) }, LinearLayout.LayoutParams(0, -2, 1f))
            topButtons.addView(pillButton("Check") {
                checkAndRefresh(item.id)
                Toast.makeText(this, "Checking manuscript. Results update automatically.", Toast.LENGTH_SHORT).show()
            }, LinearLayout.LayoutParams(0, -2, 1f))
            topButtons.addView(pillButton("History") { history(item) }, LinearLayout.LayoutParams(0, -2, 1f))
            c.addView(topButtons, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(9) })
            val bottomButtons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            bottomButtons.addView(pillButton("Tracking links") { manageTrackingLinks(item) }, LinearLayout.LayoutParams(0, -2, 1f))
            bottomButtons.addView(pillButton("Edit") { editManuscript(item) }, LinearLayout.LayoutParams(0, -2, 1f))
            bottomButtons.addView(pillButton("Remove") { confirmRemove(item) }, LinearLayout.LayoutParams(0, -2, 1f))
            c.addView(bottomButtons)
            container.addView(c, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(11) })
        }
        Line(text("Website monitoring depends on each publisher’s page structure, access rules, and session lifetime. Unsupported sites require a custom connector. Alerts come only from website status changes.", 12f, color = gray), container, 18)
    }

    private fun showOrcidVault() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(8))
        }
        val label = text("Optional: store credentials encrypted with Android Keystore. " +
            "They remain on this device. Use 'Fill saved ORCID' on an official ORCID page; " +
            "this does NOT enable unattended background sign-in or bypass 2FA.", 13f)
        content.addView(label)
        val username = EditText(this).apply {
            hint = "ORCID email / ORCID iD"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        val password = EditText(this).apply {
            hint = "ORCID password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        content.addView(username)
        content.addView(password)
        val builder = AlertDialog.Builder(this).setTitle("Encrypted ORCID credentials")
            .setView(content)
            .setPositiveButton("Save encrypted", null)
            .setNegativeButton("Cancel", null)
        if (SecureOrcidCredentials.isSaved(this)) {
            builder.setNeutralButton("Delete saved login", null)
        }
        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val u = username.text.toString().trim()
                val pw = password.text.toString()
                if (u.isEmpty() || pw.isEmpty()) {
                    Toast.makeText(this, "Enter both fields to opt in.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                try {
                    SecureOrcidCredentials.save(this, u, pw)
                    password.text.clear()
                    dialog.dismiss()
                    draw()
                    Toast.makeText(this, "Encrypted ORCID credentials saved locally.", Toast.LENGTH_LONG).show()
                } catch (_: Exception) {
                    Toast.makeText(this, "Secure storage unavailable; credentials not saved.", Toast.LENGTH_LONG).show()
                }
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                SecureOrcidCredentials.delete(this)
                password.text.clear()
                dialog.dismiss()
                draw()
            }
        }
        dialog.show()
    }

    private fun statusColor(status: String): Int = when {
        status.contains("accept", true) -> teal
        status.contains("reject", true) -> Color.rgb(146, 63, 56)
        status.contains("revise", true) || status.contains("revision", true) -> Color.rgb(147, 93, 18)
        else -> navy
    }

    private fun chooseInterval() {
        val presets = longArrayOf(15, 30, 60, 180, 360, 720, 1440)
        val choices = presets.map { SubmissionStore.intervalLabel(it) } + "Custom interval (minutes)…"
        val current = SubmissionStore.intervalMinutes(this)
        AlertDialog.Builder(this).setTitle("Background check interval")
            .setSingleChoiceItems(choices.toTypedArray(), presets.indexOf(current)) { dialog, which ->
                dialog.dismiss()
                if (which == presets.size) {
                    chooseCustomInterval(current)
                } else {
                    SubmissionStore.setIntervalMinutes(this, presets[which])
                    MonitorScheduler.schedule(this)
                    draw()
                }
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun chooseCustomInterval(current: Long) {
        val field = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            setText(current.toString())
            selectAll()
            hint = "Minutes (15–10080)"
        }
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
            addView(text("Choose 15 to 10080 minutes (7 days). " +
                "Android may delay periodic checks, particularly with battery optimization.", 12f, color = gray))
            addView(field)
        }
        val dialog = AlertDialog.Builder(this).setTitle("Custom check interval")
            .setView(wrapper)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val minutes = field.text.toString().trim().toLongOrNull()
                if (minutes == null ||
                    minutes !in SubmissionStore.MIN_INTERVAL_MINUTES..SubmissionStore.MAX_INTERVAL_MINUTES) {
                    field.error = "Enter a whole number between 15 and 10080."
                    return@setOnClickListener
                }
                SubmissionStore.setIntervalMinutes(this, minutes)
                MonitorScheduler.schedule(this)
                dialog.dismiss()
                draw()
            }
        }
        dialog.show()
    }

    private fun openSite(item: Manuscript) {
        val url = item.journalUrl.ifBlank { item.statusUrl }
        if (!url.startsWith("https://", true)) {
            Toast.makeText(this, "Edit the paper and enter the journal's HTTPS login URL first.", Toast.LENGTH_LONG).show()
            return
        }
        startActivity(Intent(this, BrowserActivity::class.java).apply {
            putExtra("url", url)
            putExtra("manuscriptId", item.id)
        })
    }

    private fun confirmRemove(item: Manuscript) {
        AlertDialog.Builder(this).setTitle("Remove manuscript?")
            .setMessage("This deletes ${item.manuscriptId} and its local history.")
            .setPositiveButton("Remove") { _, _ -> SubmissionStore.delete(this, item.id); draw() }
            .setNegativeButton("Cancel", null).show()
    }

    private fun history(item: Manuscript) {
        val data = SubmissionStore.all(this).find { it.id == item.id } ?: item
        val changes = if (data.history.isEmpty()) "No status changes recorded yet."
        else data.history.reversed().joinToString("\n\n") { event ->
            "${DateFormat.getDateTimeInstance().format(Date(event.at))}\n${event.from} → ${event.to}\nSource: ${event.source}"
        }
        val reviews = data.reviewHistory.reversed().take(30).joinToString("\n") { event ->
            val p = event.progress
            "${DateFormat.getDateTimeInstance().format(Date(event.at))}: " +
                listOfNotNull(p.invitedLabel()?.let { "Invited $it" }, p.accepted?.let { "Accepted $it" },
                    "Completed ${p.completedLabel()}").joinToString(" · ")
        }
        val message = changes + if (reviews.isBlank()) "" else "\n\nPeer-review observations\n$reviews"
        AlertDialog.Builder(this).setTitle("History: ${item.manuscriptId}")
            .setMessage(message).setPositiveButton("Close", null).show()
    }

    private fun manageTrackingLinks(item: Manuscript) {
        val entry = EditText(this).apply {
            hint = "Paste an HTTPS manuscript tracking link"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
            setPadding(dp(18), dp(12), dp(18), dp(12))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
            addView(text("Add an Elsevier Author Hub, article production, Springer, or journal tracking link. " +
                "Your private links stay on this device.", 12f, color = gray))
            addView(entry)
        }
        val refresh = pillButton("Refresh tracking results") { /* wired after the list is defined */ }
        val savedLinks = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(refresh)
        content.addView(text("Saved tracking sources:", 12f, bold = true),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        content.addView(savedLinks)
        var shownDialog: AlertDialog? = null
        // Re-read persisted state every time: a queued WorkManager check may finish while open.
        fun repaint() {
            savedLinks.removeAllViews()
            val updated = SubmissionStore.all(this).find { it.id == item.id } ?: item
            val links = TrackingSources.effective(updated)
            if (links.isEmpty()) savedLinks.addView(text("No saved tracking links.", 12f, color = gray))
            for (source in links) {
                val lastStatus = source.lastStatus.ifBlank { "Not checked" }
                savedLinks.addView(text("${source.kind.title}: $lastStatus" +
                    if (source.folder.isNotBlank()) " (${source.folder})" else "",
                    12f, color = ink))
                val checkTime = source.lastAttempted.takeIf { it != 0L }?.let {
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))
                } ?: "Never"
                savedLinks.addView(text("Last check: $checkTime", 11f, color = gray))
                if (source.lastError.isNotBlank())
                    savedLinks.addView(text("Attention: ${source.lastError}", 11f, color = Color.rgb(160, 77, 34)))
                savedLinks.addView(pillButton("Check ${source.kind.title}") {
                    checkAndRefresh(item.id, source.url) {
                        if (shownDialog?.isShowing == true) repaint()
                    }
                    Toast.makeText(this, "Checking source. Results update automatically.", Toast.LENGTH_SHORT).show()
                })
                savedLinks.addView(pillButton("Open tracking page") {
                    startActivity(Intent(this, BrowserActivity::class.java).apply {
                        putExtra("url", source.url)
                        putExtra("manuscriptId", item.id)
                    })
                })
                savedLinks.addView(pillButton("Remove ${source.kind.title}") {
                    AlertDialog.Builder(this).setTitle("Remove tracking source?")
                        .setMessage("Remove this saved link from Manuscript Watch? This does not affect the journal website.")
                        .setPositiveButton("Remove") { _, _ ->
                            SubmissionStore.removeTrackingSource(this, item.id, source.url)
                            repaint()
                            // Rebuild the dashboard behind this dialog as well.
                            draw()
                        }.setNegativeButton("Cancel", null).show()
                })
            }
        }
        refresh.setOnClickListener { repaint(); draw() }
        val scroll = ScrollView(this).apply { addView(content) }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Tracking links for ${item.manuscriptId}")
            .setView(scroll)
            .setPositiveButton("Add link", null)
            .setNegativeButton("Close", null).create()
        shownDialog = dialog
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val url = entry.text.toString().trim()
                if (!TrackingSources.isSupportedUrl(url)) {
                    Toast.makeText(this, "Enter a valid HTTPS tracking page, not a login redirect.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                SubmissionStore.addTrackingSource(this, item.id, url)
                entry.text.clear()
                repaint()
                draw()
            }
        }
        repaint()
        dialog.show()
    }

    private fun editManuscript(existing: Manuscript?) {
        val wrapper = ScrollView(this)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(8))
        }
        wrapper.addView(body)
        fun field(label: String, value: String, inputType: Int = InputType.TYPE_CLASS_TEXT): EditText {
            body.addView(text(label, 12f, bold = true, color = gray), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
            return EditText(this).apply {
                setText(value)
                setSingleLine(true)
                textSize = 14f
                this.inputType = inputType
                body.addView(this, LinearLayout.LayoutParams(-1, dp(44)))
            }
        }
        val title = field("Manuscript title *", existing?.title ?: "")
        val journal = field("Journal *", existing?.journal ?: "")
        val manuscriptId = field("Manuscript ID *", existing?.manuscriptId ?: "")
        val currentStatus = field("Current status (optional)", existing?.currentStatus?.takeUnless { it == "Not checked" } ?: "")
        val journalUrl = field("Journal sign-in page (HTTPS)", existing?.journalUrl ?: "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        val statusUrl = field("Tracking page (optional)", existing?.statusUrl ?: "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        val editorialMenu = field("Editorial Manager Author Main Menu (optional)",
            existing?.editorialMenuUrl ?: "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        Line(text("No technical setup needed: save this paper, tap Open journal, sign in, " +
            "open the page where its current status appears, then tap Track page. " +
            "The tracking link is saved for you automatically. " +
            "You may also paste a direct HTTPS link above if you have one.", 12f, color = gray), body, 12)
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Add manuscript" else "Edit manuscript")
            .setView(wrapper)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val t = title.text.toString().trim()
                val j = journal.text.toString().trim()
                val m = manuscriptId.text.toString().trim()
                val signIn = journalUrl.text.toString().trim()
                val statusPage = statusUrl.text.toString().trim()
                val menuPage = editorialMenu.text.toString().trim()
                if (t.isBlank() || j.isBlank() || m.isBlank()) {
                    Toast.makeText(this, "Title, journal and manuscript ID are required.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                if (listOf(signIn, statusPage, menuPage).any { it.isNotBlank() && !it.startsWith("https://", true) }) {
                    Toast.makeText(this, "Please use HTTPS URLs.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                if (statusPage.isNotBlank() && !JournalPageClassifier.isSaveableStatusPage(statusPage)) {
                    Toast.makeText(this, "This is a login redirect or notification site, not a manuscript status page.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                if (menuPage.isNotBlank() && !EditorialManagerDiscovery.isEditorialManager(menuPage)) {
                    Toast.makeText(this, "Use the Editorial Manager Author Main Menu URL, or leave it blank.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val saved = (existing ?: Manuscript(title = t, journal = j, manuscriptId = m)).copy(
                    title = t, journal = j, manuscriptId = m,
                    journalUrl = signIn, statusUrl = JournalPageClassifier.canonicalStatusUrl(statusPage),
                    editorialMenuUrl = menuPage,
                    cssSelector = existing?.cssSelector.orEmpty() // Preserve legacy custom rules; new manuscripts use automatic detection.
                )
                SubmissionStore.save(this, saved)
                if (statusPage.isNotBlank()) SubmissionStore.addTrackingSource(this, saved.id, statusPage)
                val initial = currentStatus.text.toString().trim()
                if (initial.isNotBlank() && initial != saved.currentStatus) {
                    SubmissionStore.observe(this, saved.id, initial, "Manual entry")
                }
                dialog.dismiss()
                draw()
            }
        }
        dialog.show()
    }
}
