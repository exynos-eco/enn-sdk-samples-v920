#include <string>

#include "include/enn_api-public_ndk_v1.hpp"

#define SUCCESS 0
#define FAILURE 1

#define STDOUT_64HEX \
    std::hex << std::uppercase << std::setw(16) << std::setfill('0')

typedef enum _BufferType {
    BufferType_FLOAT32 = 0,
    BufferType_FLOAT16 = 1,
    BufferType_INT32 = 2,
    BufferType_UINT8 = 3,
    BufferType_INT64 = 4,
    BufferType_STRING = 5,
    BufferType_BOOL = 6,
    BufferType_INT16 = 7,
    BufferType_COMPLEX64 = 8,
    BufferType_INT8 = 9,
    BufferType_FLOAT64 = 10,
    BufferType_COMPLEX128 = 11,
    BufferType_UINT64 = 12,
    BufferType_RESOURCE = 13,
    BufferType_VARIANT = 14,
    BufferType_UINT32 = 15,
    BufferType_MIN = BufferType_FLOAT32,
    BufferType_MAX = BufferType_UINT32
} BufferType;

const std::string ERROR_COLOR = "\033[1;31m";
const std::string SUCCESS_COLOR = "\033[1;32m";
const std::string RESET_COLOR = "\033[0m";

/**
 * @brief Runs a specified NNC model.
 *
 * @param model_name Identifier of the NNC model.
 * @param inputs Paths to input files.
 * @param goldens Paths to reference ("golden") files.
 * @param force_mode If true, skips output checking.
 * @param threshold Tolerance value for comparing model outputs with golden
 * references.
 * @param iteration Number of execution repetitions.
 * @return 0 for success, non-zero for failure.
 */
int execute_model(const std::string model_name,
                  const std::vector<std::string>& inputs,
                  const std::vector<std::string>& goldens,
                  const bool force_mode, const float threshold,
                  const int iteration);

/**
 * @brief Compares two data arrays and calculates the number of differences
 * based on a threshold.
 *
 * @tparam T Type of the data.
 * @param data1 Pointer to the first data array.
 * @param data2 Pointer to the second data array.
 * @param size Size of the data arrays.
 * @param threshold Threshold value beyond which differences are counted.
 * @return Returns the number of elements in the arrays that differ by more than
 * the threshold.
 */
template <typename T>
int compare_data(T* data1, T* data2, int size, T threshold = 0);

/**
 * @brief Calculates the Signal-to-Noise Ratio (SNR) between control and test
 * data arrays.
 *
 * @tparam T Type of the data.
 * @param control_data Pointer to the control data array.
 * @param test_data Pointer to the test data array.
 * @param size Size of the data arrays.
 * @return Returns the calculated SNR value.
 */
template <typename T>
float calculate_snr(T* control_data, T* test_data, int size);

/**
 * @brief Copies the content of a file into memory.
 *
 * @param filename Name/path of the file to read from.
 * @param dst Pointer to the destination memory buffer.
 * @return Returns the size of the read data in bytes or -1 in case of an error.
 */
int copy_file_to_mem(const char* filename, char* dst);

/**
 * @brief Copies a block of memory to a file.
 *
 * @param src Pointer to the source memory location.
 * @param filename Name/path of the destination file.
 * @param size Size of the memory block to write in bytes.
 * @return 0 on success, 1 on error.
 */
int copy_mem_to_file(const char* src, const char* filename, const int size);

/**
 * @brief Loads input data from files into the model's buffers.
 *
 * @param buffer_set Pointer to the model's buffers.
 * @param buffer_info Information about the number of buffers.
 * @param inputs Vector of input file paths.
 * @return 0 on success, 1 on error.
 */
int load_inputs(const EnnBufferPtr* buffer_set,
                const NumberOfBuffersInfo buffer_info,
                const std::vector<std::string>& inputs);

/**
 * @brief Processes the model's output buffers either by matching against golden
 * data or saving to files.
 *
 * @param model_id Model's unique identifier.
 * @param buffer_set Pointer to the model's buffers.
 * @param buffer_info Information about the number of buffers.
 * @param goldens Vector of golden data file paths.
 * @param threshold Threshold for matching data.
 * @return 0 on success, 1 on error.
 */
int process_outputs(const EnnModelId model_id, EnnBufferPtr* buffer_set,
                    NumberOfBuffersInfo buffer_info,
                    const std::vector<std::string>& goldens, float threshold);

/**
 * @brief Compares a buffer's data with golden data and prints matching results.
 *
 * @tparam T Data type (e.g., float, uint8_t).
 * @param control Pointer to golden data.
 * @param test Pointer to buffer data.
 * @param size Size of the data.
 * @param threshold Threshold for comparison.
 */
template <typename T>
void golden_matching(void* control, void* test, int size, float threshold);

/**
 * @brief Parses command line arguments.
 *
 * Parses the command line arguments and sets the values of the provided
 * variables accordingly.
 *
 * @param argc The count of command line arguments.
 * @param argv The array of command line arguments.
 * @param model_name The name of the model to execute.
 * @param inputs The input data files.
 * @param goldens The golden (reference) files.
 * @param iteration The number of iterations to run the model.
 * @param force Whether to run the model without input data.
 * @param threshold The threshold value for model execution.
 */
void parse_arguments(int argc, char** argv, std::string& model_name,
                     std::vector<std::string>& inputs,
                     std::vector<std::string>& goldens, int& iteration,
                     bool& force, float& threshold);