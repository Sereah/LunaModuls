#pragma once

namespace lunacattus::chat {
    inline bool IsValidUtf8(const char *string) {
        if (!string) { return true; }

        const auto *bytes = (const unsigned char *) string;
        int num;

        while (*bytes != 0x00) {
            if ((*bytes & 0x80) == 0x00) {
                // U+0000 to U+007F
                num = 1;
            } else if ((*bytes & 0xE0) == 0xC0) {
                // U+0080 to U+07FF
                num = 2;
            } else if ((*bytes & 0xF0) == 0xE0) {
                // U+0800 to U+FFFF
                num = 3;
            } else if ((*bytes & 0xF8) == 0xF0) {
                // U+10000 to U+10FFFF
                num = 4;
            } else {
                return false;
            }

            bytes += 1;
            for (int i = 1; i < num; ++i) {
                if ((*bytes & 0xC0) != 0x80) {
                    return false;
                }
                bytes += 1;
            }
        }
        return true;
    }
}
