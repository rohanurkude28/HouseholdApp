import SwiftUI
import FirebaseCore
import GoogleSignIn
import Speech
import Vision
import VisionKit
import PhotosUI
import AVFoundation
import ComposeApp

@main
struct iOSApp: App {
    init() {
        // Reads GoogleService-Info.plist (per developer, not committed). Started with
        // "-useEmulators", it joins the local emulators' project, the one Android's local build uses.
        if ProcessInfo.processInfo.arguments.contains("-useEmulators"),
           let path = Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist"),
           let options = FirebaseOptions(contentsOfFile: path) {
            options.projectID = "demo-houseaffairs"
            FirebaseApp.configure(options: options)
        } else {
            FirebaseApp.configure()
        }
        // "Taken ✓", "Done ✓"… on reminders; set before any can be pressed.
        ReminderButtons_iosKt.installReminderButtons()
        // The iOS OAuth client Firebase made for this app; its reversed form is the URL scheme in Info.plist.
        if let clientID = FirebaseApp.app()?.options.clientID {
            GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
        }
    }

    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea()
                // Google's sign-in page hands control back through this URL.
                .onOpenURL { GIDSignIn.sharedInstance.handle($0) }
        }
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(google: GoogleSignInSwift(), voice: SpeechSwift(), reader: ScannerSwift(), files: FilePickerSwift())
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

/** Google's sign-in sheet for the shared Kotlin app, which signs in to Firebase with the tokens. */
final class GoogleSignInSwift: GoogleSignInProvider {
    func signIn(onResult: @escaping (String?, String?, String?) -> Void) {
        guard let presenter = Self.topViewController() else {
            onResult(nil, nil, "Couldn't open Google sign-in. Try again.")
            return
        }
        GIDSignIn.sharedInstance.signIn(withPresenting: presenter) { result, error in
            if let error = error as NSError? {
                // Closing the sheet isn't an error worth showing.
                let cancelled = error.domain == kGIDSignInErrorDomain && error.code == GIDSignInError.canceled.rawValue
                onResult(nil, nil, cancelled ? nil : error.localizedDescription)
                return
            }
            guard let user = result?.user, let idToken = user.idToken?.tokenString else {
                onResult(nil, nil, "Google didn't send back a sign-in. Try again.")
                return
            }
            onResult(idToken, user.accessToken.tokenString, nil)
        }
    }

    func accessToken(scope: String, onResult: @escaping (String?, String?) -> Void) {
        func withUser(_ user: GIDGoogleUser) {
            if user.grantedScopes?.contains(scope) == true {
                user.refreshTokensIfNeeded { fresh, error in
                    onResult(fresh?.accessToken.tokenString, fresh == nil ? (error?.localizedDescription ?? "Google didn't answer. Try again.") : nil)
                }
                return
            }
            guard let presenter = Self.topViewController() else { return onResult(nil, "Couldn't ask Google. Try again.") }
            // Asked once; Google remembers it for this app.
            user.addScopes([scope], presenting: presenter) { result, error in
                if let error = error as NSError? {
                    let cancelled = error.domain == kGIDSignInErrorDomain && error.code == GIDSignInError.canceled.rawValue
                    return onResult(nil, cancelled ? nil : error.localizedDescription)
                }
                onResult(result?.user.accessToken.tokenString, nil)
            }
        }
        if let user = GIDSignIn.sharedInstance.currentUser { return withUser(user) }
        GIDSignIn.sharedInstance.restorePreviousSignIn { user, _ in
            if let user = user { withUser(user) } else { onResult(nil, "Sign in with Google to connect it.") }
        }
    }

    static func topViewControllerForScan() -> UIViewController? { topViewController() }

    private static func topViewController() -> UIViewController? {
        let window = UIApplication.shared.connectedScenes
            .compactMap { ($0 as? UIWindowScene)?.keyWindow }
            .first
        var top = window?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }
}

/**
 * Speech for the + box, with Apple's recogniser (Indian English first, the phone's language
 * otherwise). Words stream to Kotlin as they're heard; it stops after a short pause.
 */
final class SpeechSwift: SpeechProvider {
    private let engine = AVAudioEngine()
    private let recognizer = SFSpeechRecognizer(locale: Locale(identifier: "en-IN")) ?? SFSpeechRecognizer()
    private var request: SFSpeechAudioBufferRecognitionRequest?
    private var task: SFSpeechRecognitionTask?
    private var pause: Timer?
    private var onEnd: ((String?) -> Void)?

    func start(onText: @escaping (String) -> Void, onEnd: @escaping (String?) -> Void) {
        stop()
        self.onEnd = onEnd
        SFSpeechRecognizer.requestAuthorization { status in
            DispatchQueue.main.async {
                guard status == .authorized else { return self.finish("Allow Speech Recognition for HouseAffairs in Settings to speak.") }
                AVAudioSession.sharedInstance().requestRecordPermission { allowed in
                    DispatchQueue.main.async {
                        guard allowed else { return self.finish("Allow the microphone for HouseAffairs in Settings to speak.") }
                        do { try self.listen(onText) } catch { self.finish("Couldn't start listening. Try again.") }
                    }
                }
            }
        }
    }

    private func listen(_ onText: @escaping (String) -> Void) throws {
        guard let recognizer = recognizer, recognizer.isAvailable else { return finish("Speaking needs a connection right now. Type it instead.") }
        let session = AVAudioSession.sharedInstance()
        try session.setCategory(.record, mode: .measurement, options: .duckOthers)
        try session.setActive(true, options: .notifyOthersOnDeactivation)
        let request = SFSpeechAudioBufferRecognitionRequest()
        request.shouldReportPartialResults = true
        self.request = request
        let input = engine.inputNode
        input.installTap(onBus: 0, bufferSize: 1024, format: input.outputFormat(forBus: 0)) { buffer, _ in request.append(buffer) }
        engine.prepare()
        try engine.start()
        task = recognizer.recognitionTask(with: request) { [weak self] result, error in
            DispatchQueue.main.async {
                guard let self = self, self.onEnd != nil else { return }
                if let result = result {
                    onText(result.bestTranscription.formattedString)
                    // A short pause after speaking ends it, as Android's does.
                    self.pause?.invalidate()
                    self.pause = Timer.scheduledTimer(withTimeInterval: 1.8, repeats: false) { _ in self.finish(nil) }
                    if result.isFinal { self.finish(nil) }
                } else if error != nil {
                    self.finish(nil)
                }
            }
        }
    }

    func stop() { finish(nil) }

    /** Ends once: stops the microphone and tells Kotlin, with a message when something went wrong. */
    private func finish(_ problem: String?) {
        pause?.invalidate()
        pause = nil
        if engine.isRunning {
            engine.stop()
            engine.inputNode.removeTap(onBus: 0)
        }
        request?.endAudio()
        request = nil
        task?.finish()
        task = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        let done = onEnd
        onEnd = nil
        done?(problem)
    }
}

/**
 * Reads a bill, pack or label for the shared scan rules: Apple's document camera where the phone
 * has one, else a photo from the library (the simulator has no camera). Vision reads the text;
 * pieces on the same printed line are joined, so "Paneer 200g ... 90.00" stays one line.
 */
final class ScannerSwift: NSObject, ScanProvider, VNDocumentCameraViewControllerDelegate, PHPickerViewControllerDelegate {
    private var onText: ((String?) -> Void)?

    func scan(onText: @escaping (String?) -> Void) {
        self.onText = onText
        // Test runs: "-scanFile /path/bill.png" (with -useEmulators) reads that picture instead of asking.
        let args = ProcessInfo.processInfo.arguments
        if args.contains("-useEmulators"), let i = args.firstIndex(of: "-scanFile"), i + 1 < args.count {
            return read([UIImage(contentsOfFile: args[i + 1])?.cgImage].compactMap { $0 })
        }
        guard let top = GoogleSignInSwift.topViewControllerForScan() else { return done(nil) }
        if VNDocumentCameraViewController.isSupported {
            let camera = VNDocumentCameraViewController()
            camera.delegate = self
            top.present(camera, animated: true)
        } else {
            var config = PHPickerConfiguration()
            config.filter = .images
            config.selectionLimit = 1
            let picker = PHPickerViewController(configuration: config)
            picker.delegate = self
            top.present(picker, animated: true)
        }
    }

    func documentCameraViewController(_ controller: VNDocumentCameraViewController, didFinishWith scan: VNDocumentCameraScan) {
        let pages = (0..<scan.pageCount).compactMap { scan.imageOfPage(at: $0).cgImage }
        controller.dismiss(animated: true) { self.read(pages) }
    }

    func documentCameraViewControllerDidCancel(_ controller: VNDocumentCameraViewController) {
        controller.dismiss(animated: true) { self.done(nil) }
    }

    func documentCameraViewController(_ controller: VNDocumentCameraViewController, didFailWithError error: Error) {
        controller.dismiss(animated: true) { self.done(nil) }
    }

    func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
        picker.dismiss(animated: true)
        guard let item = results.first?.itemProvider, item.canLoadObject(ofClass: UIImage.self) else { return done(nil) }
        item.loadObject(ofClass: UIImage.self) { image, _ in
            let cg = (image as? UIImage)?.cgImage
            self.read(cg.map { [$0] } ?? [])
        }
    }

    private func read(_ pages: [CGImage]) {
        guard !pages.isEmpty else { return done(nil) }
        DispatchQueue.global(qos: .userInitiated).async {
            let text = pages.map(Self.text(of:)).joined(separator: "\n")
            self.done(text)
        }
    }

    /** One page's text, a printed line per line: Vision's pieces grouped by height, left to right. */
    static func text(of image: CGImage) -> String {
        let request = VNRecognizeTextRequest()
        request.recognitionLevel = .accurate
        request.usesLanguageCorrection = true
        try? VNImageRequestHandler(cgImage: image, options: [:]).perform([request])
        let pieces = (request.results ?? []).compactMap { o -> (box: CGRect, text: String)? in
            guard let best = o.topCandidates(1).first else { return nil }
            return (o.boundingBox, best.string)
        }.sorted { $0.box.midY > $1.box.midY }
        var lines: [[(box: CGRect, text: String)]] = []
        for p in pieces {
            if let last = lines.last?.first, abs(last.box.midY - p.box.midY) < max(last.box.height, p.box.height) * 0.5 {
                lines[lines.count - 1].append(p)
            } else {
                lines.append([p])
            }
        }
        return lines.map { $0.sorted { $0.box.minX < $1.box.minX }.map(\.text).joined(separator: " ") }.joined(separator: "\n")
    }

    private func done(_ text: String?) {
        DispatchQueue.main.async {
            let callback = self.onText
            self.onText = nil
            callback?(text)
        }
    }
}

/** The Files app's picker for "save to Drive": hands Kotlin a readable copy's path and the file's name. */
final class FilePickerSwift: NSObject, FilePickProvider, UIDocumentPickerDelegate {
    private var onFile: ((String?, String?) -> Void)?

    func pick(onFile: @escaping (String?, String?) -> Void) {
        self.onFile = onFile
        // Test runs: "-pickFile /path/passport.pdf" (with -useEmulators) picks that file without the picker.
        let args = ProcessInfo.processInfo.arguments
        if args.contains("-useEmulators"), let i = args.firstIndex(of: "-pickFile"), i + 1 < args.count {
            return done(URL(fileURLWithPath: args[i + 1]))
        }
        guard let top = GoogleSignInSwift.topViewControllerForScan() else { return done(nil) }
        // A copy, so it can be read after the picker closes.
        let picker = UIDocumentPickerViewController(forOpeningContentTypes: [.item], asCopy: true)
        picker.delegate = self
        top.present(picker, animated: true)
    }

    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) { done(urls.first) }

    func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) { done(nil) }

    private func done(_ url: URL?) {
        let callback = onFile
        onFile = nil
        callback?(url?.path, url?.lastPathComponent)
    }
}
