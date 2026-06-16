#include "embedded_probe_payload_utils.h"

#include <cctype>
#include <string_view>

namespace autojs6::node_bridge {
namespace {

std::string sanitizePayloadValue(std::string value) {
    for (char& c : value) {
        if (c == '\r' || c == '\n') {
            c = ' ';
        }
    }
    return value;
}

}  // namespace

void putPayload(std::vector<std::string>& payload, const std::string& key, const std::string& value) {
    payload.push_back(key + "=" + sanitizePayloadValue(value));
}

void putPayload(std::vector<std::string>& payload, const std::string& key, const char* value) {
    putPayload(payload, key, std::string(value == nullptr ? "" : value));
}

void putPayload(std::vector<std::string>& payload, const std::string& key, bool value) {
    putPayload(payload, key, std::string(value ? "true" : "false"));
}

void putPayload(std::vector<std::string>& payload, const std::string& key, long long value) {
    putPayload(payload, key, std::to_string(value));
}

std::string fieldValueAfterPrefix(const std::string& field, const char* prefix) {
    const std::string_view fieldView(field);
    const std::string_view prefixView(prefix);
    if (fieldView.rfind(prefixView, 0) != 0) {
        return "";
    }
    return field.substr(prefixView.size());
}

std::string jsonStringField(const std::string& text, const char* key) {
    std::string needle = "\"";
    needle += key;
    needle += "\":\"";
    const size_t valueStart = text.find(needle);
    if (valueStart == std::string::npos) {
        return "";
    }
    std::string value;
    bool escaped = false;
    for (size_t i = valueStart + needle.size(); i < text.size(); ++i) {
        const char ch = text[i];
        if (escaped) {
            switch (ch) {
                case 'n':
                    value.push_back('\n');
                    break;
                case 'r':
                    value.push_back('\r');
                    break;
                case 't':
                    value.push_back('\t');
                    break;
                case 'b':
                    value.push_back('\b');
                    break;
                case 'f':
                    value.push_back('\f');
                    break;
                default:
                    value.push_back(ch);
                    break;
            }
            escaped = false;
            continue;
        }
        if (ch == '\\') {
            escaped = true;
            continue;
        }
        if (ch == '"') {
            return value;
        }
        value.push_back(ch);
    }
    return "";
}

bool jsonBooleanField(const std::string& text, const char* key) {
    std::string needle = "\"";
    needle += key;
    needle += "\":";
    const size_t valueStart = text.find(needle);
    if (valueStart == std::string::npos) {
        return false;
    }
    const size_t boolStart = valueStart + needle.size();
    return text.compare(boolStart, 4, "true") == 0;
}

std::string jsonNumberField(const std::string& text, const char* key) {
    std::string needle = "\"";
    needle += key;
    needle += "\":";
    const size_t valueStart = text.find(needle);
    if (valueStart == std::string::npos) {
        return "";
    }
    size_t numberStart = valueStart + needle.size();
    while (numberStart < text.size() && std::isspace(static_cast<unsigned char>(text[numberStart]))) {
        ++numberStart;
    }
    size_t numberEnd = numberStart;
    while (numberEnd < text.size()) {
        const char ch = text[numberEnd];
        if ((ch >= '0' && ch <= '9') || ch == '-' || ch == '+') {
            ++numberEnd;
            continue;
        }
        break;
    }
    return numberEnd > numberStart ? text.substr(numberStart, numberEnd - numberStart) : "";
}

std::string jsonArrayField(const std::string& text, const char* key) {
    std::string needle = "\"";
    needle += key;
    needle += "\":[";
    const size_t arrayStart = text.find(needle);
    if (arrayStart == std::string::npos) {
        return "";
    }
    const size_t valueStart = arrayStart + needle.size() - 1;
    bool inString = false;
    bool escaped = false;
    int depth = 0;
    for (size_t i = valueStart; i < text.size(); ++i) {
        const char ch = text[i];
        if (escaped) {
            escaped = false;
            continue;
        }
        if (ch == '\\' && inString) {
            escaped = true;
            continue;
        }
        if (ch == '"') {
            inString = !inString;
            continue;
        }
        if (inString) {
            continue;
        }
        if (ch == '[') {
            ++depth;
        } else if (ch == ']') {
            --depth;
            if (depth == 0) {
                return text.substr(valueStart, i - valueStart + 1);
            }
        }
    }
    return "";
}

}  // namespace autojs6::node_bridge
