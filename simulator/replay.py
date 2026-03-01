import os

class ReplayTool:
    def __init__(self, log_file: str):
        if not os.path.exists(log_file):
            raise FileNotFoundError(log_file)

        self.log_file = log_file
        self.cursor = 0

        with open(self.log_file, "r", encoding="utf-8") as f:
            self._lines = f.readlines()

    def get_next_message(self):
        if not self._lines:
            return None

        self.cursor %= len(self._lines)

        line = self._lines[self.cursor].strip()
        self.cursor += 1

        return line
