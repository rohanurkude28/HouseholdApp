import SwiftUI
import FirebaseCore
import GoogleSignIn
import ComposeApp

@main
struct iOSApp: App {
    init() {
        // Reads GoogleService-Info.plist (per developer, not committed).
        FirebaseApp.configure()
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
        MainViewControllerKt.MainViewController(google: GoogleSignInSwift())
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

    private static func topViewController() -> UIViewController? {
        let window = UIApplication.shared.connectedScenes
            .compactMap { ($0 as? UIWindowScene)?.keyWindow }
            .first
        var top = window?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }
}
