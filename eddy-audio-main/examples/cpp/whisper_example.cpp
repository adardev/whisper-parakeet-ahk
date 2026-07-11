// Copyright (C) 2025 Eddy SDK
// SPDX-License-Identifier: Apache-2.0

#include "eddy/pipelines/whisper_pipeline.hpp"

#include <iostream>
#include <iomanip>
#include <exception>

int main(int argc, char* argv[]) {
    try {
        if (argc < 3) {
            std::cerr << "Usage: " << argv[0] << " <MODEL_DIR> <WAV_FILE> [DEVICE] [LANGUAGE] [--silent]" << std::endl;
            std::cerr << "Example: " << argv[0] << " ./models/whisper-v3-turbo ./audio.wav NPU en --silent" << std::endl;
            return 1;
        }

        std::string model_path = argv[1];
        std::string wav_file = argv[2];
        std::string device = "NPU";
        std::string language = "en";
        bool silent = false;

        for (int i = 3; i < argc; ++i) {
            std::string arg = argv[i];
            if (arg == "--silent") {
                silent = true;
            } else if (i == 3) {
                device = arg;
            } else if (i == 4) {
                language = arg;
            }
        }

        if (!silent) {
            std::cout << "=== Eddy Whisper Example ===" << std::endl;
            std::cout << "Model: " << model_path << std::endl;
            std::cout << "Audio: " << wav_file << std::endl;
            std::cout << "Device: " << device << std::endl;
            std::cout << "Language: " << language << std::endl;
            std::cout << std::endl;
        }

        // Configure Whisper pipeline
        eddy::WhisperConfig config;
        config.model_path = model_path;
        config.device = device;
        config.language = language;
        config.task = "transcribe";
        config.return_timestamps = true;
        config.enable_cache = true;
        config.cache_dir = "./cache";

        // Create pipeline
        if (!silent) std::cout << "Creating Whisper pipeline..." << std::endl;
        eddy::WhisperPipeline pipeline(config);
        if (!silent) std::cout << std::endl;

        // Transcribe
        if (!silent) std::cout << "Transcribing audio..." << std::endl;
        auto result = pipeline.transcribe(wav_file);
        if (!silent) std::cout << std::endl;

        // Print results
        if (!silent) {
            std::cout << "=== Transcription Result ===" << std::endl;
            std::cout << "Text: " << result.text << std::endl;
            std::cout << "Confidence: " << std::fixed << std::setprecision(2)
                      << (result.confidence * 100.0f) << "%" << std::endl;
            std::cout << "Inference Time: " << std::fixed << std::setprecision(2)
                      << result.inference_duration_ms << " ms" << std::endl;
            std::cout << std::endl;

            // Print timestamps if available
            if (!result.chunks.empty()) {
                std::cout << "=== Timestamps ===" << std::endl;
                for (const auto& chunk : result.chunks) {
                    std::cout << "[" << std::fixed << std::setprecision(2)
                              << chunk.start_ts << " -> ";
                    if (chunk.end_ts >= 0) {
                        std::cout << chunk.end_ts;
                    } else {
                        std::cout << "?";
                    }
                    std::cout << "] " << chunk.text << std::endl;
                }
            }
        } else {
            std::cout << result.text << std::endl;
        }

        return 0;

    } catch (const std::exception& e) {
        std::cerr << "Error: " << e.what() << std::endl;
        return 1;
    }
}