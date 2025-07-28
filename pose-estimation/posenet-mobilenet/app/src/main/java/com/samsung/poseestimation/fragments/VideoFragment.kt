// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

package com.samsung.poseestimation.fragments

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.MediaController
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.samsung.poseestimation.data.Human
import com.samsung.poseestimation.data.ModelConstants
import com.samsung.poseestimation.databinding.FragmentVideoBinding
import com.samsung.poseestimation.executor.ModelExecutor
import com.samsung.poseestimation.fragments.CameraFragment.Companion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


@UnstableApi class VideoFragment : Fragment(), ModelExecutor.ExecutorListener {
    private lateinit var binding: FragmentVideoBinding
    private lateinit var bitmapBuffer: Bitmap
    private lateinit var modelExecutor: ModelExecutor
    private lateinit var videoExecutor: ExecutorService
    private lateinit var player: ExoPlayer

    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null

    private val getContent =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                // Initialize ExoPlayer
                player = ExoPlayer.Builder(requireContext()).build()
                binding.playerView.player = player

                // Prepare the media item
                val mediaItem = MediaItem.fromUri(uri)
                player.setMediaItem(mediaItem)

                // Prepare the player
                player.prepare()

                binding.buttonProcess.isEnabled = true
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentVideoBinding.inflate(layoutInflater)

        return binding.root
    }

    override fun onViewCreated(
        view: View, savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        modelExecutor = ModelExecutor(
            context = requireContext(), executorListener = this
        )

        setUI()
    }

    private fun setUI() {
        binding.buttonLoad.setOnClickListener {
            getContent.launch("video/*")
        }

        binding.buttonProcess.isEnabled = false
        binding.buttonProcess.setOnClickListener {
            Log.i(TAG, "button")
            processVideoFrames()
        }

        binding.processData.buttonThresholdPlus.setOnClickListener {
            adjustThreshold(0.1F)
        }

        binding.processData.buttonThresholdMinus.setOnClickListener {
            adjustThreshold(-0.1F)
        }
    }

    private fun processVideoFrames() {
        // First, ensure the video is loaded and ready
        Log.i(TAG, "processVideoFrames")
        setupVideoFrameExtraction()
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    // Set up video frame processing
                    setupVideoFrameExtraction()
                }
            }
        })

        // Start playback
        player.play()
    }

    private fun setupVideoFrameExtraction() {
        Log.i(TAG, "setupVideoFrameExtraction")
        // Create a separate thread for frame processing
        val frameProcessingExecutor = Executors.newSingleThreadExecutor()

        // Use MediaMetadataRetriever to extract the frame
        val retriever = MediaMetadataRetriever()
        Log.i(TAG, retriever.toString())
        retriever.setDataSource(
            requireContext(),
            player.currentMediaItem?.playbackProperties?.uri
        )
        var previousBitmap: Bitmap? = null
        var processedBitmap: Bitmap? = null

        // Use VideoFrameMetadataListener to get precise frame information
        player.setVideoFrameMetadataListener { presentationTimeUs, releaseTimeNs, format, mediaItem ->
            try {
                // Extract bitmap at the specific time
                val bitmap = retriever.getFrameAtTime(
                    presentationTimeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST
//                    MediaMetadataRetriever.OPTION_PREVIOUS_SYNC
                )

                bitmap?.let { frameBitmap ->
                    // Process the bitmap
                    processedBitmap = processImage(frameBitmap)
                    // Run model inference
                    modelExecutor.process(processedBitmap!!)
                }

                previousBitmap = processedBitmap
//                TimeUnit.MICROSECONDS.sleep(500_000L)
            } catch (e: Exception) {
                Log.e(TAG, "Frame extraction error: ${e.message}")
            }
        }
    }

    // Process the image
    private fun process(image: ImageProxy) {
        image.use { bitmapBuffer.copyPixelsFromBuffer(image.planes[0].buffer) }
        modelExecutor.process(processImage(bitmapBuffer))
    }

    private fun processImage(bitmap: Bitmap): Bitmap {
        val (scaledWidth, scaledHeight) = calculateScaleSize(
            bitmap.width, bitmap.height
        )
        val scaledBitmap = Bitmap.createScaledBitmap(
            bitmap, scaledWidth, scaledHeight, true
        )
        val (x, y) = calculateCenterCropPosition(scaledBitmap)

        return Bitmap.createBitmap(scaledBitmap, x, y,
            INPUT_SIZE_W,
            INPUT_SIZE_H
        )
    }

    private fun calculateScaleSize(bitmapWidth: Int, bitmapHeight: Int): Pair<Int, Int> {
        val scaleFactor = maxOf(
            INPUT_SIZE_W.toDouble() / bitmapWidth, INPUT_SIZE_H.toDouble() / bitmapHeight
        )

        return Pair((bitmapWidth * scaleFactor).toInt(), (bitmapHeight * scaleFactor).toInt())
    }

    private fun calculateCenterCropPosition(scaledBitmap: Bitmap): Pair<Int, Int> {
        return Pair(
            (scaledBitmap.width - INPUT_SIZE_W) / 2,
            (scaledBitmap.height - INPUT_SIZE_H) / 2
        )
    }

    private fun adjustThreshold(delta: Float) {
        val newThreshold = modelExecutor.threshold + delta
        if (newThreshold in 0.05 .. 0.95) {
            modelExecutor.threshold = newThreshold
            binding.processData.textThreshold.text = String.format("%.1f", newThreshold)
        }
    }

    override fun onError(error: String) {
        Log.e(TAG, "ModelExecutor error: $error")
    }

    override fun onResults(
        detectionResult: Human, inferenceTime: Long
    ) {
        activity?.runOnUiThread {
            Log.i("VideoFragment", "detectionResult : ${detectionResult.score}")
            if (detectionResult.score >= modelExecutor.threshold) {
                binding.processData.inferenceTime.text = "$inferenceTime ms"
                binding.overlay.setResults(detectionResult)
                binding.overlay.invalidate()
            } else {
                binding.overlay.clear()
                binding.overlay.invalidate()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        modelExecutor.closeENN()
    }

    companion object {
        private const val TAG = "VideoFragment"
        private const val INPUT_SIZE_W = ModelConstants.INPUT_SIZE_W
        private const val INPUT_SIZE_H = ModelConstants.INPUT_SIZE_H
    }
}

