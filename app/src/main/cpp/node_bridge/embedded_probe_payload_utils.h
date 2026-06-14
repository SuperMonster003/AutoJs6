#pragma once

#include <string>
#include <vector>

namespace autojs6::node_bridge {

void putPayload(std::vector<std::string>& payload, const std::string& key, const std::string& value);
void putPayload(std::vector<std::string>& payload, const std::string& key, const char* value);
void putPayload(std::vector<std::string>& payload, const std::string& key, bool value);
void putPayload(std::vector<std::string>& payload, const std::string& key, long long value);

std::string fieldValueAfterPrefix(const std::string& field, const char* prefix);
std::string jsonStringField(const std::string& text, const char* key);
bool jsonBooleanField(const std::string& text, const char* key);
std::string jsonNumberField(const std::string& text, const char* key);
std::string jsonArrayField(const std::string& text, const char* key);

}  // namespace autojs6::node_bridge
