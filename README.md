# DashPilot

![Dashpilot header](./assets/dashpilot-header.png)

Turn your phone into a real-time dashboard for your car.  
DashPilot connects to your car, and provides a real-time dashboard right on your phone. It serves as a speedometer, shows the ACC speed you set, speed limit, blinkers, blind spots, and many more.
The good part is you can write your own dashboard as a [Rive](https://rive.app) or a web app.  
Check out the [dash-apps](./dash-apps) folder for examples.
Currently only Teslas are supported, but we're looking for adding support to other brands as well.

## Project structure

`/adasviz`:  

Contains the Bevy rendering engine used in `/dashpilot-android` that is capable of rendering vehicles, lanes, traffic lights and many more.  
We don't visualize everything the renderer is capable of, because the data we receive from the car is limited.  
When connecting through a Comma device, we are connected to the CAN 2 (Party bus), so we have the following information relevant for adasviz:
- stop line distance
- traffic light state
- blind spot left, right
- lane departure left, right
- ACC ON/OFF

When connecting through our kit, we connect to CAN 0 and 1 (chassis, and vehicle bus, respectively), so we als have information about the lane, so we can visualize that as well.

`/dash-apps`:  

These are the example apps running in the [dashpilot-android](./dashpilot-android/) sandbox app. They fall into two main categories: web apps, and [Rive](https://rive.app) apps. See the [dash-apps](./dash-apps/) readme for more.

`/dashpilot-android`:  

The android app that handles the connection to the vehicle, and running the `/dash-apps`.
It has two main parts: the `bridge` and the `app` module.  
The `bridge` module contains the code that establishes connection to the car, and handles CAN message, signal decoding.
The `app` module contains the code for the various data sources, rive / web dash views, navigation, UI, settings, etc. 

`/simulator`:  

A python based websocket server and simulator. It can generate dynamic scenarios for `adasviz`, and can also replay pre-recorded `routes`.

`python3 server.py --route '/path/to/a/route.jsonl'`

The server will print the ip, which you can enter in the DashPilot app start screen after selecting the WebSocket datasource.

NOTE:  
If you have [openpilot](https://github.com/commaai/openpilot) tools installed you can use the [replay](https://github.com/commaai/openpilot/tree/master/tools/replay) tool, or [cabana](https://github.com/commaai/openpilot/tree/master/tools/cabana) to stream a route, that `dashpilot-android` can receive.  

To do this, follow these steps inside your cloned openpilot folder:

- `cd tools/replay`
- `./replay 'segment-name' --data_dir="/path/to/the/folder/your/route/is/in" -a "can"`
- `cd cereal/messaging && ./bridge "can"`

This will start replaying the route, and starts the bridge, so that you can connect to it in `dashpilot-android` as if you were connecting to a real comma device. It uses the same `zmq` code path, and `dbc` handling as the real comma connection.

## How to run

1. 

You have two options:  

- Start a simulator (either our websocket server, or the openpilot replay tool)
- SSH into your comma, and start the bridge: `cd cereal/messaging` then `./bridge "can"`

2. run `./scripts/sync-vanilla-to-android` to sync `dash-apps/web-vanilla` into the android assets folder, so the our current main dash app works offline

3. Build and run the DashPilot android app in Android Studio  

4. Open the app and select a datasource: either Comma or WebSocket for now (remember: if you use openpilot replay tool, you have to select the comma data source, in the same way as if you were connecting to an actual comma device)

5. Enter the ip of the device your connecting to (the device in case of websocket is the websocket server's ip, and in case of openpilot replay tool, the ip of the device the replay tool is running on. For comma, you can find the ip of the device in the network settings)

6. Tap on "Connect" and select a dashboard

7. Enjoy


## Contributing

We're looking for:
- bug fixes
- performance improvements
- good tests
- ports to other brands

The guideline you should follow:
- Your first PR should be a small bugfix or improvement. Don't submit a big change if you haven't yet contributed.
- Keep the PRs as small and isolated as possible: never submit conceptually different changes in the same PR. If there are a lot of changes and they are conceptually the same, you should still break them up, and submit them separetely, then you can write it in the PR comment which PRs depend on that PR.
- Using AI tools is obviously fine *as long as you understand every line you're submitting*. If during the PR discussion it is clear that you're not understanding the change, the PR will be closed.
- The commit message should contain which part(s) of the stack that change is affecting, for exmaple starting with "[bridge]" if it is a change affecting the bridge module, or "[dashpilot-android]" if it is touching the DashPilot android app, etc.