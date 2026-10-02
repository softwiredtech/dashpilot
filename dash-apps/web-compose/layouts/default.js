// Shown until the user saves a layout of their own, and whenever a stored
// layout can't be read. Same document format as a saved one (see core/layout.js).
(function () {
  const DC = (window.DashCompose = window.DashCompose || {});

  DC.defaultLayout = {
    schemaVersion: 1,
    gap: 12,
    layouts: {
      landscape: {
        cols: 12,
        rows: 6,
        items: [
          { uid: "limit", type: "readout", x: 0, y: 0, w: 4, h: 2, bind: { value: "fusedSpeedLimit" } },
          { uid: "power", type: "readout", x: 0, y: 2, w: 4, h: 2, bind: { value: "packPower" } },
          { uid: "bsm-left", type: "lamp", x: 0, y: 4, w: 2, h: 2, bind: { value: "leftBlindSpot" },
            props: { icon: "warning", color: "#f59e0b", showLabel: true } },
          { uid: "odometer", type: "readout", x: 2, y: 4, w: 2, h: 2, bind: { value: "odometer" } },

          { uid: "speed", type: "radial", x: 4, y: 0, w: 4, h: 5, bind: { value: "egoSpeed", marker: "accSetSpeed" } },
          { uid: "blinker-left", type: "lamp", x: 4, y: 5, w: 1, h: 1, bind: { value: "leftBlinker" },
            props: { icon: "arrow-left", blink: true } },
          { uid: "blinker-right", type: "lamp", x: 7, y: 5, w: 1, h: 1, bind: { value: "rightBlinker" },
            props: { icon: "arrow-right", blink: true } },

          { uid: "adas", type: "lamp", x: 8, y: 0, w: 2, h: 2, bind: { value: "adasOn" },
            props: { icon: "wheel", color: "#3b82f6", showLabel: true } },
          { uid: "gear", type: "readout", x: 10, y: 0, w: 2, h: 2, bind: { value: "gear" }, props: { align: "center" } },
          { uid: "battery", type: "bar", x: 8, y: 2, w: 4, h: 2, bind: { value: "batteryPercent" } },
          { uid: "clock", type: "readout", x: 8, y: 4, w: 2, h: 1, bind: { value: "clock" } },
          { uid: "phone", type: "readout", x: 8, y: 5, w: 2, h: 1, bind: { value: "phoneBattery" } },
          { uid: "bsm-right", type: "lamp", x: 10, y: 4, w: 2, h: 2, bind: { value: "rightBlindSpot" },
            props: { icon: "warning", color: "#f59e0b", showLabel: true } },
        ],
      },
      portrait: {
        cols: 6,
        rows: 12,
        items: [
          { uid: "speed", type: "radial", x: 0, y: 0, w: 6, h: 5, bind: { value: "egoSpeed", marker: "accSetSpeed" },
            props: { showLabel: false } },
          { uid: "blinker-left", type: "lamp", x: 0, y: 5, w: 1, h: 1, bind: { value: "leftBlinker" },
            props: { icon: "arrow-left", blink: true, showLabel: false } },
          { uid: "adas", type: "lamp", x: 1, y: 5, w: 4, h: 1, bind: { value: "adasOn" },
            props: { icon: "wheel", color: "#3b82f6", showLabel: true } },
          { uid: "blinker-right", type: "lamp", x: 5, y: 5, w: 1, h: 1, bind: { value: "rightBlinker" },
            props: { icon: "arrow-right", blink: true, showLabel: false } },
          { uid: "limit", type: "readout", x: 0, y: 6, w: 3, h: 2, bind: { value: "fusedSpeedLimit" } },
          { uid: "gear", type: "readout", x: 3, y: 6, w: 3, h: 2, bind: { value: "gear" }, props: { align: "center" } },
          { uid: "battery", type: "bar", x: 0, y: 8, w: 6, h: 1, bind: { value: "batteryPercent" } },
          { uid: "power", type: "bar", x: 0, y: 9, w: 6, h: 1, bind: { value: "packPower" } },
          { uid: "pack-temp", type: "readout", x: 0, y: 10, w: 2, h: 2, bind: { value: "packTMax" } },
          { uid: "odometer", type: "readout", x: 2, y: 10, w: 2, h: 2, bind: { value: "odometer" } },
          { uid: "clock", type: "readout", x: 4, y: 10, w: 2, h: 1, bind: { value: "clock" } },
          { uid: "phone", type: "readout", x: 4, y: 11, w: 2, h: 1, bind: { value: "phoneBattery" } },
        ],
      },
    },
  };
})();
