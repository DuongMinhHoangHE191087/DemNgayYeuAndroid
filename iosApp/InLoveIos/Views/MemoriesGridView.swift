import InLoveIosCore
import PhotosUI
import SwiftUI

public struct MemoriesGridView: View {
    @EnvironmentObject private var env: AppEnvironment
    @State private var favoritesOnly = false
    @State private var showAddSheet = false
    @State private var showPrivacyDetails = false
    @State private var selectedItem: LocalMemoryItem?
    @State private var pickedPhotoItem: PhotosPickerItem?

    public init() {}

    private var displayedMemories: [LocalMemoryItem] {
        favoritesOnly ? env.memories.filter(\.isFavorite) : env.memories
    }

    public var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: InLoveMetrics.sectionGap) {
                    filterRow
                    privacyNoticeCard
                    if displayedMemories.isEmpty {
                        emptyStateCard
                    } else {
                        LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: 12) {
                            ForEach(displayedMemories) { item in
                                memoryCard(item)
                            }
                        }
                    }
                }
                .padding(.horizontal, InLoveMetrics.screenMargin)
                .padding(.vertical, 12)
            }
            .background(InLoveColors.softBlush.ignoresSafeArea())
            .navigationTitle(env.isEnglish ? "Our Memories" : "Khoảnh Khắc Đôi Ta")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        showAddSheet = true
                    } label: {
                        Label(env.isEnglish ? "Add" : "Thêm", systemImage: "plus")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(InLoveColors.rose)
                    }
                    .inLoveTapTarget()
                }
            }
            .sheet(isPresented: $showAddSheet) {
                AddMemorySheet(pickedPhotoItem: $pickedPhotoItem)
                    .environmentObject(env)
            }
            .sheet(item: $selectedItem) { item in
                MemoryDetailSheet(item: item)
                    .environmentObject(env)
            }
        }
    }

    private var filterRow: some View {
        HStack(spacing: 8) {
            filterChip(
                title: env.isEnglish ? "All (\(env.memories.count))" : "Tất cả (\(env.memories.count))",
                active: !favoritesOnly
            ) {
                favoritesOnly = false
            }
            filterChip(title: env.isEnglish ? "Favorites" : "Yêu thích", active: favoritesOnly) {
                favoritesOnly = true
            }
        }
    }

    private func filterChip(title: String, active: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.subheadline.weight(.medium))
                .foregroundStyle(active ? InLoveColors.pearl : InLoveColors.deepPlum)
                .frame(maxWidth: .infinity, minHeight: InLoveMetrics.minTapTarget)
                .background(Capsule().fill(active ? InLoveColors.rose : InLoveColors.pearl))
                .overlay(Capsule().stroke(InLoveColors.cardBorder, lineWidth: active ? 0 : 1.0))
        }
        .inLoveTapTarget()
    }

    private var privacyNoticeCard: some View {
        DisclosureGroup(isExpanded: $showPrivacyDetails) {
            Text(
                env.isEnglish
                    ? "Photos and diary notes stay locally on this phone and are never uploaded or read without your consent."
                    : "Ảnh và nhật ký kỷ niệm được giữ an toàn ngay trên điện thoại này, không tải lên máy chủ ngoài ý muốn."
            )
            .font(.caption)
            .foregroundStyle(InLoveColors.subtlePlum)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.top, 6)
        } label: {
            Label(
                env.isEnglish ? "Private memories stay on device" : "Kỷ niệm riêng tư lưu trên máy",
                systemImage: "lock.shield"
            )
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(InLoveColors.rose)
            .frame(minHeight: InLoveMetrics.minTapTarget, alignment: .leading)
        }
        .tint(InLoveColors.rose)
        .inLoveCard(background: InLoveColors.surfaceContainerLow)
    }

    private var emptyStateCard: some View {
        VStack(spacing: 12) {
            Image(systemName: "photo.on.rectangle")
                .font(.title)
                .foregroundStyle(InLoveColors.rose)
            Text(
                favoritesOnly
                    ? (env.isEnglish ? "No favorite memories marked yet" : "Chưa có kỷ niệm nào được đánh dấu yêu thích")
                    : (env.isEnglish ? "No memories saved yet" : "Album kỷ niệm chưa có mục nào")
            )
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(InLoveColors.deepPlum)
            Button(env.isEnglish ? "Save first memory" : "Lưu kỷ niệm đầu tiên") {
                showAddSheet = true
            }
            .buttonStyle(InLovePrimaryButtonStyle())
            .inLoveTapTarget()
        }
        .frame(maxWidth: .infinity)
        .inLoveCard()
    }

    private func memoryCard(_ item: LocalMemoryItem) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Image(systemName: "photo")
                    .font(.body)
                    .foregroundStyle(InLoveColors.subtlePlum)
                Spacer()
                Button {
                    env.toggleFavoriteMemory(id: item.id)
                } label: {
                    Image(systemName: item.isFavorite ? "heart.fill" : "heart")
                        .foregroundStyle(InLoveColors.rose)
                }
                .inLoveTapTarget()
                .accessibilityLabel(env.isEnglish ? "Toggle favorite" : "Đánh dấu yêu thích")
            }
            Button {
                selectedItem = item
            } label: {
                VStack(alignment: .leading, spacing: 6) {
                    Text(item.title)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(InLoveColors.deepPlum)
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)
                    Text(item.dateText)
                        .font(.caption)
                        .foregroundStyle(InLoveColors.subtlePlum)
                    if !item.location.isEmpty {
                        Text(item.location)
                            .font(.caption2)
                            .foregroundStyle(InLoveColors.subtlePlum)
                            .lineLimit(1)
                    }
                }
                .frame(maxWidth: .infinity, minHeight: 68, alignment: .topLeading)
            }
            .buttonStyle(.plain)
        }
        .frame(maxWidth: .infinity, minHeight: 132, alignment: .topLeading)
        .inLoveCard()
    }
}

private struct AddMemorySheet: View {
    @EnvironmentObject private var env: AppEnvironment
    @Environment(\.dismiss) private var dismiss
    @Binding var pickedPhotoItem: PhotosPickerItem?

    @State private var title = ""
    @State private var dateText = "18/11/2026"
    @State private var location = ""
    @State private var note = ""

    var body: some View {
        NavigationStack {
            Form {
                Section(env.isEnglish ? "Memory details" : "Chi tiết kỷ niệm") {
                    TextField(env.isEnglish ? "Memory title" : "Tên kỷ niệm", text: $title)
                        .frame(minHeight: InLoveMetrics.minTapTarget)
                    TextField(env.isEnglish ? "Date (dd/MM/yyyy)" : "Ngày (dd/MM/yyyy)", text: $dateText)
                        .frame(minHeight: InLoveMetrics.minTapTarget)
                    TextField(env.isEnglish ? "Location" : "Địa điểm", text: $location)
                        .frame(minHeight: InLoveMetrics.minTapTarget)
                    TextField(env.isEnglish ? "Diary note" : "Cảm xúc & ghi chú", text: $note, axis: .vertical)
                        .frame(minHeight: InLoveMetrics.minTapTarget)
                }
                Section {
                    PhotosPicker(
                        selection: $pickedPhotoItem,
                        matching: .images
                    ) {
                        Label(
                            env.isEnglish ? "Choose photo from library" : "Chọn ảnh từ thư viện",
                            systemImage: "photo.on.rectangle"
                        )
                        .frame(minHeight: InLoveMetrics.minTapTarget)
                    }
                }
            }
            .navigationTitle(env.isEnglish ? "Add Memory" : "Lưu Kỷ Niệm")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(env.isEnglish ? "Cancel" : "Hủy") { dismiss() }
                        .inLoveTapTarget()
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button(env.isEnglish ? "Save" : "Lưu") {
                        env.addMemory(title: title, dateText: dateText, location: location, note: note)
                        dismiss()
                    }
                    .disabled(title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    .inLoveTapTarget()
                }
            }
            .onAppear { env.adManager.enterSacredZone(.memoryCaptureFlow) }
            .onDisappear { env.adManager.exitSacredZone(.memoryCaptureFlow) }
        }
    }
}

private struct MemoryDetailSheet: View {
    @EnvironmentObject private var env: AppEnvironment
    @Environment(\.dismiss) private var dismiss
    let item: LocalMemoryItem

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 12) {
                Text(item.title)
                    .font(.title2.weight(.semibold))
                    .foregroundStyle(InLoveColors.deepPlum)
                Text("\(item.dateText)\(item.location.isEmpty ? "" : " • \(item.location)")")
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(InLoveColors.rose)
                Text(item.note.isEmpty ? (env.isEnglish ? "Saved memory moment." : "Khoảnh khắc đã lưu trong album.") : item.note)
                    .font(.body)
                    .foregroundStyle(InLoveColors.deepPlum)
                Spacer()
                Button(role: .destructive) {
                    env.deleteMemory(id: item.id)
                    dismiss()
                } label: {
                    Label(env.isEnglish ? "Delete memory" : "Xóa kỷ niệm này", systemImage: "trash")
                        .frame(maxWidth: .infinity, minHeight: InLoveMetrics.minTapTarget)
                }
                .buttonStyle(InLoveSecondaryButtonStyle())
                .inLoveTapTarget()
            }
            .padding(16)
            .background(InLoveColors.softBlush.ignoresSafeArea())
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(env.isEnglish ? "Done" : "Xong") { dismiss() }
                        .inLoveTapTarget()
                }
            }
        }
    }
}
