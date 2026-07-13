import ExpoModulesCore
import CoreTelephony
import UIKit

public class ExpoEsimUtilsModule: Module {
    /// Reads the hardware model identifier (e.g. "iPhone12,8" for iPhone SE 2nd gen).
    /// Returns the simulator's host model when running under Simulator.
    private static func deviceModelIdentifier() -> String {
        if let simModel = ProcessInfo.processInfo.environment["SIMULATOR_MODEL_IDENTIFIER"] {
            return simModel
        }
        var sysinfo = utsname()
        uname(&sysinfo)
        let machineMirror = Mirror(reflecting: sysinfo.machine)
        let identifier = machineMirror.children.reduce(into: "") { result, element in
            guard let value = element.value as? Int8, value != 0 else { return }
            result.append(String(UnicodeScalar(UInt8(value))))
        }
        return identifier
    }

    /// Cellular-variant iPad model identifiers known to support eSIM, per Apple's
    /// official device list (https://support.apple.com/en-us/119592).
    ///
    /// Unlike iPhone — where every worldwide unit of a given generation ships with
    /// both a nano-SIM tray and an eSIM — iPad Wi-Fi and Wi-Fi+Cellular are
    /// different hardware SKUs with different model identifiers, and *only* the
    /// Cellular SKU has any SIM (physical or embedded) at all. That means this
    /// can't be expressed as a "major version and later" cutoff the way the
    /// iPhone list below is; it has to be an explicit allowlist of the Cellular
    /// identifiers. Wi-Fi-only identifiers correctly fall through to `false`
    /// because those units have no SIM hardware whatsoever.
    ///
    /// Update this set when Apple ships new Cellular iPad models.
    private static let cellularEsimIPadModels: Set<String> = [
        // iPad Pro 11" (1st–4th gen, 2018–2022)
        "iPad8,3", "iPad8,4", "iPad8,10", "iPad13,5", "iPad13,7", "iPad14,4",
        // iPad Pro 12.9" (3rd–6th gen, 2018–2022)
        "iPad8,7", "iPad8,8", "iPad8,12", "iPad13,9", "iPad13,11", "iPad14,6",
        // iPad Pro 11" / 13" (M4 and later)
        "iPad16,4", "iPad16,6",
        // iPad Air (3rd–5th gen, 2019–2022)
        "iPad11,4", "iPad13,2", "iPad13,17",
        // iPad Air 11" / 13" (M2 and later)
        "iPad14,9", "iPad14,11", "iPad15,4", "iPad15,6",
        // iPad (7th–10th gen, 2019–2022)
        "iPad7,12", "iPad11,7", "iPad12,2", "iPad13,19",
        // iPad (A16 and later)
        "iPad15,8",
        // iPad mini (5th–6th gen, 2019–2021)
        "iPad11,2", "iPad14,2",
        // iPad mini (A17 Pro and later)
        "iPad16,2",
    ]

    /// Heuristic eSIM hardware capability via device model identifier.
    /// Used as a fallback because `CTCellularPlanProvisioning.supportsCellularPlan()`
    /// (and its `supportsEmbeddedSIM` sibling — same underlying entitlement gate)
    /// only returns true for apps that hold Apple's carrier-only commercial eSIM
    /// entitlement — both return false for every other app even on eSIM-capable
    /// hardware.
    ///
    /// eSIM-capable iPhones (Worldwide models — China/HK dual-physical-SIM variants excluded):
    /// - iPhone XS / XS Max / XR     → iPhone11,*
    /// - iPhone 11 series, SE 2nd gen → iPhone12,*
    /// - iPhone 12 series            → iPhone13,*
    /// - iPhone 13 series, SE 3rd gen, 14/14 Plus → iPhone14,*
    /// - iPhone 14 Pro/Pro Max, 15 series → iPhone15,*
    /// - iPhone 15 Pro/Pro Max, 16e   → iPhone16,*
    /// - iPhone 16 series            → iPhone17,*
    /// - Future iPhone18,* and later → assume yes
    ///
    /// eSIM-capable iPads: see `cellularEsimIPadModels` above — Wi-Fi-only models
    /// (~75% of iPads sold) never support eSIM since they have no SIM hardware.
    private static func modelLikelySupportsEsim(_ model: String) -> Bool {
        if model.hasPrefix("iPhone") {
            let suffix = model.dropFirst("iPhone".count)
            guard let commaIdx = suffix.firstIndex(of: ","),
                  let major = Int(suffix[..<commaIdx]) else {
                return false
            }
            return major >= 11
        }
        if model.hasPrefix("iPad") {
            return cellularEsimIPadModels.contains(model)
        }
        return false
    }

    public func definition() -> ModuleDefinition {
        Name("ExpoEsimUtils")

        /// Returns true if the device hardware supports eSIM.
        ///
        /// Uses `CTCellularPlanProvisioning.supportsCellularPlan()` first; that API
        /// requires Apple's carrier-only entitlement and returns false for normal
        /// apps even on eSIM-capable iPhones and iPads. Falls back to a device-model
        /// check (iPhone version cutoff, iPad Cellular-SKU allowlist) so consumer
        /// apps still get an accurate answer.
        Function("isEsimSupported") { () -> Bool in
            guard #available(iOS 12.0, *) else {
                return false
            }
            let provisioning = CTCellularPlanProvisioning()
            if provisioning.supportsCellularPlan() {
                return true
            }
            return ExpoEsimUtilsModule.modelLikelySupportsEsim(
                ExpoEsimUtilsModule.deviceModelIdentifier()
            )
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

            let model = ExpoEsimUtilsModule.deviceModelIdentifier()
            result["deviceModel"] = model

            let provisioning = CTCellularPlanProvisioning()
            let ctSupported = provisioning.supportsCellularPlan()
            let modelSupported = ExpoEsimUtilsModule.modelLikelySupportsEsim(model)
            let supported = ctSupported || modelSupported
            result["isSupported"] = supported

            if ctSupported {
                result["reason"] = "Device supports eSIM via CoreTelephony"
            } else if modelSupported {
                result["reason"] = "Device model \(model) supports eSIM (CoreTelephony API requires carrier entitlement)"
            } else {
                result["reason"] = "Device hardware does not support eSIM"
            }

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
