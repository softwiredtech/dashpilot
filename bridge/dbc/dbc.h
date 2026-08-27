#pragma once

#include <limits>
#include <utility>
#include <vector>
#include <string>
#include <sstream>

const std::string UNTITLED = "untitled";
const std::string DEFAULT_NODE_NAME = "XXX";
constexpr int CAN_MAX_DATA_BYTES = 64;

struct MessageId {
  uint8_t source = 0;
  uint32_t address = 0;

    std::string toString() const {
        std::ostringstream oss;
        oss << static_cast<unsigned>(source)   // prevent char promotion issues
            << ':'
            << std::hex << std::uppercase << address;
        return oss.str();
    }

    inline static MessageId fromString(const std::string &str) {
        size_t pos = str.find(':');
        if (pos == std::string::npos) return {};

        std::string sourceStr = str.substr(0, pos);
        std::string addressStr = str.substr(pos + 1);

        try {
            unsigned long sourceVal = std::stoul(sourceStr);
            unsigned long addressVal = std::stoul(addressStr, nullptr, 16); // hex conversion

            if (sourceVal > 255) return {}; // uint8_t overflow check

            return MessageId{ static_cast<uint8_t>(sourceVal), static_cast<unsigned int>(addressVal) };
        } catch (...) {
            return {}; // invalid number
        }
    }

  bool operator==(const MessageId &other) const {
    return source == other.source && address == other.address;
  }

  bool operator!=(const MessageId &other) const {
    return !(*this == other);
  }

  bool operator<(const MessageId &other) const {
    return std::tie(source, address) < std::tie(other.source, other.address);
  }

  bool operator>(const MessageId &other) const {
    return std::tie(source, address) > std::tie(other.source, other.address);
  }
};

uint qHash(const MessageId &item);

template <>
struct std::hash<MessageId> {
  std::size_t operator()(const MessageId &k) const noexcept { return qHash(k); }
};

typedef std::vector<std::pair<double, std::string>> ValueDescription;

namespace cabana {

class Signal {
public:
  Signal() = default;
  Signal(const Signal &other) = default;
  void update();
  bool getValue(const uint8_t *data, size_t data_size, double *val) const;
  // Unscaled signal bits, for signals wider than double's 53-bit mantissa.
  bool getRawBits(const uint8_t *data, size_t data_size, uint64_t *val) const;
  bool operator==(const cabana::Signal &other) const;
  inline bool operator!=(const cabana::Signal &other) const { return !(*this == other); }

  enum class Type {
    Normal = 0,
    Multiplexed,
    Multiplexor
  };

  Type type = Type::Normal;
  std::string name;
  int start_bit, msb, lsb, size;
  double factor = 1.0;
  double offset = 0;
  bool is_signed;
  bool is_little_endian;
  double min, max;
  std::string unit;
  std::string comment;
  std::string receiver_name;
  ValueDescription val_desc;

  // Multiplexed
  int multiplex_value = 0;
  Signal *multiplexor = nullptr;
};

class Msg {
public:
  Msg() = default;
  Msg(const Msg &other) { *this = other; }
  ~Msg();
  cabana::Signal *addSignal(const cabana::Signal &sig);
  cabana::Signal *updateSignal(const std::string &sig_name, const cabana::Signal &sig);
  void removeSignal(const std::string &sig_name);
  Msg &operator=(const Msg &other);
  int indexOf(const cabana::Signal *sig) const;
  cabana::Signal *sig(const std::string &sig_name) const;
  std::string newSignalName();
  void update();
  inline const std::vector<cabana::Signal *> &getSignals() const { return sigs; }

  uint32_t address;
  std::string name;
  uint32_t size;
  std::string comment;
  std::string transmitter;
  std::vector<cabana::Signal *> sigs;

  std::vector<uint8_t> mask;
  cabana::Signal *multiplexor = nullptr;
};

}  // namespace cabana

// Helper functions
double get_raw_value(const uint8_t *data, size_t data_size, const cabana::Signal &sig);
uint64_t get_raw_bits(const uint8_t *data, size_t data_size, const cabana::Signal &sig);
void updateMsbLsb(cabana::Signal &s);
inline int flipBitPos(int start_bit) { return 8 * (start_bit / 8) + 7 - start_bit % 8; }
