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

    @Environment(\.colorScheme) private var colorScheme
    @State private var binder: RiveDashBinder?
    @State private var failed = false

    var body: some View {
        ZStack {
            (colorScheme == .dark ? SwiftUI.Color.black : SwiftUI.Color.white).ignoresSafeArea()
            if let binder {
                RiveUIViewRepresentable(rive: binder.rive)
                    .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { height in
                        binder.fitHeight(height)
                    }
            } else if failed {
                Text("Could not load this Rive file")
                    .foregroundColor(.primary)
            }
        }
        .task(id: source) {
            guard let source, let binder = await RiveDashBinder.load(fileURL: source) else {
                failed = true
                return
            }
            binder.setSystemDarkMode(colorScheme == .dark)
            self.binder = binder
            for await state in incomingMessages {
                binder.apply(state)
            }
        }
        .onChange(of: colorScheme) { _, scheme in
            binder?.setSystemDarkMode(scheme == .dark)
        }
    }
}

@MainActor
final class RiveDashBinder {

    static let viewModelName = "DashViewModel"
    static let artboardHeight = 1080.0

    // A worker's rendering mode is fixed for its lifetime, so keep one per mode.
    private static var workers: [Bool: Worker] = [:]

    let rive: Rive
    private let instance: ViewModelInstance?
    private let types: [String: ViewModelProperty.DataType]
    private var systemDarkMode = false

    private init(rive: Rive, instance: ViewModelInstance?, types: [String: ViewModelProperty.DataType]) {
        self.rive = rive
        self.instance = instance
        self.types = types
    }

    private static func worker(gpuCanvas: Bool) async throws -> Worker {
        if let worker = workers[gpuCanvas] { return worker }
        let worker = try await Worker(configuration: .init(enableGPUCanvas: gpuCanvas))
        workers[gpuCanvas] = worker
        return worker
    }

    static func load(fileURL: URL) async -> RiveDashBinder? {
        do {
            let data = try Data(contentsOf: fileURL)
            let worker = try await worker(gpuCanvas: DisplaySettings.devRiveGpuCanvas)
            let file = try await File(source: .data(data), worker: worker)
            let artboard = try await file.createArtboard()
            var instance: ViewModelInstance?
            var types: [String: ViewModelProperty.DataType] = [:]
            if try await file.getViewModelNames().contains(viewModelName) {
                instance = try await file.createViewModelInstance(.viewModelDefault(from: .name(viewModelName)))
                for property in try await file.getProperties(of: viewModelName) {
                    types[property.name] = property.type
                }
            }
            let stateMachine = try await artboard.createStateMachine(nil, binding: instance)
            let rive = try await Rive(file: file, artboard: artboard, stateMachine: stateMachine,
                                      fit: .layout(scaleFactor: .automatic))
            return RiveDashBinder(rive: rive, instance: instance, types: types)
        } catch {
            print("[DashPilot] Rive load failed: \(error)")
            return nil
        }
    }

    func fitHeight(_ points: CGFloat) {
        guard points > 0 else { return }
        let screenScale = UIApplication.shared.connectedScenes
            .compactMap { ($0 as? UIWindowScene)?.screen.nativeScale }.first
            ?? UITraitCollection.current.displayScale
        rive.fit = .layout(scaleFactor: .explicit(Float(Double(points * screenScale) / Self.artboardHeight)))
    }

    func setSystemDarkMode(_ darkMode: Bool) {
        systemDarkMode = darkMode
        // Appearance must update even when no vehicle telemetry is arriving.
        guard let instance, types["darkMode"] == .boolean else { return }
        instance.setValue(of: BoolProperty(path: "darkMode"), to: darkMode)
    }

    func apply(_ state: DashState) {
        guard let instance else { return }
        for (name, value) in state.dashValues(systemDarkMode: systemDarkMode) {
            switch (value, types[name]) {
            case (.number(let v), .number):
                instance.setValue(of: NumberProperty(path: name), to: v)
            case (.bool(let v), .boolean):
                instance.setValue(of: BoolProperty(path: name), to: v)
            case (.string(let v), .string):
                instance.setValue(of: StringProperty(path: name), to: v)
            case (.color(let rgb), .color):
                instance.setValue(of: ColorProperty(path: name), to: RiveRuntime.Color(0xFF00_0000 | rgb))
            default:
                break
            }
        }
    }
}
