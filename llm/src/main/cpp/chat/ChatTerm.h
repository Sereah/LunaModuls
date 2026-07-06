#pragma once

#include <sstream>
#include <string>
#include <vector>
#include "chat.h"

namespace lunacattus::chat {
    struct ChatTerm {
        std::vector<common_chat_msg> chat_messages;
        llama_pos system_prompt_position = 0;
        llama_pos current_position = 0;

        llama_pos stop_generation_position = 0;
        std::string cached_token_chars;
        std::ostringstream assistant_ss;

        /// 上一轮格式化后的总 token 数，用于增量解码优化
        int previous_prompt_tokens = 0;
    };
}

