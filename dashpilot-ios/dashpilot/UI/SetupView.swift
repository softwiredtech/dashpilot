import SwiftUI

private let dashGreen = Color(red: 0x5C / 255.0, green: 0xBD / 255.0, blue: 0x68 / 255.0)

struct SetupView: View {

    @Environment(ConnectionViewModel.self) var connectionVM

    @State private var serverAddress: String =
        UserDefaults.standard.string(forKey: "device_ip") ?? "192.168.1.105"

    var body: some View {
        ZStack {
            Color(red: 0x0D / 255.0, green: 0x0D / 255.0, blue: 0x0D / 255.0)
                .ignoresSafeArea()

            VStack(spacing: 0) {
                headerSection
                Spacer()
                ConnectionVisualization(connectionStatus: connectionVM.connectionStatus)
                Spacer()
                controlsSection
            }
            .padding(.horizontal, 32)
            .padding(.vertical, 48)
        }
        .navigationBarHidden(true)
    }

    // MARK: - Header

    private var headerSection: some View {
        VStack(spacing: 4) {
            Text("dashpilot")
                .foregroundColor(.white)
                .font(.system(size: 28, weight: .bold))
                .tracking(1)
            Text("Connect your WebSocket device")
                .foregroundColor(Color(white: 0.53))
                .font(.system(size: 14))
        }
    }

    // MARK: - Controls

    private var controlsSection: some View {
        VStack(spacing: 0) {
            TextField("Device IP", text: $serverAddress)
                .keyboardType(.default)
                .foregroundColor(connectionVM.connectionStatus == .disconnected ? .white : Color(white: 0.53))
                .disabled(connectionVM.connectionStatus != .disconnected)
                .padding(.horizontal, 14)
                .padding(.vertical, 12)
                .background(Color(white: 0.08))
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(Color(white: 0.2), lineWidth: 1)
                )
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .frame(maxWidth: .infinity * 0.7)
                .frame(width: UIScreen.main.bounds.width * 0.7)

            Spacer().frame(height: 16)

            switch connectionVM.connectionStatus {
            case .disconnected:
                connectButton
            case .connecting:
                cancelButton
                Spacer().frame(height: 12)
                nextButton(isConnecting: true)
            case .connected:
                disconnectButton
                Spacer().frame(height: 12)
                nextButton(isConnecting: false)
            }
        }
        .frame(maxWidth: .infinity)
    }

    private var connectButton: some View {
        Button {
            UserDefaults.standard.set(serverAddress, forKey: "device_ip")
            connectionVM.connect(serverAddress: serverAddress)
        } label: {
            Text("Connect")
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(.black)
                .frame(maxWidth: .infinity)
                .frame(height: 52)
        }
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .frame(width: UIScreen.main.bounds.width * 0.7)
    }

    private var cancelButton: some View {
        Button {
            connectionVM.disconnect()
        } label: {
            Text("Cancel")
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(Color(red: 1, green: 0.32, blue: 0.32))
                .frame(maxWidth: .infinity)
                .frame(height: 52)
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Color(red: 1, green: 0.32, blue: 0.32), lineWidth: 1)
                )
        }
        .frame(width: UIScreen.main.bounds.width * 0.7)
    }

    private var disconnectButton: some View {
        Button {
            connectionVM.disconnect()
        } label: {
            Text("Disconnect")
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(Color(red: 1, green: 0.32, blue: 0.32))
                .frame(maxWidth: .infinity)
                .frame(height: 52)
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Color(red: 1, green: 0.32, blue: 0.32), lineWidth: 1)
                )
        }
        .frame(width: UIScreen.main.bounds.width * 0.7)
    }

    private func nextButton(isConnecting: Bool) -> some View {
        NavigationLink(value: AppRoute.dashboardSelection) {
            Text(isConnecting ? "Connecting..." : "Next")
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(isConnecting ? Color(white: 0.67) : .black)
                .frame(maxWidth: .infinity)
                .frame(height: 52)
        }
        .background(isConnecting ? Color(white: 0.33) : Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .frame(width: UIScreen.main.bounds.width * 0.7)
        .disabled(isConnecting)
        .simultaneousGesture(TapGesture().onEnded {
            if !isConnecting {
                UserDefaults.standard.set(serverAddress, forKey: "device_ip")
            }
        })
    }
}

// MARK: - Connection Visualization

private struct ConnectionVisualization: View {

    let connectionStatus: ConnectionStatus

    @State private var dashPhase: CGFloat = 0

    private var isConnected: Bool { connectionStatus == .connected }
    private var showDashLine: Bool { connectionStatus != .disconnected }
    private var lineColor: Color { isConnected ? dashGreen : Color(white: 0.33) }
    private var borderColor: Color { isConnected ? dashGreen : .clear }

    var body: some View {
        VStack(spacing: 0) {
            circleIcon(systemName: "car.fill")
            dashedLine
            circleIcon(systemName: "iphone")
        }
        .onAppear {
            withAnimation(.linear(duration: 1).repeatForever(autoreverses: false)) {
                dashPhase = 20
            }
        }
    }

    private func circleIcon(systemName: String) -> some View {
        ZStack {
            Circle()
                .fill(Color(white: 0.1))
                .frame(width: 80, height: 80)
                .overlay(
                    Circle().stroke(borderColor, lineWidth: 2)
                )
            Image(systemName: systemName)
                .resizable()
                .scaledToFit()
                .frame(width: 36, height: 36)
                .foregroundColor(.white)
        }
    }

    private var dashedLine: some View {
        Canvas { context, size in
            var path = Path()
            path.move(to: CGPoint(x: size.width / 2, y: 0))
            path.addLine(to: CGPoint(x: size.width / 2, y: size.height))
            context.stroke(
                path,
                with: .color(showDashLine ? lineColor : .clear),
                style: StrokeStyle(
                    lineWidth: 3,
                    dash: [10, 10],
                    dashPhase: -dashPhase
                )
            )
        }
        .frame(width: 4, height: 64)
        .padding(.vertical, 4)
    }
}

