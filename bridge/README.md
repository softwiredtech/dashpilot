# bridge

The bridge turns raw CAN frames into car state. Both apps compile it in and feed it the frames a DashKit sends over Bluetooth. It contains the `dbc` handling and the car platform logic (see `car`).

## dbc

`dbc` file handling logic from [Cabana](https://github.com/commaai/openpilot/tree/master/tools/cabana).

## car

This is where the vehicle porting code lives. It was inspired by [opendbc](https://github.com/commaai/opendbc/).
