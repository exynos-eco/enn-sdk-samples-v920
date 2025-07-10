/**
 * Copyright (C) 2022 Samsung Electronics Co., Ltd. All Rights Reserved
 *
 * This software is proprietary of Samsung Electronics.
 * No part of this software, either material or conceptual may be copied or distributed,
 * transmitted, transcribed, stored in a retrieval system or translated into any human or
 * computer language in any form by any means, electronic, mechanical, manual or
 * otherwise or disclosed to third parties without the express written permission of
 * Samsung Electronics.
 */

#include <iostream>
#include <getopt.h>
#include "include/enn_api-public.hpp"
#include "include/enn_sample_utils.hpp"

#include <cmath>
#include <vector>
#include <opencv2/opencv.hpp>

#include <sys/stat.h>
#include <gst/gst.h>
#include <gst/app/app.h>
#include <chrono>
#include <thread>

bool g_enable_log = false;

volatile sig_atomic_t keep_running = 1;

void signal_handler(int signum) {
    PRINT(GREEN "[%s] Received Ctrl+C (SIGINT), exiting... " RESET, __func__);
    keep_running = 0;
}

int run_pose_estimation_demo(const std::string& model_file, const std::string& input_file) {
    PRINT(GREEN "[%s] START " RESET, __func__);

    setenv("WAYLAND_DISPLAY", "wayland-1", 1);
    signal(SIGINT, signal_handler);

    int input_type = 0; // 1: image, 2: video
    cv::Mat img_frame = cv::imread(input_file);
    cv::VideoCapture cap(input_file);

    if (!img_frame.empty()) {
        input_type = 1;
    } else if (cap.isOpened()) {
        input_type = 2;
    } else {
        PRINT_ERROR("Failed to open input file: %s", input_file.c_str());
        return 0;
    }
    
    PRINT(BLUE "[%s] Successfully opened input file: %s" RESET, __func__, input_file.c_str());

    enn::sample_utils::EnnSession enn;
    if (!enn.initializeSession(model_file)) {
        PRINT_ERROR("Failed to initialize ENN Session.");
        return 0;
    }

    enn::sample_utils::GStreamerDisplay gst;
    if (!gst.initialize()) {
        PRINT_ERROR("Failed to initialize GStreamer.");
        enn.cleanup();
        return 0;
    }

    int frame_count = 0;
    cv::Mat last_frame;

    if (input_type == 1) {
        std::vector<std::pair<int, int>> keypoints;
        enn.run(img_frame, keypoints);
        cv::Mat vis = enn::sample_utils::KeypointDrawer::draw(img_frame, keypoints);
        last_frame = vis.clone();

        gst.push_frame(vis, frame_count++);
        cv::imwrite("/data/vendor/hrnet/result.jpg", vis);     
    } else {
        while (keep_running) {
            cv::Mat frame;
            if (!cap.read(frame)) break;

            std::vector<std::pair<int, int>> keypoints;
            enn.run(frame, keypoints);
            cv::Mat vis = enn::sample_utils::KeypointDrawer::draw(frame, keypoints);

            gst.push_frame(vis, frame_count++);
            last_frame = vis.clone();       
        }
    }

    int tick_count = 0;
    while (keep_running) {
        if (tick_count % 10 == 0) {
            PRINT(CYAN "[main] Waiting for Ctrl+C to exit..." RESET);
        }        

        std::this_thread::sleep_for(std::chrono::milliseconds(100));
        tick_count++;
    }

    gst.cleanup();
    enn.cleanup();
    PRINT(GREEN "[%s] Finished" RESET, __func__);
    return 0;
}

void print_help(char** argv) {
    printf(
        "\n"
        "Usage: %s [OPTION]\n"
        "[REQUIRED]\n"
        "   -m         modle file path\n"
        "   -i         input file path\n"
        "   -c         test case number\n"
        "              1: [sample] run pose_hrnet(fp16) with ENN API\n"
        "              2: [sample] run pose_hrnet(asymm) with ENN API\n"
        "              [default : 1]\n\n"
        , argv[0]
    );

}

int main(int argc, char** argv ) {
    int opt;
    std::string arg_model = TEST_MODEL_PATH_POSE_HRNET;
    std::string arg_input = TEST_INPUT_FILE_PATH;
    int test_case = TEST_CASE_ENN_POSE_HRNET;

    while ((opt = getopt(argc, argv, "m:i:c:l:")) != -1) {
        switch (opt) {
            case 'm':
                arg_model = optarg;
                break;
            case 'i':
                arg_input = optarg;
                break;
            case 'c':
                test_case = atoi(optarg);
                break;           
            case 'l':
                g_enable_log = atoi(optarg) != 0;
                break;                
            default:
                print_help(argv);
                return 1;
        }
    }

    switch(test_case)
    {
        case TEST_CASE_ENN_POSE_HRNET:
            run_pose_estimation_demo(arg_model, arg_input);
            break;

        default:
            break;
    }

    return 0;
}
