@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package org.freevia.rubikbuddy.ui

import kotlinx.cinterop.*
import platform.QuartzCore.CACurrentMediaTime
import org.freevia.rubikbuddy.vision.CameraSessionLifecycle
import org.freevia.rubikbuddy.vision.PixelFrameAnalyzer
import platform.AVFoundation.*
import platform.CoreGraphics.CGRectZero
import platform.CoreMedia.*
import platform.CoreVideo.*
import platform.Foundation.*
import platform.UIKit.*
import platform.darwin.*

/** Capture configuration/start/stop stays on one serial queue; analyzer state stays on main. */
internal class CameraView(private val analyzer: PixelFrameAnalyzer, private val onError: (String?) -> Unit) : UIView(frame = CGRectZero.readValue()) {
    private val session = AVCaptureSession()
    private val preview = AVCaptureVideoPreviewLayer(session = session)
    private val output = AVCaptureVideoDataOutput()
    private val queue = dispatch_queue_create("org.freevia.rubikbuddy.camera", null)
    private val lifecycle = CameraSessionLifecycle()
    private val observers = mutableListOf<Any>()
    private var lastFrame = 0L
    private var closed = false
    private var orientation: AVCaptureVideoOrientation = AVCaptureVideoOrientationPortrait

    private val delegate = object : NSObject(), AVCaptureVideoDataOutputSampleBufferDelegateProtocol {
        override fun captureOutput(output: AVCaptureOutput, didOutputSampleBuffer: CMSampleBufferRef?, fromConnection: AVCaptureConnection) {
            if (!lifecycle.shouldRun) return
            val now = (CACurrentMediaTime() * 1000).toLong()
            if (now-lastFrame < 100) return
            lastFrame = now
            val aspect = bounds.useContents { if (size.height > 0) size.width/size.height else 0.0 }
            if (aspect <= 0) return
            val buffer = didOutputSampleBuffer?.let { CMSampleBufferGetImageBuffer(it) } ?: return
            if (CVPixelBufferGetPixelFormatType(buffer) != kCVPixelFormatType_32BGRA) return
            if (CVPixelBufferLockBaseAddress(buffer, kCVPixelBufferLock_ReadOnly) != kCVReturnSuccess) return
            try {
                val bytes = CVPixelBufferGetBaseAddress(buffer)?.reinterpret<UByteVar>() ?: return
                val stride = CVPixelBufferGetBytesPerRow(buffer).toInt()
                val w = CVPixelBufferGetWidth(buffer).toInt()
                val h = CVPixelBufferGetHeight(buffer).toInt()
                analyzer.process(w,h,aspect) { x,y ->
                    val p = y*stride+x*4
                    (0xFF shl 24) or (bytes[p+2].toInt() shl 16) or (bytes[p+1].toInt() shl 8) or bytes[p].toInt()
                }
            } finally { CVPixelBufferUnlockBaseAddress(buffer, kCVPixelBufferLock_ReadOnly) }
        }
    }

    init {
        preview.videoGravity = AVLayerVideoGravityResizeAspectFill
        layer.addSublayer(preview)
        lifecycle.foreground = UIApplication.sharedApplication.applicationState == UIApplicationState.UIApplicationStateActive
        observe(UIApplicationWillResignActiveNotification) {
            lifecycle.foreground = false
            updateRunning()
        }
        observe(UIApplicationDidBecomeActiveNotification) {
            lifecycle.foreground = true
            // A background interruption may not emit an "ended" event after stopRunning.
            // Request capture again; AVFoundation reports any still-active interruption.
            lifecycle.interrupted = false
            if (lifecycle.configured) onError(null)
            updateRunning()
        }
        observe(AVCaptureSessionWasInterruptedNotification, session) {
            lifecycle.interrupted = true
            analyzer.resetTemporalBuffers()
            onError("Camera interrupted. Close other camera apps, then retry.")
        }
        observe(AVCaptureSessionInterruptionEndedNotification, session) {
            lifecycle.interrupted = false
            onError(null)
            updateRunning()
        }
        observe(AVCaptureSessionRuntimeErrorNotification, session) { notification ->
            analyzer.resetTemporalBuffers()
            val error = notification.userInfo?.get(AVCaptureSessionErrorKey) as? NSError
            if (error?.code == AVErrorMediaServicesWereReset) { onError(null); updateRunning() }
            else {
                lifecycle.configured = false
                updateRunning()
                onError("Camera error: ${error?.localizedDescription ?: "Unable to capture frames"}. Retry the camera.")
            }
        }
        dispatch_async(queue) { configure() }
    }

    private fun observe(name: String?, source: Any? = null, action: (NSNotification) -> Unit) {
        if (name == null) return
        observers += NSNotificationCenter.defaultCenter.addObserverForName(name, source, NSOperationQueue.mainQueue) { notification ->
            if (!closed && notification != null) action(notification)
        }
    }

    private fun configure() {
        session.beginConfiguration()
        var failure: String? = null
        try {
            val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
            val input = device?.let { AVCaptureDeviceInput.deviceInputWithDevice(it, null) }
            if (input == null || !session.canAddInput(input)) failure = "Camera unavailable. Check camera access in Settings."
            else {
                if (session.canSetSessionPreset(AVCaptureSessionPreset640x480)) session.sessionPreset = AVCaptureSessionPreset640x480
                session.addInput(input)
                output.videoSettings = mapOf(kCVPixelBufferPixelFormatTypeKey to kCVPixelFormatType_32BGRA)
                output.alwaysDiscardsLateVideoFrames = true
                output.setSampleBufferDelegate(delegate, dispatch_get_main_queue())
                if (session.canAddOutput(output)) session.addOutput(output)
                else failure = "Unable to read camera frames."
            }
        } finally { session.commitConfiguration() }
        dispatch_async(dispatch_get_main_queue()) {
            if (!closed) {
                if (failure != null) onError(failure)
                else {
                    lifecycle.configured = true
                    updateOrientation()
                    updateRunning()
                }
            }
        }
    }

    private fun updateRunning() {
        val run = lifecycle.shouldRun
        if (!run) analyzer.resetTemporalBuffers()
        dispatch_async(queue) {
            if (run && !session.running) session.startRunning()
            else if (!run && session.running) session.stopRunning()
        }
    }

    private fun updateOrientation() {
        val next = when (window?.windowScene?.interfaceOrientation) {
            UIInterfaceOrientationLandscapeLeft -> AVCaptureVideoOrientationLandscapeLeft
            UIInterfaceOrientationLandscapeRight -> AVCaptureVideoOrientationLandscapeRight
            UIInterfaceOrientationPortraitUpsideDown -> AVCaptureVideoOrientationPortraitUpsideDown
            else -> AVCaptureVideoOrientationPortrait
        }
        if (next != orientation) analyzer.resetTemporalBuffers()
        orientation = next
        output.connectionWithMediaType(AVMediaTypeVideo)?.let { if (it.supportsVideoOrientation) it.videoOrientation = next }
        preview.connection?.let { if (it.supportsVideoOrientation) it.videoOrientation = next }
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        preview.frame = bounds
        if (lifecycle.configured) updateOrientation()
    }

    fun close() {
        closed = true
        observers.forEach { NSNotificationCenter.defaultCenter.removeObserver(it) }
        observers.clear()
        lifecycle.close()
        updateRunning()
        dispatch_async(queue) { output.setSampleBufferDelegate(null, null) }
    }
}
