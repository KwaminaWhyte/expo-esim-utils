package expo.modules.esimutils

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.euicc.DownloadableSubscription
import android.telephony.euicc.EuiccInfo
import android.telephony.euicc.EuiccManager
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class ExpoEsimUtilsModule : Module() {

    companion object {
        private const val ACTION_DOWNLOAD_SUBSCRIPTION = "expo.modules.esimutils.DOWNLOAD_SUBSCRIPTION"
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
        AsyncFunction("openEsimSetup") { activationCode: String? ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                return@AsyncFunction "unsupported"
            }

            val euiccManager = context.getSystemService(Context.EUICC_SERVICE) as? EuiccManager
            if (euiccManager == null || !euiccManager.isEnabled) {
                return@AsyncFunction "unsupported"
            }

            if (activationCode.isNullOrEmpty()) {
                return@AsyncFunction "fail"
            }

            // Try downloadSubscription for direct in-app install
            try {
                val subscription = DownloadableSubscription.forActivationCode(activationCode)

                val result = suspendCoroutine<String> { continuation ->
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
                                    continuation.resume("success")
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
                                            continuation.resume("settings_opened")
                                        } else {
                                            continuation.resume("fail")
                                        }
                                    } catch (_: Exception) {
                                        continuation.resume("fail")
                                    }
                                }
                                else -> {
                                    continuation.resume("fail")
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
                }

                return@AsyncFunction result
            } catch (_: Exception) {
                // downloadSubscription failed — fall back to opening settings with clipboard
            }

            // Fallback: copy to clipboard and open eSIM management screen
            try {
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
                return@AsyncFunction "settings_opened"
            } catch (_: Exception) {
                return@AsyncFunction "fail"
            }
        }
    }

    private val context: Context
        get() = requireNotNull(appContext.reactContext) {
            "React Application Context is not available"
        }
}
