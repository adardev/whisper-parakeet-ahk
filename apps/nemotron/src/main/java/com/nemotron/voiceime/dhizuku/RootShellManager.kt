package com.nemotron.voiceime.dhizuku

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter

/** Privileged shell operations supplied exclusively by KernelSU root. */
object RootShellManager {

    data class RunningService(val component: String, val packageName: String, val bound: Boolean)

    private const val TAG = "RootShellManager"
    private var shellProc: Process? = null
    private var shellIn: PrintWriter? = null
    private var shellOut: BufferedReader? = null
    private var shellErr: BufferedReader? = null
    private var shellUsesRoot = false
    private val shellLock = Any()

    private const val ASSISTANT_ROLE = "android.app.role.ASSISTANT"
    private const val GEMINI_PACKAGE = "com.google.android.googlequicksearchbox"
    private const val TOOTSIE_PACKAGE = "com.adarbot.app"

    fun isAvailable(): Boolean = RootAccessManager.hasRootAccess()

    fun hasPermission(): Boolean = RootAccessManager.hasRootAccess()

    /** Package freeze tiles can use KernelSU root when KernelSU is not authorized. */
    fun hasTilePermission(): Boolean = hasPermission()

    private fun execTileCommand(cmd: Array<String>, timeoutMs: Long = 6_000L): String? {
        return execShellFresh(cmd, timeoutMs)
    }

    fun requestPermission(): Boolean {
        return RootAccessManager.hasRootAccess() ||
            RootAccessManager.requestAccess(timeoutMs = 5_000L).success
    }

    /** Runs a fresh argv-safe command as root. */
    fun execShellFresh(cmd: Array<String>, timeoutMs: Long = 6000L): String? {
        if (!RootAccessManager.hasRootAccess()) return null
        val result = RootAccessManager.runRoot(cmd, timeoutMs)
        return if (result.success) result.detail else null
    }

    private fun ensureShell() {
        if (shellProc != null && shellIn != null && shellUsesRoot) return
        closeShell()
        if (RootAccessManager.hasRootAccess()) {
            try {
                val proc = RootAccessManager.openRootShell()
                shellProc = proc
                shellUsesRoot = true
                shellIn = PrintWriter(proc.outputStream, true)
                shellOut = BufferedReader(InputStreamReader(proc.inputStream))
                shellErr = BufferedReader(InputStreamReader(proc.errorStream))
                Log.d(TAG, "persistent KernelSU root shell started")
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to start KernelSU root shell", t)
                closeShell()
            }
            return
        }
    }

    fun execShell(cmd: String): Boolean {
        synchronized(shellLock) {
            ensureShell()
            val input = shellIn ?: return false
            try {
                input.println(cmd)
                input.flush()
                if (input.checkError()) {
                    Log.w(TAG, "shell pipe broken: $cmd")
                    closeShell()
                    return false
                }
                drainOutput()
                return true
            } catch (t: Throwable) {
                Log.w(TAG, "exec failed: $cmd", t)
                closeShell()
                return false
            }
        }
    }

    fun execShellCapture(cmd: String): String? {
        synchronized(shellLock) {
            ensureShell()
            val input = shellIn ?: return null
            val out = shellOut ?: return null
            try {
                val marker = "__NEMO_${System.nanoTime()}__"
                input.println("$cmd; echo $marker")
                input.flush()
                if (input.checkError()) {
                    Log.w(TAG, "capture pipe broken: $cmd")
                    closeShell()
                    return null
                }
                val sb = StringBuilder()
                val deadline = System.currentTimeMillis() + 5000L
                var completed = false
                while (System.currentTimeMillis() < deadline) {
                    if (!out.ready()) {
                        Thread.sleep(10)
                        continue
                    }
                    val line = out.readLine() ?: break
                    if (line.contains(marker)) {
                        completed = true
                        break
                    }
                    sb.appendLine(line)
                }
                if (!completed) {
                    Log.w(TAG, "capture timed out before completion: $cmd")
                    return null
                }
                return sb.toString().trim()
            } catch (t: Throwable) {
                Log.w(TAG, "execCapture failed: $cmd", t)
                closeShell()
                return null
            }
        }
    }

    /** El binder de KernelSU ha muerto: descarta el shell persistente para
     *  no reutilizar un proceso sin vida y que se vuelva a crear limpio. */
    fun onBinderDead() {
        synchronized(shellLock) {
            closeShell()
        }
    }

    private fun drainOutput() {
        try {
            val o = shellOut ?: return
            while (o.ready()) o.readLine()
        } catch (_: Throwable) {}
    }

    private fun exec(cmd: String): Boolean = execShell(cmd)

    private fun closeShell() {
        try { shellProc?.destroy() } catch (_: Throwable) {}
        shellProc = null
        shellIn = null
        shellOut = null
        shellErr = null
        shellUsesRoot = false
    }

    fun pasteText(context: Context, @Suppress("UNUSED_PARAMETER") text: String): Boolean {
        if (!hasPermission()) return false
        return try {
            exec("input keyevent 279")
        } catch (t: Throwable) {
            Log.w(TAG, "KernelSU pasteText failed", t)
            false
        }
    }

    /** Escribe texto a un archivo vía KernelSU. Resetea el shell persistente
     *  (descarta colas viejas atascadas), encola la escritura del base64 en
     *  chunks, y confirma por polling con procesos FRESCOS independientes. */
    fun writeTextFile(path: String, content: String): Boolean {
        if (!hasPermission()) return false
        val expected = content.toByteArray(Charsets.UTF_8).size.toLong()
        synchronized(shellLock) {
            // Resetear: mata cualquier cola pendiente de shells anteriores.
            closeShell()
            ensureShell()
            val input = shellIn ?: return false
            val tmp = "$path.nemo.b64"
            val out = "$path.nemo.tmp"
            try {
                val b64 = android.util.Base64.encodeToString(
                    content.toByteArray(Charsets.UTF_8),
                    android.util.Base64.NO_WRAP
                )
                input.println("mkdir -p '${path.substringBeforeLast("/", "")}'")
                input.println("rm -f '$tmp' '$out' '$path'")
                input.flush()
                b64.chunked(65536).forEach { chunk ->
                    input.println("printf '%s' '$chunk' >> '$tmp'")
                }
                input.println("base64 -d '$tmp' > '$out'")
                input.println("mv -f '$out' '$path'")
                input.println("rm -f '$tmp' '$out'")
                input.flush()
                if (input.checkError()) {
                    Log.w(TAG, "writeTextFile pipe broken: $path")
                    closeShell()
                    return false
                }
            } catch (t: Throwable) {
                Log.w(TAG, "writeTextFile failed: $path", t)
                closeShell()
                return false
            }
        }
        // Polling en proceso fresco independiente (no usa el shell persistente).
        repeat(240) {
            Thread.sleep(500)
            val size = execShellFresh(arrayOf("stat", "-c", "%s", "--", path))?.trim()?.toLongOrNull()
            if (size != null && size == expected) {
                Log.d(TAG, "writeTextFile $path OK (${content.length}B)")
                return true
            }
        }
        Log.w(TAG, "writeTextFile $path: no se confirman los $expected bytes")
        return false
    }

    fun hideApp(packageName: String): Boolean {
        if (!hasTilePermission()) return false
        if (!PACKAGE_NAME.matches(packageName)) return false
        val imeState = rememberEnabledInputMethods(packageName)
        val out = execTileCommand(arrayOf("pm", "hide", "--user", "0", packageName)) ?: return false
        val hidden = out.contains("new hidden state: true", ignoreCase = true) && isAppHidden(packageName)
        if (hidden && imeState != null) {
            Thread.sleep(250L)
            val fallback = execShellFresh(arrayOf("settings", "get", "secure", "default_input_method"))
                ?.trim().orEmpty()
            RootAccessManager.saveHiddenImeState(packageName, imeState.copy(defaultAfterHide = fallback))
        }
        Log.d(TAG, "pm hide $packageName → $out (confirmed=$hidden)")
        return hidden
    }

    fun unhideApp(packageName: String): Boolean {
        if (!hasTilePermission()) return false
        if (!PACKAGE_NAME.matches(packageName)) return false
        val out = execTileCommand(arrayOf("pm", "unhide", "--user", "0", packageName)) ?: return false
        val unhidden = out.contains("new hidden state: false", ignoreCase = true) && !isAppHidden(packageName)
        Log.d(TAG, "pm unhide $packageName → $out (confirmed=$unhidden)")
        return unhidden && restoreInputMethodState(packageName)
    }

    /** Save enabled keyboard components before package hide can remove them from IME Manager. */
    private fun rememberEnabledInputMethods(packageName: String): RootAccessManager.HiddenImeState? {
        val available = execShellFresh(arrayOf(
            "cmd", "package", "query-services", "--brief", "--components", "-a", "android.view.InputMethod"
        )) ?: return null
        val enabled = execShellFresh(arrayOf("ime", "list", "-s")) ?: return null
        val components = available.lineSequence().map { it.trim().removeSuffix(":") }
            .filter { it.substringBefore('/') == packageName && COMPONENT_NAME.matches(it) }
            .filter { component -> enabled.lineSequence().any { it.trim().removeSuffix(":") == component } }
            .toSet()
        if (components.isEmpty()) return null
        val currentDefault = execShellFresh(arrayOf("settings", "get", "secure", "default_input_method"))
            ?.trim().orEmpty()
        val state = RootAccessManager.HiddenImeState(components, currentDefault, "")
        RootAccessManager.saveHiddenImeState(packageName, state)
        return state
    }

    /** Re-enable a keyboard hidden with its app, preserving the user's prior default choice. */
    private fun restoreInputMethodState(packageName: String): Boolean {
        val state = RootAccessManager.getHiddenImeState(packageName) ?: return true
        for (component in state.enabledComponents) {
            if (execShellFresh(arrayOf("ime", "enable", "--user", "0", component)) == null) {
                Log.e(TAG, "Could not re-enable keyboard input method $component")
                return false
            }
        }
        val enabled = execShellFresh(arrayOf("ime", "list", "-s")) ?: return false
        if (state.enabledComponents.any { component ->
                enabled.lineSequence().none { it.trim().removeSuffix(":") == component }
            }) return false

        val currentDefault = execShellFresh(arrayOf("settings", "get", "secure", "default_input_method"))
            ?.trim().orEmpty()
        if (state.defaultBeforeHide in state.enabledComponents &&
            currentDefault == state.defaultAfterHide) {
            if (execShellFresh(arrayOf("ime", "set", "--user", "0", state.defaultBeforeHide)) == null) {
                Log.e(TAG, "Could not restore default keyboard ${state.defaultBeforeHide}")
                return false
            }
            val restoredDefault = execShellFresh(arrayOf("settings", "get", "secure", "default_input_method"))
                ?.trim()
            if (restoredDefault != state.defaultBeforeHide) return false
        }
        RootAccessManager.clearHiddenImeState(packageName)
        return true
    }

    /** Clears ActivityManager's force-stop bit so system broadcasts can wake a package again. */
    fun unstopApp(packageName: String): Boolean {
        if (!hasTilePermission()) return false
        // Use a fresh process: the persistent KernelSU shell can retain stale
        // output after long-running tile actions, making a successful unstop
        // look like a failure (or vice versa).
        val out = execTileCommand(
            arrayOf("pm", "unstop", "--user", "0", packageName), timeoutMs = 5_000L
        ) ?: return false
        val status = execTileCommand(
            arrayOf("dumpsys", "package", packageName), timeoutMs = 5_000L
        ).orEmpty()
        val userState = Regex("(?m)^\\s*User 0:.*$")
            .find(status)?.value.orEmpty()
        val confirmed = out.contains("new stopped state: false", ignoreCase = true) ||
            (userState.isNotEmpty() && userState.contains("stopped=false", ignoreCase = true))
        Log.d(TAG, "pm unstop $packageName → $out; $userState; confirmed=$confirmed")
        return confirmed
    }

    /**
     * Re-applies Health Connect permissions declared by an app through the
     * KernelSU shell. This is used for Samsung Health after its historical
     * disable/enable cycle revoked Health Connect access. The package remains
     * enabled now; the grants are only repaired if Android reports them absent.
     */
    fun ensureHealthConnectPermissions(packageName: String): Boolean {
        if (!hasPermission()) return false
        val dump = execShellFresh(arrayOf("dumpsys", "package", packageName), timeoutMs = 12_000L)
            ?: return false
        val requested = linkedSetOf<String>()
        var inRequestedPermissions = false
        for (line in dump.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed == "requested permissions:") {
                inRequestedPermissions = true
                continue
            }
            if (inRequestedPermissions && trimmed.endsWith("permissions:")) break
            if (inRequestedPermissions && HEALTH_PERMISSION.matches(trimmed)) requested += trimmed
        }
        if (requested.isEmpty()) {
            Log.w(TAG, "No se encontraron permisos Health Connect declarados por $packageName")
            return false
        }

        val before = grantedHealthPermissions(dump)
        val missing = requested - before
        for (permission in missing) {
            execShellFresh(arrayOf("pm", "grant", "--user", "0", packageName, permission), timeoutMs = 5_000L)
        }

        val afterDump = execShellFresh(arrayOf("dumpsys", "package", packageName), timeoutMs = 12_000L)
            ?: return false
        val after = grantedHealthPermissions(afterDump)
        val complete = requested.all { it in after }
        Log.i(TAG, "Health Connect $packageName: ${after.intersect(requested).size}/${requested.size} permisos concedidos; corregidos=${missing.size}")
        if (!complete) return false

        // pm grant itself is silent. If Health Connect's permission page was
        // already left in front by an earlier/manual flow, dismiss it only
        // after the grants above have been verified, never before.
        return closeHealthConnectPermissionActivityIfForeground()
    }

    private fun closeHealthConnectPermissionActivityIfForeground(): Boolean {
        val activityDump = execShellFresh(
            arrayOf("dumpsys", "activity", "activities"), timeoutMs = 10_000L
        ) ?: return false
        val topComponent = Regex("(?m)^\\s*topResumedActivity=ActivityRecord\\{[^\\n]*?\\bu\\d+\\s+([^\\s}]+)")
            .find(activityDump)?.groupValues?.getOrNull(1).orEmpty()
        val topPackage = topComponent.substringBefore('/')
        if (topPackage != HEALTH_CONNECT_CONTROLLER_PACKAGE) return true

        execShellFresh(arrayOf("am", "force-stop", "--user", "0", HEALTH_CONNECT_CONTROLLER_PACKAGE), timeoutMs = 5_000L)
        val afterDump = execShellFresh(
            arrayOf("dumpsys", "activity", "activities"), timeoutMs = 10_000L
        ) ?: return false
        val stillForeground = Regex("(?m)^\\s*topResumedActivity=ActivityRecord\\{[^\\n]*?\\bu\\d+\\s+([^\\s}]+)")
            .find(afterDump)?.groupValues?.getOrNull(1)
            ?.substringBefore('/') == HEALTH_CONNECT_CONTROLLER_PACKAGE
        Log.i(TAG, "Health Connect permiso completo antes de cerrar actividad; pantalla al frente=${!stillForeground}")
        return !stillForeground
    }

    private fun grantedHealthPermissions(packageDump: String): Set<String> {
        val result = mutableSetOf<String>()
        var inRuntimePermissions = false
        for (line in packageDump.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed == "runtime permissions:") {
                inRuntimePermissions = true
                continue
            }
            if (inRuntimePermissions && trimmed.endsWith("permissions:")) break
            if (!inRuntimePermissions || !trimmed.startsWith("android.permission.health.")) continue
            val permission = trimmed.substringBefore(':')
            if (trimmed.contains("granted=true")) result += permission
        }
        return result
    }

    private val HEALTH_PERMISSION = Regex("android\\.permission\\.health\\.[A-Z0-9_]+")
    private const val HEALTH_CONNECT_CONTROLLER_PACKAGE = "com.google.android.healthconnect.controller"

    fun isAppHidden(packageName: String): Boolean {
        return getHiddenPackages(listOf(packageName))?.contains(packageName) ?: false
    }

    /** Returns hidden packages from a bounded set in one root process. */
    fun getHiddenPackages(packages: Collection<String>): Set<String>? {
        if (!hasTilePermission()) return null
        if (packages.any { !PACKAGE_NAME.matches(it) }) return null
        if (packages.isEmpty()) return emptySet()
        val script = buildString {
            append("for p in")
            packages.distinct().forEach { append(" '").append(it).append("'") }
            append("; do dumpsys package \"\$p\" | grep -Eq '^[[:space:]]*User 0:.*hidden=true' && echo \"\$p\"; done; exit 0")
        }
        val out = execTileCommand(arrayOf("sh", "-c", script), timeoutMs = 12_000L) ?: return null
        return out.lineSequence().map { it.trim() }
            .filter { it.isNotEmpty() && PACKAGE_NAME.matches(it) }.toSet()
    }

    /** Returns which of [packages] are still hidden after a hide/unhide action. */
    fun stillHidden(packages: Collection<String>): Set<String> {
        if (packages.isEmpty()) return emptySet()
        if (!hasPermission()) return emptySet()
        return getHiddenPackages(packages)?.intersect(packages.toSet()).orEmpty()
    }

    private val PACKAGE_NAME = Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")
    private val COMPONENT_NAME = Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+/[A-Za-z0-9_.$]+")

    fun stopApp(packageName: String): Boolean {
        if (!hasPermission()) return false
        return execShell("am force-stop $packageName")
    }

    /** Queries ActivityManager's live service records through KernelSU shell. */
    fun getRunningServices(): List<RunningService> {
        if (!hasPermission()) return emptyList()
        val output = execShellFresh(arrayOf("dumpsys", "activity", "services"), timeoutMs = 12_000L)
            ?: return emptyList()
        val result = linkedMapOf<String, RunningService>()
        val record = Regex("(?m)^\\s*\\*?\\s*ServiceRecord\\{[^\\n]*?\\bu\\d+\\s+([A-Za-z0-9_.]+/[^\\s}]+)")
        val matches = record.findAll(output).toList()
        matches.forEachIndexed { index, match ->
            val raw = match.groupValues[1]
            val pkg = raw.substringBefore('/')
            val cls = raw.substringAfter('/')
            val component = "$pkg/$cls"
            val blockEnd = matches.getOrNull(index + 1)?.range?.first ?: output.length
            val block = output.substring(match.range.first, blockEnd)
            val bound = Regex("\\bhasBound=true\\b").containsMatchIn(block)
            if (pkg != "android" && pkg != "com.nemotron.core" && pkg != "com.nemotron.voiceime2") {
                result.putIfAbsent(component, RunningService(component, pkg, bound))
            }
        }
        return result.values.sortedBy { it.component }
    }

    /** Stops only the selected service component; it does not force-stop its package. */
    fun stopService(component: String): Boolean {
        if (!hasPermission() || !component.matches(Regex("[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+"))) return false
        val out = execShellFresh(arrayOf("am", "stopservice", "--user", "0", "-n", component), timeoutMs = 8_000L)
            ?: return false
        val accepted = out.contains("Stopping service:") && !out.contains("Error", ignoreCase = true)
        Log.i(TAG, "stopservice $component → $out (accepted=$accepted)")
        return accepted
    }

    /** Stops a batch of selected components and verifies the result in one system snapshot. */
    fun stopServices(components: Collection<String>): Map<String, Boolean> {
        if (components.isEmpty()) return emptyMap()
        val before = getRunningServices().associateBy { it.component }
        val accepted = components.associateWith { component ->
            if (before[component]?.bound == true) {
                Log.w(TAG, "Not stopping bound service $component; Android still has active clients")
                false
            } else stopService(component)
        }
        val active = getRunningServices().mapTo(mutableSetOf()) { it.component }
        return accepted.mapValues { (component, commandAccepted) ->
            val stopped = commandAccepted && component !in active
            Log.i(TAG, "screen-off service $component stopped=$stopped stillRunning=${component in active}")
            stopped
        }
    }

    /** Returns the package currently holding Android's default assistant role. */
    fun getDefaultAssistantPackage(): String? {
        if (!hasPermission()) return null
        val holders = execShellCapture(
            "cmd role get-role-holders --user \$(am get-current-user) $ASSISTANT_ROLE"
        )
        val holder = holders?.lineSequence()?.map { it.trim() }
            ?.firstOrNull { it.startsWith("com.") }
        if (!holder.isNullOrBlank()) return holder

        // Some vendor builds omit `get-role-holders`; their secure setting is
        // still synchronized from the role holder by Android's assistant service.
        val component = execShellCapture("settings get secure assistant")?.trim()
        return component?.substringBefore('/')?.takeIf { it.startsWith("com.") }
    }

    /** Ask the system role manager (through KernelSU's shell identity) to switch assistant. */
    fun setDefaultAssistantPackage(packageName: String): Boolean {
        if (!hasPermission() || packageName !in setOf(GEMINI_PACKAGE, TOOTSIE_PACKAGE)) return false
        val output = execShellCapture(
            "cmd role add-role-holder --user \$(am get-current-user) $ASSISTANT_ROLE $packageName 0; " +
                "role_status=\$?; echo NEMO_ASSISTANT_ROLE_EXIT:\$role_status"
        ) ?: return false
        if (!output.contains("NEMO_ASSISTANT_ROLE_EXIT:0")) {
            Log.w(TAG, "Could not set assistant role to $packageName: $output")
            return false
        }
        // The role command waits for its callback, but allow vendor settings a
        // moment to reflect the new holder before the tile refreshes its label.
        Thread.sleep(250)
        return getDefaultAssistantPackage() == packageName
    }

    /** Apaga y bloquea la pantalla (equivale a pulsar el botón de encendido). */
    fun lockScreen(): Boolean {
        if (!hasPermission()) return false
        return execShell("input keyevent 26")
    }

    /** Desactiva el modo No Molestar (vuelve al filtro "todos"). */
    fun disableDnd(): Boolean {
        if (!hasPermission()) return false
        return execShell("cmd notification set_dnd off")
    }

    /** Verifica si el NFC está encendido. */
    fun isNfcEnabled(): Boolean {
        if (!hasPermission()) return false
        val out = execShellCapture("dumpsys nfc | grep mState") ?: return false
        return out.contains("mState=on")
    }

    /** Enciende/apaga el NFC. */
    fun setNfcEnabled(enabled: Boolean): Boolean {
        if (!hasPermission()) return false
        return execShell(if (enabled) "cmd nfc enable-nfc" else "cmd nfc disable-nfc")
    }

    /** Verifica si la app tiene un proceso corriendo. */
    fun isProcessRunning(packageName: String): Boolean {
        if (!hasPermission()) return false
        val out = execShellCapture("pidof $packageName") ?: return false
        return out.isNotBlank()
    }

    private const val ANDROID_AUTO_PKG = "com.google.android.projection.gearhead"

    /** True si Android Auto está activo (proyección al coche). Los procesos de
     *  Android Auto llevan sufijo (:car, :shared, ...) y el proceso base no
     *  siempre existe, por eso se listan las variantes más comunes. */
    fun isAndroidAutoActive(): Boolean {
        if (!hasPermission()) return false
        val out = execShellCapture(
            "pidof $ANDROID_AUTO_PKG " +
                "$ANDROID_AUTO_PKG:car $ANDROID_AUTO_PKG:shared " +
                "$ANDROID_AUTO_PKG:watchdog $ANDROID_AUTO_PKG:provider"
        ) ?: return false
        return out.isNotBlank()
    }

    /** Activa/desactiva el gesto Samsung «Doble toque para encender pantalla». */
    fun setDoubleTapToWake(enabled: Boolean): Boolean {
        if (!hasPermission()) return false
        val value = if (enabled) "1" else "0"
        val ok = execShell("settings put system double_tab_to_wake_up $value")
        Log.i(TAG, "double tap to wake → $enabled")
        return ok
    }

    /** Activa/desactiva el gesto Samsung «Doble toque para apagar pantalla». */
    fun setDoubleTapToSleep(enabled: Boolean): Boolean {
        if (!hasPermission()) return false
        val value = if (enabled) "1" else "0"
        val ok = execShell("settings put system double_tap_to_sleep $value")
        Log.i(TAG, "double tap to sleep → $enabled")
        return ok
    }

    /** Lee el estado del modo avión. */
    fun isAirplaneModeOn(): Boolean? {
        if (!hasPermission()) return null
        return when (execShellFresh(arrayOf("settings", "get", "global", "airplane_mode_on"))?.trim()) {
            "1" -> true
            "0" -> false
            else -> null
        }
    }

    /** Cambia el modo avión usando el comando soportado por ConnectivityService. */
    fun setAirplaneMode(enabled: Boolean): Boolean {
        if (!hasPermission()) return false
        val action = if (enabled) "enable" else "disable"
        return execShellFresh(arrayOf("cmd", "connectivity", "airplane-mode", action)) != null
    }

    /** Lanza la app como si fuera del home screen. */
    fun launchApp(packageName: String): Boolean {
        if (!hasPermission()) return false
        return execShell("monkey -p $packageName -c android.intent.category.LAUNCHER 1")
    }

    /** Lanza una actividad concreta de la app. */
    fun launchApp(packageName: String, activity: String): Boolean {
        if (!hasPermission()) return false
        return execShell("am start -n $packageName/$activity")
    }

    /** Exime a [packageName] de doze para que Samsung no congele su proceso. */
    fun exemptFromDoze(packageName: String): Boolean {
        if (!hasPermission()) return false
        val out = execShellCapture("dumpsys deviceidle whitelist +$packageName")
        execShell("am set-standby-bucket $packageName active")
        Log.d(TAG, "doze whitelist $packageName → $out")
        return out?.contains("Added") == true
    }

    /** Fuerza doze profundo de inmediato (con pantalla apagada). */
    fun forceIdle(): Boolean {
        if (!hasPermission()) return false
        val out = execShellCapture("cmd deviceidle force-idle") ?: return false
        Log.d(TAG, "force-idle → $out")
        return out.contains("forced")
    }

    /** Revierte el doze forzado. */
    fun unforceIdle(): Boolean {
        if (!hasPermission()) return false
        val out = execShellCapture("cmd deviceidle unforce") ?: return false
        Log.d(TAG, "unforce-idle → $out")
        return true
    }

    fun isAccessibilityServiceEnabled(serviceComponent: String): Boolean {
        if (!hasPermission()) return false
        val current = execShellCapture("settings get secure enabled_accessibility_services") ?: return false
        return current.split(':').map { it.trim() }.contains(serviceComponent)
    }

    fun setAccessibilityServiceEnabled(serviceComponent: String, enabled: Boolean): Boolean {
        if (!hasPermission()) return false
        val current = execShellCapture("settings get secure enabled_accessibility_services") ?: return false
        val services = current.split(':')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "null" }
            .toMutableSet()
        val changed = if (enabled) services.add(serviceComponent) else services.remove(serviceComponent)
        val joined = services.joinToString(":")
        val servicesOk = !changed || execShell("settings put secure enabled_accessibility_services '$joined'")
        if (!servicesOk) return false
        val accessibilityEnabled = enabled || services.isNotEmpty()
        val flagOk = execShell("settings put secure accessibility_enabled ${if (accessibilityEnabled) 1 else 0}")
        Log.d(TAG, "a11y $serviceComponent → $enabled [$joined]")
        return flagOk
    }

}
