# ReTally

Scan a receipt, read every line item, and catch anything charged twice.

## What it does

1. Tap **Scan Receipt** → VisionKit's document camera opens, auto-detects the
   receipt's edges, and captures a perspective-corrected photo.
2. Vision OCR reads the receipt top to bottom; a heuristic parser extracts:
   - **Store name** (first prominent text line)
   - **Purchase date** (`NSDataDetector`)
   - **Payment info** (card network + masked last-4, or cash)
   - **Line items** — optional item number, name, and price
3. **Duplicate detection**: items with the same normalized name *and* price are
   flagged red — the classic "cashier scanned it twice" case.
4. On save:
   - Receipt + items persist locally via **SwiftData**
   - The receipt image is archived to the app's `Documents/Receipts` folder
     **and** to the user's **photo library** (add-only permission) for future use.

## Data model

| Model | Fields |
|---|---|
| `Receipt` | `storeName`, `purchaseDate`, `paymentInfo`, `imageFileName`, `createdAt`, `items` (cascade delete) |
| `ReceiptItem` | `itemNumber?`, `name`, `price` (Decimal), `isDuplicate`, `receipt` (inverse) |

## Architecture (MVVM)

Views are declarative and dumb; orchestration lives in `@Observable` ViewModels;
domain logic lives in stateless, unit-testable services.

```
ReTally/
├── ReTallyApp.swift              # @main, SwiftData container
├── Models/                       # Receipt, ReceiptItem (SwiftData)
├── ViewModels/
│   ├── HomeViewModel.swift       # scan pipeline: camera → OCR → parse → review; deletion
│   └── ScanReviewViewModel.swift # editable parsed state + save (SwiftData insert, image archival)
├── Services/
│   ├── ReceiptTextRecognizer.swift   # Vision OCR → ordered text lines
│   ├── ReceiptParser.swift           # store/date/payment/items + duplicate flagging
│   └── ReceiptImageStore.swift       # Documents/Receipts + photo library archival
└── Views/
    ├── HomeView.swift            # receipt list (@Query) + Scan button
    ├── DocumentScannerView.swift # VNDocumentCameraViewController wrapper
    ├── ScanReviewView.swift      # post-OCR review/edit, delegates to its ViewModel
    └── ReceiptDetailView.swift   # saved receipt with duplicate highlights
```

One deliberate deviation from textbook MVVM: the receipt *list* stays in
`HomeView` via `@Query`, because SwiftData's query property wrapper only works
inside SwiftUI views. The ViewModel owns everything else (scan flow state,
processing, errors, deletion).

## Running it

Requires **Xcode 16+** and **iOS 17+**.

1. Open `ReTally.xcodeproj`
2. Signing & Capabilities → select your development team
3. Run on a **physical device** — the document camera does not exist in the
   simulator (the app shows a friendly error there)
4. Permission prompts (camera, add-to-photos) appear on first use; the usage
   strings are set in build settings (`INFOPLIST_KEY_NSCameraUsageDescription`,
   `INFOPLIST_KEY_NSPhotoLibraryAddUsageDescription`)

## Notes & known limits

- Receipt formats vary wildly; the parser is heuristic. The review screen exists
  precisely so users can correct fields before saving. Accuracy improves a lot
  with flat, well-lit receipts (the document camera helps).
- Duplicate detection keys on *name + price*. Legitimate repeat purchases (two
  identical coffees) are flagged too — the UI wording says "possible duplicate"
  on purpose, and users can delete rows in review.
- Multi-page receipts: only the first captured page is parsed in v1.
- Next steps for production hardening: unit tests for `ReceiptParser` (it's
  pure and easily testable), quantity-line handling ("2 @ 3.99"), currency
  localization beyond the device default, and iCloud sync via SwiftData's
  CloudKit integration.
