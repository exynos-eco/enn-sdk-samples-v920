#pragma once

#include <iostream>
#include <cmath>
#include <vector>
#include <opencv2/opencv.hpp>

#include <sys/stat.h>
#include <gst/gst.h>
#include <gst/app/app.h>

/* Debug Option */
#define DEF_ENN_BENCHMARK

#define RED         "\x1b[31m"
#define GREEN         "\x1b[32m"
#define YELLOW         "\x1b[33m"
#define BLUE         "\x1b[34m"
#define MAGENTA        "\x1b[35m"
#define CYAN         "\x1b[36m"
#define RESET         "\x1b[0m"

#define TEST_MODEL_PATH_POSE_HRNET "/data/vendor/hrnet/pose_hrnet_w48_384x288_simplify_O2_SingleCore.nnc"
#define TEST_INPUT_FILE_PATH "/data/vendor/hrnet/media/image.jpg"

extern bool g_enable_log; 

#define PRINT_ERROR(message, ...) \
    if (g_enable_log) { \
        printf(RED "TEST: " message, ##__VA_ARGS__); \
        printf("\n" RESET); \
    }

#define PRINT(message, ...) \
    if (g_enable_log) { \
        printf("TEST: " message, ##__VA_ARGS__); \
        printf("\n"); \
    }

enum _TEST_CASE
{
    TEST_CASE_ENN_POSE_HRNET = 1,
    TEST_CASE_OUT_OF_OPTION
} TEST_CASE;

#define INPUT_WIDTH 288
#define INPUT_HEIGHT 384
#define HEATMAP_WIDTH 72
#define HEATMAP_HEIGHT 96
#define DISPLAY_WIDTH 1920
#define DISPLAY_HEIGHT 1080

namespace enn {
namespace sample_utils {

int export_mem_to_file(const char *filename, const void *va, uint32_t size) {
    size_t ret_cnt;

    PRINT("DEBUG:: Export memory to file: name(%s) va(%p), size(%d)", filename, va, size);

    FILE *fp = fopen(filename, "wb");

    ret_cnt = fwrite(va, size, 1, fp);
    if (ret_cnt <= 0) {
        PRINT("FileWrite Failed!!(%zu)", ret_cnt);
        fclose(fp);
        return ENN_RET_INVAL;
    }

    PRINT("DEBUG:: File Save Completed.");
    fclose(fp);

    return ENN_RET_SUCCESS;
}

void show_raw_memory_to_hex(uint8_t *va, uint32_t size, const int line_max, const int size_max) {
    char line_tmp[100] = {0,};
    int int_size = static_cast<int>(size);
    int max = (size_max == 0 ? int_size : (int_size < size_max ? int_size : size_max));
    int idx = sprintf(line_tmp, "[%p] ", va);  // prefix of line
    int i = 0;                                 // idx records current location of print line
    for (; i < max; ++i) {
        idx += sprintf(&(line_tmp[idx]), "%02X ", va[i]);
        if (i % line_max == (line_max - 1)) {
            // if new line is required, flush print --> and record prefix print
            line_tmp[idx] = 0;
            std::cout << line_tmp << std::endl;
            idx = 0;
            idx = sprintf(line_tmp, "[%p] ", &(va[i]));
        }
    }
    if (i % line_max != 0) {
        std::cout << line_tmp << std::endl;
    }
}

void print_buffer_hex(uint8_t *ptr, const int length, int line_max)
{
    printf( " [ %s , %d, %d] ", __func__, length, line_max);

    for(int i = 0 ; i < length ; i++)
    {
        if( i == 0 || (i%line_max) == 0 )
        {
            printf("\n[%p] index(%4d) -" , ptr, i);
        }
        printf(" %02x,", *ptr);
        ptr++;
    }
    printf("\n " );
}

void print_buffer_info(EnnBufferInfo info_p, const enn_buf_dir_e direction)
{
    if(direction == ENN_DIR_IN)
    {
        printf(YELLOW);
        printf( " >>>>>>>>>>>> Input Buffer information  \n" );
        printf( " is_able_to_update : %d \n" , info_p.is_able_to_update);
        printf( " batch size        : %d \n" , info_p.n);
        printf( " width             : %d \n" , info_p.width);
        printf( " height            : %d \n" , info_p.height);
        printf( " channel           : %d \n" , info_p.channel);
        printf( " size              : %d \n" , info_p.size);
        printf( " buffer type       : %d \n" , info_p.buffer_type);
        printf( " label             : %s \n" , info_p.label);
        printf("\n" RESET);
    }
    else if(direction == ENN_DIR_OUT)
    {
        printf(YELLOW);
        printf( " <<<<<<<<<<<< Output Buffer information  \n" );
        printf( " is_able_to_update : %d \n" , info_p.is_able_to_update);
        printf( " batch size        : %d \n" , info_p.n);
        printf( " width             : %d \n" , info_p.width);
        printf( " height            : %d \n" , info_p.height);
        printf( " channel           : %d \n" , info_p.channel);
        printf( " size              : %d \n" , info_p.size);
        printf( " buffer type       : %d \n" , info_p.buffer_type);
        printf( " label             : %s \n" , info_p.label);
        printf("\n" RESET);
    }
    return;
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
struct InferenceProcessor {
    static int preprocess_frame(const cv::Mat& frame, void* buffer) {
        PRINT(GREEN "[InferenceProcessor::%s] START" RESET, __func__);

        float *input_tensor = reinterpret_cast<float *>(buffer);
        cv::Mat resized, rgb;

        cv::cvtColor(frame, rgb, cv::COLOR_BGR2RGB);

        PRINT(BLUE "[InferenceProcessor::%s] Resizing from %dx%d to %dx%d..." RESET, __func__, frame.cols, frame.rows, INPUT_WIDTH, INPUT_HEIGHT);
        cv::resize(rgb, resized, cv::Size(INPUT_WIDTH, INPUT_HEIGHT));

        PRINT(BLUE "[InferenceProcessor::%s] Normalizing pixel values to [0,1]..." RESET, __func__);
        resized.convertTo(resized, CV_32FC3, 1.0 / 255.0);

        PRINT(CYAN "[InferenceProcessor::%s] Rearranging data to CHW format..." RESET, __func__);
        int idx = 0;
        for (int c = 0; c < 3; ++c)
            for (int h = 0; h < INPUT_HEIGHT; ++h)
                for (int w = 0; w < INPUT_WIDTH; ++w)
                    input_tensor[idx++] = resized.at<cv::Vec3f>(h, w)[c];

        PRINT(GREEN "[InferenceProcessor::%s] DONE" RESET, __func__);
        return 0;
    }

    static int load_frame(cv::Mat& frame, EnnBufferPtr inBuffer, EnnModelId model_id) {
        EnnBufferInfo in_buf_info;
        enn::api::EnnGetBufferInfoByIndex(&in_buf_info, model_id, ENN_DIR_IN, 0);

        int ret = preprocess_frame(frame, inBuffer->va);

        return ret;
    }

    static int postprocess(EnnBufferPtr outBuffer, EnnModelId model_id, std::vector<std::pair<int, int>>& keypoints) {
        PRINT(GREEN "[InferenceProcessor::%s] START" RESET, __func__);

        float *output_p = reinterpret_cast<float *>(outBuffer->va);
        EnnBufferInfo out_buf_info;
        enn::api::EnnGetBufferInfoByIndex(&out_buf_info, model_id, ENN_DIR_OUT, 0);

        int channel = out_buf_info.channel;
        int h = out_buf_info.height;
        int w = out_buf_info.width;

        PRINT(BLUE "[InferenceProcessor::%s] Output shape: channel=%d, height=%d, width=%d" RESET, __func__, channel, h, w);

        for (int i = 0; i < channel; ++i) {
            int max_idx = 0;
            float max_val = -1.0f;
            for (int y = 0; y < h; ++y) {
                for (int x = 0; x < w; ++x) {
                    int idx = i * h * w + y * w + x;
                    if (output_p[idx] > max_val) {
                        max_val = output_p[idx];
                        max_idx = idx;
                    }
                }
            }

            int y = (max_idx % (h * w)) / w;
            int x = (max_idx % (h * w)) % w;
            keypoints.emplace_back(x, y);

            PRINT(YELLOW "[InferenceProcessor::%s] keypoint %d: (x=%d, y=%d), max_val=%f" RESET, __func__, i, x, y, max_val);
        }

        PRINT(GREEN "[InferenceProcessor::%s] DONE" RESET, __func__);
        return 0;
    }
};


struct KeypointDrawer {
    inline static const std::vector<std::tuple<int, int, cv::Scalar>> SKELETON = {
        {0, 1, cv::Scalar(127, 2, 240)}, {0, 2, cv::Scalar(127, 2, 240)},
        {1, 3, cv::Scalar(127, 2, 240)}, {2, 4, cv::Scalar(127, 2, 240)},
        {5, 6, cv::Scalar(142, 209, 169)}, {5, 7, cv::Scalar(142, 209, 169)}, {7, 9, cv::Scalar(142, 209, 169)},
        {6, 8, cv::Scalar(0, 255, 255)}, {8, 10, cv::Scalar(0, 255, 255)},
        {5, 11, cv::Scalar(240, 176, 0)}, {11, 13, cv::Scalar(240, 176, 0)}, {13, 15, cv::Scalar(240, 176, 0)},
        {6, 12, cv::Scalar(243, 176, 252)}, {12, 14, cv::Scalar(243, 176, 252)}, {14, 16, cv::Scalar(243, 176, 252)}
    };

    static cv::Mat draw(const cv::Mat& image, const std::vector<std::pair<int, int>>& keypoints) {
        PRINT(GREEN "[KeypointDrawer::%s] START" RESET, __func__);

        float scale_x = static_cast<float>(image.cols) / HEATMAP_WIDTH;
        float scale_y = static_cast<float>(image.rows) / HEATMAP_HEIGHT;
        PRINT(YELLOW "[KeypointDrawer::%s] x_scale: %f , y_scale: %f" RESET, __func__, scale_x, scale_y);

        int base_dim = std::min(image.rows, image.cols);
        int radius = std::max(2, static_cast<int>(base_dim * 0.008f));
        int line_thickness = std::max(2, static_cast<int>(base_dim * 0.005f));

        PRINT(BLUE "[KeypointDrawer::%s] Drawing skeleton and keypoints (radius=%d, thickness=%d)..." RESET, __func__, radius, line_thickness);

        cv::Mat vis_img = image.clone();
        std::vector<cv::Point> scaled_points;

        for (size_t i = 0; i < keypoints.size(); ++i) {
            int px = static_cast<int>(keypoints[i].first * scale_x);
            int py = static_cast<int>(keypoints[i].second * scale_y);
            scaled_points.emplace_back(px, py);
            // PRINT(CYAN "[KeypointDrawer::%s] keypoint %zu: scaled (x=%d, y=%d)" RESET, __func__, i, px, py);
        }

        int num_points = static_cast<int>(scaled_points.size());
        for (const auto& [start_idx, end_idx, color] : SKELETON) {
            if (start_idx < num_points && end_idx < num_points) {
                cv::line(vis_img, scaled_points[start_idx], scaled_points[end_idx], color, line_thickness, cv::LINE_AA);
            }
        }

        for (const auto& pt : scaled_points) {
            cv::circle(vis_img, pt, radius, cv::Scalar(0, 0, 0), 1, cv::LINE_AA);
        }

        PRINT(BLUE "[KeypointDrawer::%s] Centering image in %dx%d canvas" RESET, __func__, DISPLAY_WIDTH, DISPLAY_HEIGHT);

        const int canvas_w = DISPLAY_WIDTH;
        const int canvas_h = DISPLAY_HEIGHT;

        int img_w = vis_img.cols;
        int img_h = vis_img.rows;

        float scale = std::min((float)canvas_w / img_w, (float)canvas_h / img_h);
        int new_w = static_cast<int>(img_w * scale);
        int new_h = static_cast<int>(img_h * scale);

        cv::Mat resized_img;
        cv::resize(vis_img, resized_img, cv::Size(new_w, new_h));

        cv::Mat padded_img = cv::Mat::zeros(cv::Size(canvas_w, canvas_h), vis_img.type());
        int x_offset = (canvas_w - new_w) / 2;
        int y_offset = (canvas_h - new_h) / 2;
        cv::Rect roi(x_offset, y_offset, new_w, new_h);
        resized_img.copyTo(padded_img(roi));

        PRINT(GREEN "[KeypointDrawer::%s] DONE" RESET, __func__);
        return padded_img;
    }
};


struct GStreamerDisplay {
    GstElement* pipeline = nullptr;
    GstElement* appsrc = nullptr;

    bool initialize() {
        PRINT(GREEN "[GStreamerDisplay::%s] START" RESET, __func__ );
        gst_init(nullptr, nullptr);

        GError* error = nullptr;
        PRINT(BLUE "[GStreamerDisplay::%s] Creating GStreamer pipeline..." RESET, __func__);
        pipeline = gst_parse_launch(
            "appsrc name=mysource is-live=true block=true format=time ! "
            "videoconvert ! "
            "videoscale ! "
            "video/x-raw,width=1920,height=1080 ! "
            "autovideosink sync=false", &error);

        if (error) {
            PRINT_ERROR("[GStreamerDisplay::%s] Failed to create pipeline: %s", __func__, error->message);
            return false;
        }

        PRINT(BLUE "[GStreamerDisplay::%s] Getting appsrc element..." RESET, __func__);
        appsrc = gst_bin_get_by_name(GST_BIN(pipeline), "mysource");

        PRINT(BLUE "[GStreamerDisplay::%s] Setting caps..." RESET, __func__);
        GstCaps* caps = gst_caps_new_simple("video/x-raw",
            "format", G_TYPE_STRING, "BGR",
            "width", G_TYPE_INT, DISPLAY_WIDTH,
            "height", G_TYPE_INT, DISPLAY_HEIGHT,
            "framerate", GST_TYPE_FRACTION, 30, 1, NULL);
        gst_app_src_set_caps(GST_APP_SRC(appsrc), caps);
        gst_caps_unref(caps);

        PRINT(BLUE "[GStreamerDisplay::%s] Setting pipeline to PLAYING..." RESET, __func__);
        gst_element_set_state(pipeline, GST_STATE_PLAYING);

        PRINT(GREEN "[GStreamerDisplay::%s] SUCCESS" RESET, __func__);
        return true;
    }

    void push_frame(const cv::Mat& frame, int frame_count) {
        PRINT(CYAN "[GStreamerDisplay::%s] Pushing frame %d..." RESET, __func__, frame_count);

        GstBuffer* buffer = gst_buffer_new_allocate(NULL, frame.total() * frame.elemSize(), NULL);
        GstMapInfo map;
        gst_buffer_map(buffer, &map, GST_MAP_WRITE);
        memcpy(map.data, frame.data, frame.total() * frame.elemSize());
        gst_buffer_unmap(buffer, &map);

        GST_BUFFER_PTS(buffer) = gst_util_uint64_scale(frame_count, GST_SECOND, 30);
        GST_BUFFER_DURATION(buffer) = gst_util_uint64_scale(1, GST_SECOND, 30);

        GstFlowReturn ret;
        g_signal_emit_by_name(appsrc, "push-buffer", buffer, &ret);
        gst_buffer_unref(buffer);

        PRINT(CYAN "[GStreamerDisplay::%s] Frame %d pushed. Flow return: %d" RESET, __func__, frame_count, ret);
    }

    void cleanup() {
        PRINT(YELLOW "[GStreamerDisplay::%s] START cleanup" RESET, __func__);
        gst_element_set_state(pipeline, GST_STATE_NULL);
        gst_object_unref(pipeline);
        PRINT(YELLOW "[GStreamerDisplay::%s] DONE cleanup" RESET, __func__);
    }
};


struct EnnSession {
    EnnModelId model_id;
    EnnBufferPtr* buffer_set;
    EnnBufferInfo* in_bufs;
    EnnBufferInfo* out_bufs;
    uint32_t n_in_buf;
    uint32_t n_out_buf;

    bool initializeSession(const std::string& model_file) {
        PRINT(GREEN "[EnnSession::%s] START" RESET, __func__);

        PRINT(BLUE "[EnnSession::%s] Initializing ENN..." RESET, __func__);
        if (enn::api::EnnInitialize()) {
            PRINT_ERROR("[EnnSession::%s] Initialize Failed", __func__);
            return false;
        }

        PRINT(BLUE "[EnnSession::%s] Opening model: %s" RESET, __func__, model_file.c_str());
        if (enn::api::EnnOpenModel(model_file.c_str(), &model_id)) {
            PRINT_ERROR("[EnnSession::%s] Open Model Failed: %s", __func__, model_file.c_str());
            return false;
        }

        PRINT(BLUE "[EnnSession::%s] Allocating buffers..." RESET, __func__);
        NumberOfBuffersInfo num_buffers;
        if (enn::api::EnnAllocateAllBuffers(model_id, &buffer_set, &num_buffers)) {
            PRINT_ERROR("[EnnSession::%s] Allocate Buffer Error", __func__);
            return false;
        }

        n_in_buf = num_buffers.n_in_buf;
        n_out_buf = num_buffers.n_out_buf;
        PRINT(YELLOW "[EnnSession::%s] Number of input buffers: %u" RESET, __func__, n_in_buf);
        PRINT(YELLOW "[EnnSession::%s] Number of output buffers: %u" RESET, __func__, n_out_buf);

        in_bufs = new EnnBufferInfo[n_in_buf];
        out_bufs = new EnnBufferInfo[n_out_buf];

        for (uint32_t i = 0; i < n_in_buf; ++i)
            enn::api::EnnGetBufferInfoByIndex(&in_bufs[i], model_id, ENN_DIR_IN, i);

        for (uint32_t i = 0; i < n_out_buf; ++i)
            enn::api::EnnGetBufferInfoByIndex(&out_bufs[i], model_id, ENN_DIR_OUT, i);

        PRINT(GREEN "[EnnSession::%s] DONE" RESET, __func__);
        return true;
    }

    void run(cv::Mat& frame, std::vector<std::pair<int, int>>& keypoints) {
        PRINT(GREEN "[EnnSession::%s] START" RESET, __func__);

        InferenceProcessor::load_frame(frame, buffer_set[0], model_id);

        PRINT(BLUE "[EnnSession::%s] EnnExcute model inference..." RESET, __func__);
        enn::api::EnnExecuteModel(model_id);

        InferenceProcessor::postprocess(buffer_set[n_in_buf], model_id, keypoints);

        PRINT(GREEN "[EnnSession::%s] DONE" RESET, __func__);
    }

    void cleanup() {
        PRINT(GREEN "[EnnSession::%s] START cleanup" RESET, __func__);

        PRINT(BLUE "[EnnSession::%s] Releasing buffers..." RESET, __func__);
        enn::api::EnnReleaseBuffers(buffer_set, n_in_buf + n_out_buf);

        PRINT(BLUE "[EnnSession::%s] Closing model..." RESET, __func__);
        enn::api::EnnCloseModel(model_id);

        PRINT(BLUE "[EnnSession::%s] Deinitializing ENN..." RESET, __func__);
        enn::api::EnnDeinitialize();

        delete[] in_bufs;
        delete[] out_bufs;

        PRINT(GREEN "[EnnSession::%s] DONE cleanup" RESET, __func__);
    }
};


}  // namespace sample_utils
}  // namespace enn
