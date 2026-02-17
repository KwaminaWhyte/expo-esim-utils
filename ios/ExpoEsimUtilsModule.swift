import ExpoModulesCore
import CoreTelephony

public class ExpoEsimUtilsModule: Module {
    public func definition() -> ModuleDefinition {
        Name("ExpoEsimUtils")

        /// Returns true if the device hardware supports eSIM.
        /// Uses CTCellularPlanProvisioning.supportsCellularPlan() — no entitlement required.
        Function("isEsimSupported") { () -> Bool in
            guard #available(iOS 12.0, *) else {
                return false
            }
            let provisioning = CTCellularPlanProvisioning()
            return provisioning.supportsCellularPlan()
        }

        /// Returns detailed eSIM capability information.
        Function("getEsimCapability") { () -> [String: Any] in
            var result: [String: Any] = [
                "platform": "ios",
                "isSupported": false,
                "reason": "unknown",
            ]

            guard #available(iOS 12.0, *) else {
                result["reason"] = "Requires iOS 12.0 or later"
                return result
            }

            let provisioning = CTCellularPlanProvisioning()
            let supported = provisioning.supportsCellularPlan()
            result["isSupported"] = supported
            result["reason"] = supported
                ? "Device supports eSIM via CoreTelephony"
                : "Device hardware does not support eSIM"

            // Additional carrier info from CTTelephonyNetworkInfo
            if #available(iOS 12.0, *) {
                let networkInfo = CTTelephonyNetworkInfo()
                if let carriers = networkInfo.serviceSubscriberCellularProviders {
                    var plans: [[String: Any]] = []
                    for (key, carrier) in carriers {
                        var plan: [String: Any] = ["slot": key]
                        if let name = carrier.carrierName {
                            plan["carrierName"] = name
                        }
                        if let mcc = carrier.mobileCountryCode {
                            plan["mobileCountryCode"] = mcc
                        }
                        if let mnc = carrier.mobileNetworkCode {
                            plan["mobileNetworkCode"] = mnc
                        }
                        plan["allowsVOIP"] = carrier.allowsVOIP
                        plans.append(plan)
                    }
                    if !plans.isEmpty {
                        result["activePlans"] = plans
                    }
                }
            }

            return result
        }

        /// Returns a list of active cellular plans on the device.
        Function("getActivePlans") { () -> [[String: Any]] in
            var plans: [[String: Any]] = []

            guard #available(iOS 12.0, *) else {
                return plans
            }

            let networkInfo = CTTelephonyNetworkInfo()
            guard let carriers = networkInfo.serviceSubscriberCellularProviders else {
                return plans
            }

            for (key, carrier) in carriers {
                var plan: [String: Any] = ["slot": key]
                if let name = carrier.carrierName {
                    plan["carrierName"] = name
                }
                if let mcc = carrier.mobileCountryCode {
                    plan["mobileCountryCode"] = mcc
                }
                if let mnc = carrier.mobileNetworkCode {
                    plan["mobileNetworkCode"] = mnc
                }
                if let isoCode = carrier.isoCountryCode {
                    plan["isoCountryCode"] = isoCode
                }
                plan["allowsVOIP"] = carrier.allowsVOIP
                plans.append(plan)
            }

            return plans
        }

        /// Opens the device eSIM setup flow.
        ///
        /// - With an activation code on iOS 17.4+: opens `cellular-setup://` URL for direct install.
        /// - Without: opens the cellular settings page.
        ///
        /// The activation code should be in LPA format: `LPA:1$<SMDP_ADDRESS>$<MATCHING_ID>`
        AsyncFunction("openEsimSetup") { (activationCode: String?) -> Bool in
            // iOS 17.4+ supports the cellular-setup:// URL scheme for direct eSIM activation
            if #available(iOS 17.4, *), let code = activationCode, !code.isEmpty {
                let encoded = code.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? code
                if let url = URL(string: "cellular-setup://esim?carddata=\(encoded)") {
                    let canOpen = await MainActor.run {
                        UIApplication.shared.canOpenURL(url)
                    }
                    if canOpen {
                        await MainActor.run {
                            UIApplication.shared.open(url, options: [:], completionHandler: nil)
                        }
                        return true
                    }
                }
            }

            // Fallback: open the cellular data settings
            if let settingsUrl = URL(string: "App-prefs:MOBILE_DATA_SETTINGS_ID") {
                await MainActor.run {
                    UIApplication.shared.open(settingsUrl, options: [:], completionHandler: nil)
                }
                return true
            }

            return false
        }
    }
}
