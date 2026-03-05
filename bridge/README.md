# bridge

The bridge serves as a gateway to your vehicle. It contains both communication, `dbc`, and car platform handling logic (see `car`).
The bridge uses `zmq` to communicate with the Comma device. For the Comma device connection, the bridge has to be running on your device.

## msgq

A slightly stripped-down version of [openpilot msgq](https://github.com/commaai/msgq/) for `zmq` support.

## dbc

`dbc` file handling logic from [Cabana](https://github.com/commaai/openpilot/tree/master/tools/cabana).

## capnp

openpilot `capnp` data structures to deserialize the streamed data from the Comma device.

## car

This is where the vehicle porting code lives. It was inspired by [opendbc](https://github.com/commaai/opendbc/).