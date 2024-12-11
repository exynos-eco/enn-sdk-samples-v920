// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

package com.samsung.imageclassification.fragments

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.samsung.imageclassification.data.ModelConstants
import com.samsung.imageclassification.databinding.FragmentImageBinding
import com.samsung.imageclassification.databinding.FragmentVideoBinding
import com.samsung.imageclassification.executor.ModelExecutor
import java.util.concurrent.Executors
import kotlin.math.min
import kotlin.math.roundToInt


@UnstableApi
class VideoFragment : Fragment(), ModelExecutor.ExecutorListener {
    private lateinit var binding: FragmentVideoBinding
    private lateinit var bitmapBuffer: Bitmap
    private lateinit var modelExecutor: ModelExecutor
    private lateinit var detectedItems: List<TextView>
    private lateinit var player: ExoPlayer
    private val TARGET_SIZE = 256
    private val CROP_SIZE = 224

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
            processVideoFrames()
        }

        binding.processData.buttonThresholdPlus.setOnClickListener {
            adjustThreshold(0.1F)
        }

        binding.processData.buttonThresholdMinus.setOnClickListener {
            adjustThreshold(-0.1F)
        }

        detectedItems = listOf(
            binding.processData.detectedItem0,
//            binding.processData.detectedItem1,
//            binding.processData.detectedItem2
        )
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
        var processedBitmap: Bitmap? = null

        // Use VideoFrameMetadataListener to get precise frame information
        player.setVideoFrameMetadataListener { presentationTimeUs, releaseTimeNs, format, mediaItem ->
            try {
                // Extract bitmap at the specific time
                val bitmap = retriever.getFrameAtTime(
                    presentationTimeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST
                )

                bitmap?.let { frameBitmap ->
                    // Process the bitmap
                    processedBitmap = processImage(frameBitmap)
                    // Run model inference
                    modelExecutor.process(processedBitmap!!)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Frame extraction error: ${e.message}")
            }
        }
    }


    private fun process(bitmapBuffer: Bitmap) {
        modelExecutor.process(bitmapBuffer)
    }

    private fun processImage(bitmap: Bitmap): Bitmap {
        // Step 1: Resize the image while maintaining aspect ratio
        val resizedBitmap = resizeImage(bitmap, TARGET_SIZE)

        // Step 2: Center crop the image to 224x224
        val cropedBitmap = centerCrop(resizedBitmap, CROP_SIZE, CROP_SIZE)

        return cropedBitmap
    }

    private fun resizeImage(bitmap: Bitmap, minLen: Int): Bitmap {
        val ratio = minLen.toFloat() / min(bitmap.width, bitmap.height)
        val newWidth: Int
        val newHeight: Int
        if (bitmap.width > bitmap.height) {
            newWidth = (ratio * bitmap.width).roundToInt()
            newHeight = minLen
        } else {
            newWidth = minLen
            newHeight = (ratio * bitmap.height).roundToInt()
        }
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun centerCrop(bitmap: Bitmap, cropW: Int, cropH: Int): Bitmap {
        val startX = (bitmap.width - cropW) / 2
        val startY = (bitmap.height - cropH) / 2
        return Bitmap.createBitmap(bitmap, startX, startY, cropW, cropH)
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
        result: Map<String, Float>, inferenceTime: Long
    ) {
        activity?.runOnUiThread {
            binding.processData.inferenceTime.text = "$inferenceTime ms"
            updateUI(result)
        }
    }

    private fun updateUI(result: Map<String, Float>) {
        detectedItems.forEachIndexed { index, textView ->
            if (index < result.size) {
                val key = result.keys.elementAt(index)
                textView.text = key
            } else {
                textView.text = ""
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        modelExecutor.closeENN()
    }

    companion object {
        private const val TAG = "ImageFragment"
        private const val INPUT_SIZE_W = ModelConstants.INPUT_SIZE_W
        private const val INPUT_SIZE_H = ModelConstants.INPUT_SIZE_H
    }
}
