package com.houseaffairs.ios

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/** The Swift app hosts this; FirebaseApp.configure() runs first in the app's init. */
fun MainViewController(): UIViewController = ComposeUIViewController { App() }
