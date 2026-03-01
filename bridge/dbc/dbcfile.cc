#include "dbcfile.h"
#include <regex>
#include <string>
#include <fstream>
#include <sstream>
#include <stdexcept>
#include <filesystem>
#include <vector>

// NOTE!
// This is a ported version of dbcfile used in Cabana.
// All the Qt dependencies have been removed and replace with std equivalents.
// Comments are not parse, only messages and signals.

// Helper to trim whitespace from a string
static inline std::string trim(const std::string &s) {
    size_t start = s.find_first_not_of(" \t\r\n");
    if (start == std::string::npos) return "";
    size_t end = s.find_last_not_of(" \t\r\n");
    return s.substr(start, end - start + 1);
}

bool endsWith(const std::string& str, const std::string& suffix) {
    if (suffix.size() > str.size()) return false;
    return str.compare(str.size() - suffix.size(), suffix.size(), suffix) == 0;
}

void replaceAll(std::string& str, const std::string& from, const std::string& to) {
    size_t start_pos = 0;
    while ((start_pos = str.find(from, start_pos)) != std::string::npos) {
        str.replace(start_pos, from.length(), to);
        start_pos += to.length(); // Move past the replacement
    }
}

std::vector<std::string> split(const std::string& s, char delimiter) {
    std::vector<std::string> tokens;
    std::stringstream ss(s);
    std::string item;
    while (std::getline(ss, item, delimiter)) {
        tokens.push_back(item);
    }
    return tokens;
}

DBCFile::DBCFile(const std::string &dbc_file_name) {
    std::ifstream file(dbc_file_name);
    if (!file.is_open()) {
        throw std::runtime_error("Failed to open file: " + dbc_file_name);
    }

    // Extract base name without extension
    name_ = std::filesystem::path(dbc_file_name).stem().string();
    filename = dbc_file_name;

    // Read entire file into a string
    std::stringstream buffer;
    buffer << file.rdbuf();
    parse(buffer.str());
}

DBCFile::DBCFile(const std::string &name, const std::string &content) : name_(name), filename("") {
  parse(content);
}

void DBCFile::updateMsg(const MessageId &id, const std::string &name, uint32_t size, const std::string &node, const std::string &comment) {
  auto &m = msgs[id.address];
  m.address = id.address;
  m.name = name;
  m.size = size;
  m.transmitter = node.empty() ? DEFAULT_NODE_NAME : node;
  m.comment = comment;
}

cabana::Msg *DBCFile::msg(uint32_t address) {
  auto it = msgs.find(address);
  return it != msgs.end() ? &it->second : nullptr;
}

cabana::Msg *DBCFile::msg(const std::string &name) {
  auto it = std::find_if(msgs.begin(), msgs.end(), [&name](auto &m) { return m.second.name == name; });
  return it != msgs.end() ? &(it->second) : nullptr;
}

cabana::Signal *DBCFile::signal(uint32_t address, const std::string &name) {
  auto m = msg(address);
  return m ? (cabana::Signal *)m->sig(name) : nullptr;
}

void DBCFile::parse(const std::string &content) {
    msgs.clear();

    int line_num = 0;
    std::string line;
    cabana::Msg *current_msg = nullptr;
    int multiplexor_cnt = 0;
    bool seen_first = false;

    std::istringstream stream(content);

    auto trim = [](std::string &s) {
        s.erase(s.begin(), std::find_if(s.begin(), s.end(),
                                        [](unsigned char ch) { return !std::isspace(ch); }));
        s.erase(std::find_if(s.rbegin(), s.rend(),
                             [](unsigned char ch) { return !std::isspace(ch); }).base(),
                s.end());
    };

    auto startsWith = [](const std::string &str, const std::string &prefix) {
        return str.size() >= prefix.size() && str.compare(0, prefix.size(), prefix) == 0;
    };

    std::string raw_line;
    while (std::getline(stream, raw_line)) {
        ++line_num;
        line = raw_line;
        trim(line);

        bool seen = true;
        try {
            if (startsWith(line, "BO_ ")) {
                multiplexor_cnt = 0;
                current_msg = parseBO(line);
            } else if (startsWith(line, "SG_ ")) {
                parseSG(line, current_msg, multiplexor_cnt);
            } else {
                seen = false;
            }
        } catch (const std::exception &e) {
            std::ostringstream oss;
            oss << "[" << filename << ":" << line_num << "] " << e.what() << ": " << line;
            throw std::runtime_error(oss.str());
        }

        if (seen) {
            seen_first = true;
        } else if (!seen_first) {
            header += raw_line + "\n";
        }
    }

    for (auto &[_, m] : msgs) {
        m.update();
    }
}

cabana::Msg* DBCFile::parseBO(const std::string &line) {
    static const std::regex bo_regexp(R"(^BO_ (\w+) (\w+)\s*:\s*(\w+) (\w+))");

    std::smatch match;
    if (!std::regex_match(line, match, bo_regexp)) {
        return nullptr;
    }

    // Convert address and size
    uint32_t address = std::stoul(match[1].str(), nullptr, 0);
    if (msgs.count(address) > 0) {
        throw std::runtime_error("Duplicate message address: " + std::to_string(address));
    }

    // Create a new message object
    cabana::Msg &msg = msgs[address];
    msg.address = address;
    msg.name = match[2].str();
    msg.size = std::stoul(match[3].str(), nullptr, 0);
    msg.transmitter = trim(match[4].str());

    return &msg;
}

void DBCFile::parseSG(const std::string &line, cabana::Msg *current_msg, int &multiplexor_cnt) {
    static const std::regex sg_regexp(R"(^SG_ (\w+) *: (\d+)\|(\d+)@(\d+)([\+|\-]) \(([0-9.+\-eE]+),([0-9.+\-eE]+)\) \[([0-9.+\-eE]+)\|([0-9.+\-eE]+)\] \"(.*)\" (.*))");
    static const std::regex sgm_regexp(R"(^SG_ (\w+) (\w+) *: (\d+)\|(\d+)@(\d+)([\+|\-]) \(([0-9.+\-eE]+),([0-9.+\-eE]+)\) \[([0-9.+\-eE]+)\|([0-9.+\-eE]+)\] \"(.*)\" (.*))");

  if (!current_msg)
    throw std::runtime_error("No Message");

  int offset = 0;
  std::smatch match;
  bool hasMatch = false;
  if (!std::regex_match(line, match, sg_regexp)) {
      hasMatch = std::regex_match(line, match, sgm_regexp);
      offset = 1;
  } else {
      hasMatch = true;
  }
  if (!hasMatch)
    throw std::runtime_error("Invalid SG_ line format");

  std::string name = match[1];
  if (current_msg->sig(name) != nullptr)
    throw std::runtime_error("Duplicate signal name");

  cabana::Signal s{};
  if (offset == 1) {
    std::string indicator = match[2];
    if (indicator == "M") {
      ++multiplexor_cnt;
      // Only one signal within a single message can be the multiplexer switch.
      if (multiplexor_cnt >= 2)
        throw std::runtime_error("Multiple multiplexor");

      s.type = cabana::Signal::Type::Multiplexor;
    } else {
      s.type = cabana::Signal::Type::Multiplexed;
      s.multiplex_value = std::stoi(indicator.substr(1));
    }
  }
  s.name = name;
  s.start_bit = std::stoi(match[offset + 2]);
  s.size = std::stoi(match[offset + 3]);
  s.is_little_endian = std::stoi(match[offset + 4]) == 1;
  s.is_signed = match[offset + 5] == "-";
  s.factor = std::stod(match[offset + 6]);
  s.offset = std::stod(match[offset + 7]);
  s.min = std::stod(match[8 + offset]);
  s.max = std::stod(match[9 + offset]);
  s.unit = match[10 + offset];
  s.receiver_name = trim(match[11 + offset]);
  current_msg->sigs.push_back(new cabana::Signal(s));
}
