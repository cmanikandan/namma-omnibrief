import Foundation

/// Pure text transformation utilities for Article -> X drafts and archived History items.
///
/// Mirrors `MainViewModel.retargetSource` and `MainViewModel.splitArchivedDraft` from Android so
/// the exact same regression test suite runs on iOS.
public enum SourceRetargeter {

    public static let archiveSeparator = "\n\n---\n\n"

    /// Rewrites a finished draft so it attributes `newSource` instead of `oldSource`.
    ///
    /// Two passes:
    /// 1. Every mention of `oldSource` is replaced case-insensitively. The bare form without a
    ///    leading "The " is handled too, longest variant first so "The" is never orphaned.
    /// 2. Any line-anchored `Source:` line is rewritten outright. That covers the case where the
    ///    old name is unknown or blank.
    public static func retargetSource(text: String, oldSource: String, newSource: String) -> String {
        let trimmedNew = newSource.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmedNew.isEmpty || text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return text
        }

        if oldSource.caseInsensitiveCompare(trimmedNew) == .orderedSame {
            return replaceSourceLine(in: text, with: trimmedNew)
        }

        var result = text
        let strippedThe: String = {
            if oldSource.hasPrefix("The ") || oldSource.hasPrefix("the ") {
                return String(oldSource.dropFirst(4))
            }
            return oldSource
        }()

        var candidates: [String] = []
        for c in [oldSource, strippedThe] {
            let trimmed = c.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !trimmed.isEmpty,
                  trimmed.caseInsensitiveCompare(trimmedNew) != .orderedSame,
                  !candidates.contains(where: { $0.caseInsensitiveCompare(trimmed) == .orderedSame })
            else { continue }
            candidates.append(trimmed)
        }
        candidates.sort { $0.count > $1.count }

        for variant in candidates {
            let escaped = NSRegularExpression.escapedPattern(for: variant)
            if let regex = try? NSRegularExpression(pattern: escaped, options: [.caseInsensitive]) {
                let range = NSRange(result.startIndex..<result.endIndex, in: result)
                let template = NSRegularExpression.escapedTemplate(for: trimmedNew)
                result = regex.stringByReplacingMatches(in: result, options: [], range: range, withTemplate: template)
            }
        }

        return replaceSourceLine(in: result, with: trimmedNew)
    }

    private static func replaceSourceLine(in text: String, with newSource: String) -> String {
        guard let regex = try? NSRegularExpression(pattern: "(?m)^([ \\t]*Source:[ \\t]*).*$", options: []) else {
            return text
        }
        let range = NSRange(text.startIndex..<text.endIndex, in: text)
        let escapedSource = NSRegularExpression.escapedTemplate(for: newSource)
        return regex.stringByReplacingMatches(in: text, options: [], range: range, withTemplate: "$1\(escapedSource)")
    }

    /// Splits a stored History entry back into individual post texts, stripping `[Image 1] ` labels.
    public static func splitArchivedDraft(_ content: String) -> [String] {
        let rawParts = content.components(separatedBy: archiveSeparator)
        let labelRegex = try? NSRegularExpression(pattern: "^\\[[^\\]\\n]{1,40}\\]\\s*", options: [])

        return rawParts.compactMap { part in
            var cleaned = part
            if let regex = labelRegex {
                let range = NSRange(cleaned.startIndex..<cleaned.endIndex, in: cleaned)
                cleaned = regex.stringByReplacingMatches(in: cleaned, options: [], range: range, withTemplate: "")
            }
            let trimmed = cleaned.trimmingCharacters(in: .whitespacesAndNewlines)
            return trimmed.isEmpty ? nil : trimmed
        }
    }

    /// Joins multiple draft items into the archive storage string.
    public static func joinDraftsForArchive(_ drafts: [XPostDraftItem]) -> String {
        if drafts.count == 1 {
            return drafts[0].text
        }
        return drafts.map { "[\($0.label)] \($0.text)" }.joined(separator: archiveSeparator)
    }
}
