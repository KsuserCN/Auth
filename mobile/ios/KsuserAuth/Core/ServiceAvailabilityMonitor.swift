import Foundation
import Observation

@MainActor @Observable final class ServiceAvailabilityMonitor {
    private(set) var isUnavailable = false

    func recordServiceFailure() { isUnavailable = true }

    func recordServiceResponse() {
        guard isUnavailable else { return }
        isUnavailable = false
    }
}
