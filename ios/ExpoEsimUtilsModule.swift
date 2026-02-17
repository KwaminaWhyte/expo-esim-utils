import ExpoModulesCore
import CoreTelephony
import UIKit

public class ExpoEsimUtilsModule: Module {
    public func definition() -> ModuleDefinition {
        Name("ExpoEsimUtils")

        /// Returns true if the device hardware supports eSIM.
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

            let networkInfo = CTTelephonyNetworkInfo()
            if let carriers = networkInfo.serviceSubscriberCellularProviders {
                var plans: [[String: Any]] = []
                for (key, carrier) in carriers {
                    var plan: [String: Any] = ["slot": key]
                    if let name = carrier.carrierName { plan["carrierName"] = name }
                    if let mcc = carrier.mobileCountryCode { plan["mobileCountryCode"] = mcc }
                    if let mnc = carrier.mobileNetworkCode { plan["mobileNetworkCode"] = mnc }
                    plan["allowsVOIP"] = carrier.allowsVOIP
                    plans.append(plan)
                }
                if !plans.isEmpty { result["activePlans"] = plans }
            }

            return result
        }

        /// Returns a list of active cellular plans on the device.
        Function("getActivePlans") { () -> [[String: Any]] in
            var plans: [[String: Any]] = []
            guard #available(iOS 12.0, *) else { return plans }

            let networkInfo = CTTelephonyNetworkInfo()
            guard let carriers = networkInfo.serviceSubscriberCellularProviders else { return plans }

            for (key, carrier) in carriers {
                var plan: [String: Any] = ["slot": key]
                if let name = carrier.carrierName { plan["carrierName"] = name }
                if let mcc = carrier.mobileCountryCode { plan["mobileCountryCode"] = mcc }
                if let mnc = carrier.mobileNetworkCode { plan["mobileNetworkCode"] = mnc }
                if let isoCode = carrier.isoCountryCode { plan["isoCountryCode"] = isoCode }
                plan["allowsVOIP"] = carrier.allowsVOIP
                plans.append(plan)
            }
            return plans
        }

        /// Opens the eSIM installation flow.
        ///
        /// iOS 17.4+: Uses Apple's Universal Link to open the native eSIM install screen.
        ///   URL: https://esimsetup.apple.com/esim_qrcode_provisioning?carddata=<LPA_CODE>
        ///   No entitlement required — works for any app.
        ///
        /// iOS < 17.4: Returns "unsupported" so the JS layer can show QR/manual fallback.
        ///
        /// The activation code should be in LPA format: LPA:1$<SMDP_ADDRESS>$<MATCHING_ID>
        ///
        /// Returns: "settings_opened" on success, "unsupported" if iOS < 17.4, "fail" on error.
        AsyncFunction("openEsimSetup") { (activationCode: String?) -> String in
            guard let code = activationCode, !code.isEmpty else {
                return "fail"
            }

            // iOS 17.4+ — use Apple's Universal Link for eSIM provisioning
            if #available(iOS 17.4, *) {
                let encodedCode = code.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? code
                let urlString = "https://esimsetup.apple.com/esim_qrcode_provisioning?carddata=\(encodedCode)"

                guard let url = URL(string: urlString) else {
                    return "fail"
                }

                let opened: Bool = await withCheckedContinuation { continuation in
                    DispatchQueue.main.async {
                        UIApplication.shared.open(url, options: [:]) { success in
                            continuation.resume(returning: success)
                        }
                    }
                }

                return opened ? "settings_opened" : "fail"
            }

            // iOS < 17.4 — no entitlement-free install method available
            return "unsupported"
        }
    }
}
