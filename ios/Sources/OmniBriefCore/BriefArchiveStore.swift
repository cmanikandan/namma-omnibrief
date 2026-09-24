import Foundation

/// On-device FIFO archive storing up to 10 recent briefs (`maxLimit = 10`).
///
/// Mirrors Android's `BriefItemDao.insertWithRollover(item, maxLimit = 10)` and supports in-place
/// source retargeting when the user overrides a misdetected publication masthead.
public final class BriefArchiveStore: @unchecked Sendable {

    public static let maxItems = 10
    private let fileURL: URL
    private let lock = NSLock()

    public init(fileURL: URL? = nil) {
        if let fileURL = fileURL {
            self.fileURL = fileURL
        } else {
            let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first
                ?? URL(fileURLWithPath: NSTemporaryDirectory())
            self.fileURL = docs.appendingPathComponent("omnibrief_archive.json")
        }
    }

    public func loadAll() -> [BriefItem] {
        lock.lock()
        defer { lock.unlock() }
        return readInternal()
    }

    /// Inserts a new brief at the head of the list and trims oldest entries beyond `maxLimit`.
    @discardableResult
    public func saveWithRollover(_ item: BriefItem, maxLimit: Int = BriefArchiveStore.maxItems) -> UUID {
        lock.lock()
        defer { lock.unlock() }
        var items = readInternal()
        items.removeAll { $0.id == item.id }
        items.insert(item, at: 0)
        if items.count > maxLimit {
            items = Array(items.prefix(maxLimit))
        }
        writeInternal(items)
        return item.id
    }

    /// Rewrites an archived brief in-place when the user changes the publication source chip.
    @discardableResult
    public func retargetBrief(id: UUID, newSource: String) -> BriefItem? {
        lock.lock()
        defer { lock.unlock() }
        var items = readInternal()
        guard let idx = items.firstIndex(where: { $0.id == id }) else { return nil }
        var existing = items[idx]
        let rewritten = SourceRetargeter.retargetSource(
            text: existing.content,
            oldSource: existing.sourceOrSpeaker,
            newSource: newSource
        )
        existing.content = rewritten
        existing.sourceOrSpeaker = newSource
        items[idx] = existing
        writeInternal(items)
        return existing
    }

    public func delete(id: UUID) {
        lock.lock()
        defer { lock.unlock() }
        var items = readInternal()
        items.removeAll { $0.id == id }
        writeInternal(items)
    }

    public func clearAll() {
        lock.lock()
        defer { lock.unlock() }
        writeInternal([])
    }

    private func readInternal() -> [BriefItem] {
        guard let data = try? Data(contentsOf: fileURL) else { return [] }
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return (try? decoder.decode([BriefItem].self, from: data)) ?? []
    }

    private func writeInternal(_ items: [BriefItem]) {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        if let data = try? encoder.encode(items) {
            try? data.write(to: fileURL, options: .atomic)
        }
    }
}
