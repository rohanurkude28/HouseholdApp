package com.houseaffairs.ios

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.writeToURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

/** Writes the files to a temporary folder and opens iOS's share sheet, so the person picks where they go. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual fun shareFiles(files: List<Pair<String, String>>) {
    val urls = files.map { (name, text) ->
        val url = NSURL.fileURLWithPath(NSTemporaryDirectory() + name)
        NSString.create(string = text).writeToURL(url, atomically = true, encoding = NSUTF8StringEncoding, error = null)
        url
    }
    var top = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (top?.presentedViewController != null) top = top.presentedViewController
    top?.presentViewController(UIActivityViewController(activityItems = urls, applicationActivities = null), animated = true, completion = null)
}
