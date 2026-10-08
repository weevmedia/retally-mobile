import Foundation

/// The result of parsing OCR lines from a receipt, before the user confirms and saves.
struct ParsedReceipt {
    var storeName: String
    var purchaseDate: Date?
    var paymentInfo: String
    var items: [ParsedItem]

    var duplicateCount: Int { items.filter(\.isDuplicate).count }
}

struct ParsedItem: Identifiable {
    let id = UUID()
    var itemNumber: String?
    var name: String
    var price: Decimal
    var isDuplicate: Bool = false
}

/// Heuristic parser for OCR'd receipt lines: extracts the store name, purchase date,
/// payment info, and priced line items, then flags likely duplicate scans.
enum ReceiptParser {

    /// Lines containing these words are receipt plumbing, not purchasable items.
    private static let nonItemKeywords: [String] = [
        "TOTAL", "SUBTOTAL", "SUB-TOTAL", "TAX", "CHANGE", "TENDER", "TEND",
        "BALANCE", "PAYMENT", "AMOUNT", "CASHBACK", "CASH BACK", "REFUND",
        "SAVINGS", "DISCOUNT", "COUPON", "LOYALTY", "POINTS", "REWARD",
        "VISA", "MASTERCARD", "MASTER CARD", "AMEX", "AMERICAN EXPRESS",
        "DISCOVER", "DEBIT", "CREDIT", "APPROVED", "AUTH", "ACCOUNT",
        "ITEMS SOLD", "QTY", "INVOICE", "MEMBER", "THANK",
    ]

    private static let paymentPattern =
        #"(?i)(VISA|MASTER\s?CARD|AMEX|AMERICAN EXPRESS|DISCOVER|DEBIT|CREDIT|CASH|APPLE PAY|GIFT CARD)"# +
        #"(?:[^\d]*((?:\*|X|x){2,}\s?\d{2,4}|\d{4}))?"#

    /// Matches "optional item number, item name, price at end of line".
    /// Tolerates a leading currency symbol and a trailing single tax-code letter
    /// (e.g. Walmart's " X" / " N" / " T" suffixes).
    private static let itemPattern =
        #"^(?:(\d{4,})\s+)?(.+?)\s+\$?(-?\d{1,4}[.,]\d{2})(?:\s?[A-Za-z*])?$"#

    // MARK: - Public API

    static func parse(lines: [String]) -> ParsedReceipt {
        var receipt = ParsedReceipt(
            storeName: detectStoreName(lines: lines),
            purchaseDate: detectDate(lines: lines),
            paymentInfo: detectPaymentInfo(lines: lines),
            items: detectItems(lines: lines)
        )
        receipt.items = flagDuplicates(receipt.items)
        return receipt
    }

    // MARK: - Store name

    private static func detectStoreName(lines: [String]) -> String {
        // The store name is almost always the first prominent text line:
        // skip lines that are mostly digits, too short, or look like a date/phone.
        for line in lines.prefix(5) {
            let letters = line.filter(\.isLetter).count
            if letters >= 3, letters >= line.count / 2 {
                return line
            }
        }
        return lines.first ?? "Unknown Store"
    }

    // MARK: - Date

    private static func detectDate(lines: [String]) -> Date? {
        guard
            let detector = try? NSDataDetector(
                types: NSTextCheckingResult.CheckingType.date.rawValue
            )
        else { return nil }

        let text = lines.joined(separator: "\n")
        let range = NSRange(text.startIndex..., in: text)
        return detector
            .matches(in: text, options: [], range: range)
            .compactMap(\.date)
            // Receipts are printed in the past; ignore detector artifacts in the future.
            .first { $0 <= .now.addingTimeInterval(24 * 60 * 60) }
    }

    // MARK: - Payment info

    private static func detectPaymentInfo(lines: [String]) -> String {
        guard let regex = try? NSRegularExpression(pattern: paymentPattern) else { return "" }

        for line in lines {
            let range = NSRange(line.startIndex..., in: line)
            guard let match = regex.firstMatch(in: line, options: [], range: range) else { continue }

            var parts: [String] = []
            if let methodRange = Range(match.range(at: 1), in: line) {
                parts.append(String(line[methodRange]).capitalized)
            }
            if match.numberOfRanges > 2, let digitsRange = Range(match.range(at: 2), in: line) {
                let digits = String(line[digitsRange]).filter(\.isNumber)
                if !digits.isEmpty { parts.append("•••• \(digits.suffix(4))") }
            }
            if !parts.isEmpty { return parts.joined(separator: " ") }
        }
        return ""
    }

    // MARK: - Line items

    private static func detectItems(lines: [String]) -> [ParsedItem] {
        guard let regex = try? NSRegularExpression(pattern: itemPattern) else { return [] }

        var items: [ParsedItem] = []
        for line in lines {
            let upper = line.uppercased()
            if nonItemKeywords.contains(where: upper.contains) { continue }

            let range = NSRange(line.startIndex..., in: line)
            guard let match = regex.firstMatch(in: line, options: [], range: range) else { continue }

            let itemNumber = Range(match.range(at: 1), in: line).map { String(line[$0]) }
            guard
                let nameRange = Range(match.range(at: 2), in: line),
                let priceRange = Range(match.range(at: 3), in: line)
            else { continue }

            let name = String(line[nameRange]).trimmingCharacters(in: .whitespaces)
            let priceString = String(line[priceRange]).replacingOccurrences(of: ",", with: ".")
            guard let price = Decimal(string: priceString), !name.isEmpty else { continue }
            // A "name" that is only digits is usually a barcode fragment, not an item.
            guard name.contains(where: \.isLetter) else { continue }

            items.append(ParsedItem(itemNumber: itemNumber, name: name, price: price))
        }
        return items
    }

    // MARK: - Duplicate detection

    static func flagDuplicates(_ items: [ParsedItem]) -> [ParsedItem] {
        var counts: [String: Int] = [:]
        for item in items {
            counts[duplicateKey(for: item), default: 0] += 1
        }
        return items.map { item in
            var copy = item
            copy.isDuplicate = (counts[duplicateKey(for: item)] ?? 0) > 1
            return copy
        }
    }

    private static func duplicateKey(for item: ParsedItem) -> String {
        let normalized = item.name
            .lowercased()
            .replacingOccurrences(of: "[^a-z0-9]", with: "", options: .regularExpression)
        return "\(normalized)|\(item.price)"
    }
}
