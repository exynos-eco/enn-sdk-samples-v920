// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

#include "include/enn_nnc_model_tester.h"

#include <chrono>
#include <cmath>
#include <fstream>
#include <iostream>
#include <string>
#include <vector>

#include "include/CLI11.hpp"
#include "include/enn_api-public_ndk_v1.hpp"

int main(int argc, char *argv[]) {
    std::string model_name;
    std::vector<std::string> inputs;
    std::vector<std::string> goldens;
    int iteration = 1;
    bool force = false;
    float threshold = 0.0F;

    parse_arguments(argc, argv, model_name, inputs, goldens, iteration, force,
                    threshold);

    if (inputs.empty() && !force) {
        std::cerr
            << "You must provide input data files or use the --force flag."
            << std::endl;
        return FAILURE;
    }

    if (execute_model(model_name, inputs, goldens, force, threshold,
                      iteration)) {
        std::cerr << ERROR_COLOR << "[[Failed to Execute Model]]" << RESET_COLOR
                  << std::endl;
        return FAILURE;
    }
    std::cout << SUCCESS_COLOR << "ENN Framework Execute Model Sucess"
              << RESET_COLOR << std::endl;
}

int execute_model(const std::string model_name,
                  const std::vector<std::string> &inputs,
                  const std::vector<std::string> &goldens,
                  const bool force_mode, const float threshold,
                  const int iteration) {
    if (enn::api::EnnInitialize()) {
        std::cerr << ERROR_COLOR << "ENN Framework Error:" << RESET_COLOR
                  << "\tFailed to Initialize" << std::endl;
        return FAILURE;
    }

    EnnModelId model_id;

    if (enn::api::EnnOpenModel(model_name.c_str(), &model_id)) {
        std::cerr << ERROR_COLOR << "ENN Framework Error:" << RESET_COLOR
                  << "\tFailed to Open Model" << std::endl;
        return FAILURE;
    } else {
        std::cout << "Loaded Model:\n\t" << model_name << "(" << STDOUT_64HEX
                  << model_id << std::dec << ")" << std::endl;
    }

    EnnBufferPtr *buffer_set;
    NumberOfBuffersInfo buffer_info;

    if (enn::api::EnnAllocateAllBuffers(model_id, &buffer_set, &buffer_info)) {
        std::cerr << ERROR_COLOR << "ENN Framework Error:" << RESET_COLOR
                  << "\tFailed to Allocate Buffers" << std::endl;
        return FAILURE;
    }

    uint32_t n_in_buf = buffer_info.n_in_buf;
    uint32_t n_out_buf = buffer_info.n_out_buf;

    if (!force_mode) {
        if (load_inputs(buffer_set, buffer_info, inputs)) {
            std::cerr << ERROR_COLOR << "INPUT Error:" << RESET_COLOR
                      << "\tProblem loading input files" << std::endl;
            return FAILURE;
        }
    }

    auto total_duration = 0;

    for (int idx = 1; idx <= iteration; idx++) {
        auto start = std::chrono::high_resolution_clock::now();

        if (enn::api::EnnExecuteModel(model_id)) {
            std::cerr << ERROR_COLOR << "ENN Framework Error:" << RESET_COLOR
                      << "\tFailed to Execute Model" << std::endl;
            return FAILURE;
        }

        auto end = std::chrono::high_resolution_clock::now();
        auto duration =
            (std::chrono::duration_cast<std::chrono::microseconds>(end - start))
                .count();
        std::cout << "Model Execution Time (" << idx << "): " << duration
                  << " microseconds" << std::endl;
        total_duration += duration;
    }

    std::cout << "Avg. Model Execution Time: " << (total_duration / iteration)
              << " microseconds" << std::endl;

    if (!force_mode) {
        process_outputs(model_id, buffer_set, buffer_info, goldens, threshold);
    }

    if (enn::api::EnnReleaseBuffers(buffer_set, n_in_buf + n_out_buf)) {
        std::cerr << ERROR_COLOR << "ENN Framework Error:" << RESET_COLOR
                  << "\tFailed to Release Buffers" << std::endl;
        return FAILURE;
    }

    if (enn::api::EnnCloseModel(model_id)) {
        std::cerr << ERROR_COLOR << "ENN Framework Error:" << RESET_COLOR
                  << "\tFailed to Close Model" << std::endl;
        return FAILURE;
    }

    if (enn::api::EnnDeinitialize()) {
        std::cerr << ERROR_COLOR << "ENN Framework Error:" << RESET_COLOR
                  << "\tFailed to Deinitialize" << std::endl;
        return FAILURE;
    }

    return SUCCESS;
}

template <typename T>
int compare_data(T *data1, T *data2, int size, T threshold) {
    int diff = 0;
    for (int idx = 0; idx < size; idx++) {
        if (std::abs(data1[idx] - data2[idx]) > threshold) {
            diff++;
        }
    }

    return diff;
}

template <typename T>
float calculate_snr(T *control_data, T *test_data, int size) {
    float signal_power = 0;
    float noise_power = 0;

    for (int idx = 0; idx < size; idx++) {
        signal_power += control_data[idx] * control_data[idx];
        noise_power += (test_data[idx] - control_data[idx]) *
                       (test_data[idx] - control_data[idx]);
    }

    if (noise_power == 0) return INFINITY;

    return 10 * log10(signal_power / noise_power);
}

int copy_file_to_mem(const char *filename, char *dst) {
    FILE *f = fopen(filename, "rb");

    if (!f) {
        std::cerr << ERROR_COLOR << "INPUT Error:" << RESET_COLOR
                  << "\tCannot open file(" << filename << ")" << std::endl;
        return -1;
    }

    size_t size;

    fseek(f, 0, SEEK_END);
    size = ftell(f);
    fseek(f, 0, SEEK_SET);

    if (size < 0) {
        std::cerr << ERROR_COLOR << "INPUT Error:" << RESET_COLOR
                  << "\tInvalid file size" << std::endl;
        return -1;
    }

    if (size != fread(dst, 1, size, f)) {
        std::cerr << ERROR_COLOR << "INPUT Error:" << RESET_COLOR
                  << "\tCannot read file" << std::endl;
        return -1;
    }

    fclose(f);

    return size;
}

int copy_mem_to_file(const char *src, const char *filename, const int size) {
    FILE *f = fopen(filename, "wb");

    if (!f) {
        std::cerr << ERROR_COLOR << "OUTPUT Error:" << RESET_COLOR
                  << "\tCannot open file(" << filename << ")" << std::endl;
        return FAILURE;
    }

    if (fwrite(src, 1, size, f) != size) {
        std::cerr << ERROR_COLOR << "OUTPUT Error:" << RESET_COLOR
                  << "\tCannot write to file" << std::endl;
        return FAILURE;
    }

    fclose(f);

    return SUCCESS;
}

int load_inputs(const EnnBufferPtr *buffer_set,
                const NumberOfBuffersInfo buffer_info,
                const std::vector<std::string> &inputs) {
    uint32_t n_in_buf = buffer_info.n_in_buf;

    if (inputs.size() != n_in_buf) {
        std::cerr
            << ERROR_COLOR << "INPUT Error:" << RESET_COLOR
            << "\tNumber of input layers and input data files do not match"
            << std::endl;
        return FAILURE;
    }

    for (int idx = 0; idx < n_in_buf; idx++) {
        int load_size = copy_file_to_mem(
            inputs[idx].c_str(), reinterpret_cast<char *>(buffer_set[idx]->va));
        if (load_size < 0) {
            std::cerr << ERROR_COLOR << "INPUT Error:" << RESET_COLOR
                      << "\tLayer Index " << idx
                      << ": Problem loading input data file to memory"
                      << std::endl;
            return FAILURE;
        }
        if (load_size != (buffer_set[idx]->size)) {
            std::cerr << ERROR_COLOR << "INPUT Error:" << RESET_COLOR
                      << "\tLayer Index " << idx
                      << ": Input layer size and input data size mismatch"
                      << std::endl;
            return FAILURE;
        }
    }
    
    return SUCCESS;
}

int process_outputs(const EnnModelId model_id, EnnBufferPtr *buffer_set,
                    NumberOfBuffersInfo buffer_info,
                    const std::vector<std::string> &goldens, float threshold) {
    uint32_t n_in_buf = buffer_info.n_in_buf;
    uint32_t n_out_buf = buffer_info.n_out_buf;

    const bool golden_match = !(goldens.size() != n_out_buf);

    if (goldens.size() != n_out_buf) {
        std::cout << "Number of golden files and output layers mismatch.\n"
                  << "\tDumping output layers." << std::endl;
    }

    EnnBufferInfo output_buffer_info;

    for (int idx = 0; idx < n_out_buf; idx++) {
        int layer_idx = n_in_buf + idx;

        if (golden_match) {
            int golden_size = buffer_set[layer_idx]->size;
            char *golden = new char[golden_size];

            int load_size = copy_file_to_mem(goldens[idx].c_str(), golden);
            if (load_size < 0) {
                std::cerr << ERROR_COLOR << "INPUT Error:" << RESET_COLOR
                          << "\tLayer Index " << idx
                          << ": Problem loading golden data file to memory"
                          << std::endl;
                delete[] golden;
                return FAILURE;
            }
            if (load_size != golden_size) {
                std::cerr << ERROR_COLOR << "INPUT Error:" << RESET_COLOR
                          << "\tLayer Index " << idx
                          << ": Output layer size and golden data size mismatch"
                          << std::endl;
                delete[] golden;
                return FAILURE;
            }

            std::cout << "Output Layer(" << idx << "): ";

            enn::api::EnnGetBufferInfoByIndex(&output_buffer_info, model_id,
                                              ENN_DIR_OUT, idx);

            switch (output_buffer_info.buffer_type) {
                case BufferType_FLOAT32:
                    golden_matching<float>(golden, buffer_set[layer_idx]->va,
                                           buffer_set[layer_idx]->size,
                                           threshold);
                    break;
                case BufferType_UINT8:
                    golden_matching<uint8_t>(golden, buffer_set[layer_idx]->va,
                                             buffer_set[layer_idx]->size,
                                             threshold);
                    break;
            }
            delete[] golden;
        } else {
            char filename[256];
            snprintf(filename, sizeof(filename), "output%d.bin", idx);
            if (copy_mem_to_file(
                    reinterpret_cast<char *>(buffer_set[layer_idx]->va),
                    filename, (buffer_set[layer_idx]->size))) {
                std::cerr << ERROR_COLOR << "OUTPUT Error:" << RESET_COLOR
                          << "\tFailed to write output file" << std::endl;
                return FAILURE;
            }
        }
    }

    return SUCCESS;
}

template <typename T>
void golden_matching(void *control, void *test, int size, float threshold) {
    int diff = compare_data<T>(reinterpret_cast<T *>(test),
                               reinterpret_cast<T *>(control), size / sizeof(T),
                               threshold);

    float snr =
        calculate_snr<T>(reinterpret_cast<T *>(test),
                         reinterpret_cast<T *>(control), size / sizeof(T));

    if (diff == 0) {
        std::cout << SUCCESS_COLOR << "Golden Match" << RESET_COLOR
                  << std::endl;
    } else {
        std::cout << ERROR_COLOR << "Golden Mismatch" << RESET_COLOR
                  << std::endl;
        std::cout << "-\t"
                  << "different indices:" << diff << std::endl;
    }
    std::cout << "-\tsnr value:" << snr << std::endl;
}

void parse_arguments(int argc, char **argv, std::string &model_name,
                     std::vector<std::string> &inputs,
                     std::vector<std::string> &goldens, int &iteration,
                     bool &force, float &threshold) {
    CLI::App app("ENN SDK NNC Model Tester");

    app.add_option("--model", model_name, "Name of the model to execute")
        ->required();

    app.add_option("--input", inputs, "Input data files");

    app.add_option("--golden", goldens, "Golden (reference) files");

    app.add_option("--iteration", iteration,
                   "Number of iterations to run the model")
        ->check(CLI::NonNegativeNumber);

    app.add_option("--threshold", threshold,
                   "Threshold value for model execution")
        ->default_val("0.0");

    app.add_flag("--force", force, "Run the model without input data");

    try {
        app.parse(argc, argv);
    } catch (const CLI::ParseError &e) {
        exit(app.exit(e));
    }
}
