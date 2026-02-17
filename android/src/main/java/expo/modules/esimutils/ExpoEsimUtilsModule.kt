package expo.modules.esimutils

import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.euicc.EuiccInfo
import android.telephony.euicc.EuiccManager
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class ExpoEsimUtilsModule : Module() {
    override fun definition() = ModuleDefinition {
        Name("ExpoEsimUtils")

        /// Returns true if the device hardware supports eSIM.
        /// Uses EuiccManager.isEnabled() — no permissions required.
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

                // Get eUICC firmware info if available
                try {
                    val euiccInfo: EuiccInfo? = euiccManager.euiccInfo
                    euiccInfo?.let {
                        result["osVersion"] = it.osVersion
                    }
                } catch (_: Exception) {
                    // EuiccInfo may not be accessible on all devices
                }

                // Android 13+: Check if a SIM port is available for a new profile
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    try {
                        result["isSimPortAvailable"] = euiccManager.isSimPortAvailable(0)
                    } catch (_: Exception) {
                        // Port availability check may fail on some devices
                    }
                }
            } else {
                result["reason"] = "eSIM is not enabled or not supported on this device"
            }

            return@Function result
        }

        /// Returns a list of active cellular subscriptions.
        /// Note: On Android 10+, reading subscription details requires READ_PHONE_STATE permission.
        /// Without the permission, this returns an empty list (no crash).
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

                    // Check if this subscription uses an embedded (eSIM) SIM
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        plan["isEmbedded"] = info.isEmbedded
                    }

                    plans.add(plan)
                }
            } catch (_: SecurityException) {
                // READ_PHONE_STATE permission not granted — return empty list
            } catch (_: Exception) {
                // Unexpected error — return empty list
            }

            return@Function plans
        }

        /// Opens the system eSIM management / setup screen.
        ///
        /// On Android 9+: launches EuiccManager.ACTION_MANAGE_EMBEDDED_SUBSCRIPTIONS.
        /// If an activation code is provided, it is copied to the clipboard for the user.
        AsyncFunction("openEsimSetup") { activationCode: String? ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                return@AsyncFunction false
            }

            val euiccManager = context.getSystemService(Context.EUICC_SERVICE) as? EuiccManager
            if (euiccManager == null || !euiccManager.isEnabled) {
                return@AsyncFunction false
            }

            // Copy activation code to clipboard if provided
            if (!activationCode.isNullOrEmpty()) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                clipboard?.setPrimaryClip(
                    android.content.ClipData.newPlainText("eSIM Activation Code", activationCode)
                )
            }

            // Open the system eSIM management screen
            val intent = Intent(EuiccManager.ACTION_MANAGE_EMBEDDED_SUBSCRIPTIONS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            try {
                val activity = appContext.currentActivity
                if (activity != null) {
                    activity.startActivity(intent)
                } else {
                    context.startActivity(intent)
                }
                return@AsyncFunction true
            } catch (_: Exception) {
                return@AsyncFunction false
            }
        }
    }

    private val context: Context
        get() = requireNotNull(appContext.reactContext) {
            "React Application Context is not available"
        }
}
