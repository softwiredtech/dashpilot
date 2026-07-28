import Foundation

/// Minimal Semantic Versioning (MAJOR.MINOR.PATCH) helper used to decide
/// whether a firmware release advertised in the update manifest is newer than
/// what the DashKit currently reports over BLE (port of the Android `SemVer`).
enum SemVer {

    /// Parse a version string into [major, minor, patch]. Tolerates a leading
    /// "v" and any pre-release / build metadata suffix (e.g. "1.2.3-rc1+build").
    /// Returns nil if the core version cannot be parsed.
    static func parse(_ version: String?) -> [Int]? {
        guard var core = version?.trimmingCharacters(in: .whitespaces), !core.isEmpty else { return nil }
        if core.hasPrefix("v") || core.hasPrefix("V") { core.removeFirst() }
        // Drop pre-release/build metadata.
        core = core.components(separatedBy: "-")[0].components(separatedBy: "+")[0]
            .trimmingCharacters(in: .whitespaces)
        let parts = core.split(separator: ".", omittingEmptySubsequences: false)
        // Require at least a valid major to consider it a version.
        guard let first = parts.first, Int(first) != nil else { return nil }
        return (0..<3).map { index in
            index < parts.count ? (Int(parts[index]) ?? 0) : 0
        }
    }

    /// Compare two versions. Negative if a < b, zero if equal, positive if
    /// a > b, or nil if either cannot be parsed.
    static func compare(_ a: String?, _ b: String?) -> Int? {
        guard let pa = parse(a), let pb = parse(b) else { return nil }
        for i in 0..<3 where pa[i] != pb[i] {
            return pa[i] - pb[i]
        }
        return 0
    }

    /// True if `candidate` is strictly newer than `current`.
    static func isNewer(_ candidate: String?, than current: String?) -> Bool {
        guard let c = compare(candidate, current) else { return false }
        return c > 0
    }
}
