import SwiftUI
import RiveRuntime

enum DevRiveFile {
    static var url: URL {
        URL.applicationSupportDirectory.appendingPathComponent("dev_rive.riv")
    }

    static func save(from picked: URL) throws {
        let accessing = picked.startAccessingSecurityScopedResource()
        defer { if accessing { picked.stopAccessingSecurityScopedResource() } }
        let data = try Data(contentsOf: picked)
        try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        try data.write(to: url, options: .atomic)
    }
}

struct RiveDashView: View {

    let source: URL?
    let incomingMessages: AsyncStream<DashState>

    @State private var binder: RiveDashBinder?
    @State private var failed = false

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            if let binder {
                binder.riveViewModel.view()
                    .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { height in
                        binder.fitHeight(height)
                    }
            } else if failed {
                Text("Could not load this Rive file")
                    .foregroundColor(.white)
            }
        }
        .task(id: source) {
            guard let source, let binder = RiveDashBinder(fileURL: source) else {
                failed = true
                return
            }
            self.binder = binder
            for await state in incomingMessages {
                binder.apply(state)
            }
        }
    }
}

@MainActor
final class RiveDashBinder {

    static let viewModelName = "DashViewModel"
    static let artboardHeight = 1080.0

    let riveViewModel: RiveViewModel
    private let instance: RiveDataBindingViewModel.Instance?
    private var numbers: [String: RiveDataBindingViewModel.Instance.NumberProperty] = [:]
    private var bools: [String: RiveDataBindingViewModel.Instance.BooleanProperty] = [:]
    private var strings: [String: RiveDataBindingViewModel.Instance.StringProperty] = [:]
    private var missing: Set<String> = []

    init?(fileURL: URL) {
        guard let data = try? Data(contentsOf: fileURL),
              let file = try? RiveFile(data: data, loadCdn: false) else { return nil }
        let model = RiveModel(riveFile: file)
        riveViewModel = RiveViewModel(model, stateMachineName: nil, fit: .layout)
        instance = file.viewModelNamed(Self.viewModelName)?.createDefaultInstance()
        if let instance {
            if let stateMachine = model.stateMachine {
                stateMachine.bind(viewModelInstance: instance)
            } else {
                model.artboard.bind(viewModelInstance: instance)
            }
        }
    }

    func fitHeight(_ points: CGFloat) {
        let screenScale = riveViewModel.riveView?.window?.screen.nativeScale ?? UITraitCollection.current.displayScale
        guard points > 0 else { return }
        riveViewModel.layoutScaleFactor = Double(points * screenScale) / Self.artboardHeight
    }

    func apply(_ state: DashState) {
        guard let instance else { return }
        for (name, value) in state.dashValues() where !missing.contains(name) {
            switch value {
            case .number(let v):
                guard let p = numbers[name] ?? instance.numberProperty(fromPath: name) else { missing.insert(name); continue }
                numbers[name] = p
                p.value = v
            case .bool(let v):
                guard let p = bools[name] ?? instance.booleanProperty(fromPath: name) else { missing.insert(name); continue }
                bools[name] = p
                p.value = v
            case .string(let v):
                guard let p = strings[name] ?? instance.stringProperty(fromPath: name) else { missing.insert(name); continue }
                strings[name] = p
                p.value = v
            }
        }
        if !riveViewModel.isPlaying {
            riveViewModel.play()
        }
    }
}
