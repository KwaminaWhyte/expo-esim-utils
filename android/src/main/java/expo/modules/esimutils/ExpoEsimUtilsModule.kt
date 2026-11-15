package expo.modules.esimutils

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.SubscriptionManager
import android.telephony.euicc.DownloadableSubscription
import android.telephony.euicc.EuiccInfo
import android.telephony.euicc.EuiccManager
import expo.modules.kotlin.Promise
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class ExpoEsimUtilsModule : Module() {

    companion object {
        private const val ACTION_DOWNLOAD_SUBSCRIPTION = "expo.modules.esimutils.DOWNLOAD_SUBSCRIPTION"

        /// How long installEsim waits for the system to report a final result.
        private const val INSTALL_TIMEOUT_MS = 4 * 60 * 1000L

        /// A download can need more than one confirmation (consent, then a
        /// confirmation code). More than this is a loop, not a flow.
        private const val MAX_RESOLUTIONS = 3
    }

    override fun definition() = ModuleDefinition {
        Name("ExpoEsimUtils")

        /// Returns true if the device hardware supports eSIM.
        Function("isEsimSupported") {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                return@Function false
            }
            val euiccManager = context.getSystemService(Context.EUICC_SERVICE) as? EuiccManager
            return@Function euiccManager?.isEnabled ?: false
        }

        /// Returns detailed eSIM capability information.
        Function("getEsimCapability") {
            val result = mutableMapOf<String, Any?>(
                "platform" to "android",
                "isSupported" to false,
                "confidence" to "confirmed",
                "reason" to "unknown"
            )

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                result["reason"] = "Requires Android 9 (API 28) or later"
                return@Function result
            }

            val euiccManager = context.getSystemService(Context.EUICC_SERVICE) as? EuiccManager

            if (euiccManager == null) {
                result["reason"] = "EuiccManager service not available on this device"
                return@Function result
            }

            val isEnabled = euiccManager.isEnabled
            result["isSupported"] = isEnabled
            // Android has no carrier-entitlement wall like iOS's CoreTelephony —
            // EuiccManager.isEnabled() is a direct hardware/OS check, so this is
            // always "confirmed", never a model-based guess.

            if (isEnabled) {
                result["reason"] = "Device supports eSIM via EuiccManager"
                try {
                    val euiccInfo: EuiccInfo? = euiccManager.euiccInfo
                    euiccInfo?.let { result["osVersion"] = it.osVersion }
                } catch (_: Exception) {}

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    try {
                        result["isSimPortAvailable"] = euiccManager.isSimPortAvailable(0)
                    } catch (_: Exception) {}
                }
            } else {
                result["reason"] = "eSIM is not enabled or not supported on this device"
            }

            return@Function result
        }

        /// Returns a list of active cellular subscriptions.
        Function("getActivePlans") {
            val plans = mutableListOf<Map<String, Any?>>()

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
                return@Function plans
            }

            try {
                val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                    ?: return@Function plans

                @Suppress("MissingPermission")
                val subscriptions = subscriptionManager.activeSubscriptionInfoList ?: return@Function plans

                for (info in subscriptions) {
                    val plan = mutableMapOf<String, Any?>(
                        "slot" to info.simSlotIndex.toString(),
                        "carrierName" to info.carrierName?.toString(),
                        "mobileCountryCode" to info.mccString,
                        "mobileNetworkCode" to info.mncString,
                        "isoCountryCode" to info.countryIso,
                        "subscriptionId" to info.subscriptionId
                    )

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        plan["isEmbedded"] = info.isEmbedded
                    }

                    plans.add(plan)
                }
            } catch (_: SecurityException) {
            } catch (_: Exception) {
            }

            return@Function plans
        }

        /// Downloads and installs an eSIM profile on the device.
        ///
        /// Uses EuiccManager.downloadSubscription() which triggers a system consent dialog
        /// for non-carrier apps. The user confirms, and the profile is downloaded directly.
        ///
        /// If downloadSubscription fails or is not available, falls back to opening
        /// the system eSIM management screen with the code copied to clipboard.
        ///
        /// Returns: "success", "fail", "settings_opened", or "unsupported"
        AsyncFunction("openEsimSetup") { activationCode: String?, promise: Promise ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                promise.resolve("unsupported")
                return@AsyncFunction
            }

            val euiccManager = context.getSystemService(Context.EUICC_SERVICE) as? EuiccManager
            if (euiccManager == null || !euiccManager.isEnabled) {
                promise.resolve("unsupported")
                return@AsyncFunction
            }

            if (activationCode.isNullOrEmpty()) {
                promise.resolve("fail")
                return@AsyncFunction
            }

            // Try downloadSubscription for direct in-app install
            try {
                val subscription = DownloadableSubscription.forActivationCode(bareActivationCode(activationCode))

                val callbackIntent = Intent(ACTION_DOWNLOAD_SUBSCRIPTION).apply {
                    setPackage(context.packageName)
                }

                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    0,
                    callbackIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )

                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context?, intent: Intent?) {
                        context.unregisterReceiver(this)
                        val resultCode = resultCode
                        when (resultCode) {
                            EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_OK -> {
                                promise.resolve("success")
                            }
                            EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_RESOLVABLE_ERROR -> {
                                // Non-carrier app: system shows a user consent dialog
                                try {
                                    val activity = appContext.currentActivity
                                    if (activity != null && intent != null) {
                                        euiccManager.startResolutionActivity(
                                            activity,
                                            0,
                                            intent,
                                            pendingIntent
                                        )
                                        promise.resolve("settings_opened")
                                    } else {
                                        promise.resolve("fail")
                                    }
                                } catch (_: Exception) {
                                    promise.resolve("fail")
                                }
                            }
                            else -> {
                                // Try Android Universal Link before giving up
                                if (tryOpenUniversalLink(activationCode)) {
                                    promise.resolve("universal_link_opened")
                                } else if (openSettingsFallback(activationCode)) {
                                    promise.resolve("settings_opened")
                                } else {
                                    promise.resolve("fail")
                                }
                            }
                        }
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(
                        receiver,
                        IntentFilter(ACTION_DOWNLOAD_SUBSCRIPTION),
                        Context.RECEIVER_NOT_EXPORTED
                    )
                } else {
                    context.registerReceiver(
                        receiver,
                        IntentFilter(ACTION_DOWNLOAD_SUBSCRIPTION)
                    )
                }

                euiccManager.downloadSubscription(subscription, true, pendingIntent)
                return@AsyncFunction
            } catch (_: Exception) {
                // downloadSubscription failed — try middle fallback (universal link), then settings
            }

            // Middle fallback: Android Universal Link (API 29+)
            if (tryOpenUniversalLink(activationCode)) {
                promise.resolve("universal_link_opened")
                return@AsyncFunction
            }

            // Final fallback: copy to clipboard and open eSIM management screen
            if (openSettingsFallback(activationCode)) {
                promise.resolve("settings_opened")
            } else {
                promise.resolve("fail")
            }
        }

        /// Downloads and installs an eSIM profile and reports what actually happened.
        ///
        /// Unlike openEsimSetup, this keeps listening after the system shows its
        /// consent dialog, so the promise resolves with the real outcome
        /// (installed, failed, or timed out) rather than "settings_opened".
        /// Failures carry Android's result and error codes so the caller can tell
        /// a full eUICC from a carrier lock from a network error.
        ///
        /// Resolves a map: status ("success" | "failed" | "timeout" |
        /// "universal_link_opened" | "settings_opened" | "unsupported"),
        /// consentShown, and when Android supplied them resultCode, detailedCode,
        /// operationCode, errorCode, smdxSubjectCode, smdxReasonCode.
        AsyncFunction("installEsim") { activationCode: String?, promise: Promise ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                promise.resolve(mapOf("status" to "unsupported", "consentShown" to false))
                return@AsyncFunction
            }

            val euiccManager = context.getSystemService(Context.EUICC_SERVICE) as? EuiccManager
            if (euiccManager == null || !euiccManager.isEnabled) {
                promise.resolve(mapOf("status" to "unsupported", "consentShown" to false))
                return@AsyncFunction
            }

            if (activationCode.isNullOrBlank()) {
                promise.resolve(mapOf("status" to "failed", "consentShown" to false, "reason" to "no_activation_code"))
                return@AsyncFunction
            }

            val handler = Handler(Looper.getMainLooper())
            var settled = false
            var resolutions = 0
            var receiver: BroadcastReceiver? = null

            fun finish(status: String, code: Int?, intent: Intent?, reason: String? = null) {
                if (settled) return
                settled = true
                handler.removeCallbacksAndMessages(null)
                receiver?.let {
                    try {
                        context.unregisterReceiver(it)
                    } catch (_: Exception) {
                    }
                }
                promise.resolve(installResult(status, code, intent, resolutions > 0, reason))
            }

            /// Nothing was shown to the user yet: hand over to a system install screen.
            fun fallBack(code: Int?, intent: Intent?, reason: String) {
                when {
                    tryOpenUniversalLink(activationCode) -> finish("universal_link_opened", code, intent, reason)
                    openSettingsFallback(activationCode) -> finish("settings_opened", code, intent, reason)
                    else -> finish("failed", code, intent, reason)
                }
            }

            try {
                // Unique per attempt, so a late callback from an earlier attempt
                // can never settle this one.
                val action = "$ACTION_DOWNLOAD_SUBSCRIPTION.${System.nanoTime()}"
                val callbackIntent = Intent(action).apply { setPackage(context.packageName) }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    0,
                    callbackIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )

                receiver = object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context?, intent: Intent?) {
                        val code = resultCode
                        when (code) {
                            EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_OK -> finish("success", code, intent)

                            EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_RESOLVABLE_ERROR -> {
                                // The system needs the user (consent, or a
                                // confirmation code). Show its dialog and KEEP
                                // listening: it reports the final result to this
                                // same PendingIntent when the user is done.
                                val activity = appContext.currentActivity
                                if (activity == null || intent == null) {
                                    fallBack(code, intent, "no_activity_for_resolution")
                                    return
                                }
                                if (resolutions >= MAX_RESOLUTIONS) {
                                    finish("failed", code, intent, "too_many_resolutions")
                                    return
                                }
                                try {
                                    euiccManager.startResolutionActivity(activity, 0, intent, pendingIntent)
                                    resolutions++
                                } catch (_: Exception) {
                                    if (resolutions > 0) {
                                        finish("failed", code, intent, "resolution_failed")
                                    } else {
                                        fallBack(code, intent, "resolution_unavailable")
                                    }
                                }
                            }

                            else -> {
                                // After the system dialog this is the user
                                // cancelling or a real download failure; either way
                                // they have already been through one install
                                // screen, so do not open another.
                                if (resolutions > 0) {
                                    finish("failed", code, intent)
                                } else {
                                    fallBack(code, intent, "download_error")
                                }
                            }
                        }
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(receiver, IntentFilter(action), Context.RECEIVER_NOT_EXPORTED)
                } else {
                    context.registerReceiver(receiver, IntentFilter(action))
                }

                handler.postDelayed({ finish("timeout", null, null) }, INSTALL_TIMEOUT_MS)

                val subscription = DownloadableSubscription.forActivationCode(bareActivationCode(activationCode))
                euiccManager.downloadSubscription(subscription, true, pendingIntent)
            } catch (_: Exception) {
                fallBack(null, null, "download_threw")
            }
        }
    }

    /// The SGP.22 activation code without the "LPA:" URI scheme.
    ///
    /// A QR code carries "LPA:1$<smdp>$<matching id>". EuiccManager wants only
    /// the activation code ("1$<smdp>$<matching id>"); whether a phone's LPA
    /// tolerates the scheme in front differs by manufacturer.
    private fun bareActivationCode(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.startsWith("LPA:", ignoreCase = true)) trimmed.substring(4) else trimmed
    }

    /// The result installEsim resolves with, including whatever error detail
    /// Android attached to the callback.
    private fun installResult(
        status: String,
        resultCode: Int?,
        intent: Intent?,
        consentShown: Boolean,
        reason: String?
    ): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>("status" to status, "consentShown" to consentShown)
        resultCode?.let { result["resultCode"] = it }
        reason?.let { result["reason"] = it }
        if (intent != null) {
            if (intent.hasExtra(EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_DETAILED_CODE)) {
                result["detailedCode"] =
                    intent.getIntExtra(EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_DETAILED_CODE, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (intent.hasExtra(EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_OPERATION_CODE)) {
                    result["operationCode"] =
                        intent.getIntExtra(EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_OPERATION_CODE, 0)
                }
                if (intent.hasExtra(EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_ERROR_CODE)) {
                    result["errorCode"] =
                        intent.getIntExtra(EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_ERROR_CODE, 0)
                }
                intent.getStringExtra(EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_SMDX_SUBJECT_CODE)
                    ?.let { result["smdxSubjectCode"] = it }
                intent.getStringExtra(EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_SMDX_REASON_CODE)
                    ?.let { result["smdxReasonCode"] = it }
            }
        }
        return result
    }

    /// Attempts to launch the Android Universal Link for eSIM install.
    /// Returns true if a handler was found and the activity started successfully.
    /// Requires Android 10+ (API 29+). On older versions or when no handler exists, returns false.
    private fun tryOpenUniversalLink(activationCode: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return false
        }
        return try {
            val encoded = Uri.encode("LPA:" + bareActivationCode(activationCode))
            val uri = Uri.parse("https://esimsetup.android.com/esim_qrcode_provisioning?carddata=$encoded")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) == null) {
                return false
            }
            val activity = appContext.currentActivity
            if (activity != null) {
                activity.startActivity(intent)
            } else {
                context.startActivity(intent)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /// Copies the activation code to clipboard and opens the system eSIM management screen.
    /// Returns true on success, false otherwise.
    private fun openSettingsFallback(activationCode: String): Boolean {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            clipboard?.setPrimaryClip(
                android.content.ClipData.newPlainText("eSIM Activation Code", activationCode)
            )

            val intent = Intent(EuiccManager.ACTION_MANAGE_EMBEDDED_SUBSCRIPTIONS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            val activity = appContext.currentActivity
            if (activity != null) {
                activity.startActivity(intent)
            } else {
                context.startActivity(intent)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private val context: Context
        get() = requireNotNull(appContext.reactContext) {
            "React Application Context is not available"
        }
}
